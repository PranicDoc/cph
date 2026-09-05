package com.pranicdoc.cph.rbac.controller;

import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.rbac.baseline.BaselineRoleBackfillReport;
import com.pranicdoc.cph.rbac.baseline.BaselineRoleBackfillService;
import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization contract + web-layer coverage for
 * {@link BaselineRoleBackfillController} — the single
 * {@code POST /api/admin/roles/practitioner/backfill} deploy-ops endpoint.
 *
 * <p>Standalone MockMvc has no Spring Security filter chain, so the gate is
 * never evaluated at request time here — the reflection assertions below pin
 * the literal, exactly as the other {@code rbac.controller} contract tests do;
 * real allow/deny is proven in {@code RbacAuthzTest}. This class is also the
 * reason {@code LegacyEndpointCutoverSweepTest}'s {@code DELIBERATELY_UNSWEPT}
 * entry can call the endpoint "not an ungated hole".
 */
class BaselineRoleBackfillControllerAuthorizationContractTest {

    private static final String UPDATE_LITERAL = "@rbacAuthz.hasPermission('ROLES','UPDATE')";

    private static final List<Class<? extends Annotation>> REQUEST_MAPPING_ANNOTATIONS = List.of(
            GetMapping.class, PostMapping.class, PutMapping.class, PatchMapping.class,
            DeleteMapping.class, RequestMapping.class);

    private MockMvc mockMvc;
    private BaselineRoleBackfillService backfillService;

    @BeforeEach
    void setUp() {
        backfillService = mock(BaselineRoleBackfillService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new BaselineRoleBackfillController(backfillService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ─── reflection contract ──

    @Test
    void backfill_isAnnotatedWithBareRolesUpdateGate() throws Exception {
        Method method = BaselineRoleBackfillController.class.getMethod("backfill", boolean.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(UPDATE_LITERAL);
        assertThat(method.getAnnotation(PostMapping.class)).isNotNull();
    }

    @Test
    void everyRequestMappedMethodCarriesABareDynamicGate() {
        int inspected = 0;
        for (Method method : BaselineRoleBackfillController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            boolean isRequestMapped = REQUEST_MAPPING_ANNOTATIONS.stream()
                    .anyMatch(a -> method.getAnnotation(a) != null);
            if (!isRequestMapped) {
                continue;
            }
            inspected++;

            PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
            assertThat(preAuthorize).as("method %s must carry @PreAuthorize", method.getName()).isNotNull();
            assertThat(preAuthorize.value())
                    .as("method %s must carry a bare @rbacAuthz.hasPermission('ROLES','<VERB>') gate",
                            method.getName())
                    .matches("^@rbacAuthz\\.hasPermission\\('ROLES','[A-Z]+'\\)$")
                    .doesNotContain("hasRole")
                    .doesNotContain("hasAnyRole");
        }
        assertThat(inspected)
                .as("the controller must expose exactly one request-mapped method")
                .isEqualTo(1);
    }

    @Test
    void pinnedGatePairIsAllowListedByTheResourceCatalog() {
        assertThat(Resource.ROLES.supports(Operation.UPDATE)).isTrue();
    }

    // ─── web-layer behaviour ──

    @Test
    void post_defaultsDryRunToFalse_andReturnsTheReport() throws Exception {
        UUID roleId = UUID.randomUUID();
        when(backfillService.run(false)).thenReturn(
                new BaselineRoleBackfillReport(roleId, false, 0, 0, 0, 5, 5, 0, false));

        mockMvc.perform(post("/api/admin/roles/practitioner/backfill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleId").value(roleId.toString()))
                .andExpect(jsonPath("$.healersAssigned").value(5))
                .andExpect(jsonPath("$.dryRun").value(false));

        verify(backfillService).run(false);
    }

    @Test
    void post_passesDryRunTrueThrough() throws Exception {
        when(backfillService.run(true)).thenReturn(
                new BaselineRoleBackfillReport(null, true, 22, 0, 0, 5, 0, 0, true));

        mockMvc.perform(post("/api/admin/roles/practitioner/backfill").param("dryRun", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.healersAssigned").value(0));

        verify(backfillService).run(true);
    }
}
