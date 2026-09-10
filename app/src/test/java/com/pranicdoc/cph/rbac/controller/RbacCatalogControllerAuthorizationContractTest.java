package com.pranicdoc.cph.rbac.controller;

import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.rbac.dto.RbacCatalogResourceResponse;
import com.pranicdoc.cph.rbac.dto.RbacCatalogResourceResponse.VerbDto;
import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;
import com.pranicdoc.cph.rbac.service.RbacCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization contract + web-layer coverage for {@link RbacCatalogController}
 * (38-CONTEXT D-03; threat register T-38-01 / T-38-02 / T-38-14).
 *
 * <p>Standalone MockMvc has no Spring Security filter chain, so authorization
 * annotations are never evaluated at request time in this file -- that is
 * exactly why the reflection contract tests in Section A/B exist, and why the
 * real allow/deny logic is proven separately in {@code RbacAuthzTest} (unit)
 * and, at request time through the real filter chain, in
 * {@code RbacMethodSecuritySliceContractTest} (plan 40-01). No test in this
 * file asserts a 403/401 for an under-permissioned caller -- such an assertion
 * would pass here for the wrong reason (there is no filter chain to deny
 * anything) and would create false confidence in a gate that was never
 * actually exercised (36-RESEARCH Pitfall 2 / 38-RESEARCH Pitfall 4).
 *
 * <p>Phase 40 D-02/D-03 harmonized this endpoint to the bare
 * {@code @rbacAuthz.hasPermission('ROLES','READ')} form: the coarse
 * {@code ADMIN}/{@code SUPADM} role prefix was removed, so Section A pins the
 * bare literal, Section B's sweep asserts exact bare-form equality rather than
 * a {@code startsWith} on the removed prefix, and Section D asserts the pinned
 * pair is allow-listed and that {@code ROLES} has no {@code ADMIN} verb.
 */
class RbacCatalogControllerAuthorizationContractTest {

    private static final String READ_LITERAL =
            "@rbacAuthz.hasPermission('ROLES','READ')";

    private MockMvc mockMvc;
    private RbacCatalogService rbacCatalogService;

    @BeforeEach
    void setUp() {
        rbacCatalogService = mock(RbacCatalogService.class);
        RbacCatalogController controller = new RbacCatalogController(rbacCatalogService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(new LocalValidatorFactoryBean())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ─── Section A — reflection contract test pinning the exact gate literal ──

    @Test
    void catalog_isAnnotatedWithBareReadPermissionGate() throws Exception {
        Method method = RbacCatalogController.class.getMethod("catalog");
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(READ_LITERAL);
    }

    // ─── Section B — the ungated-endpoint sweep (anti-vacuous: inspected == 1) ──

    private static final List<Class<? extends Annotation>> REQUEST_MAPPING_ANNOTATIONS = List.of(
            GetMapping.class, PostMapping.class, PutMapping.class, PatchMapping.class,
            DeleteMapping.class, RequestMapping.class);

    /**
     * Anchored bare-form matcher: the whole {@code @PreAuthorize} value must be
     * exactly {@code @rbacAuthz.hasPermission('<RESOURCE>','<VERB>')}. A
     * re-introduced {@code hasAnyRole(...) and ...} prefix, or any wider
     * expression, fails {@code matches(...)} — unlike the old {@code contains(...)}
     * check, which such a prefix would still pass.
     */
    private static final Pattern BARE_GATE =
            Pattern.compile("^@rbacAuthz\\.hasPermission\\('([A-Z_]+)','([A-Z]+)'\\)$");

    @Test
    void everyRequestMappedMethodCarriesABareDynamicGate() {
        int inspected = 0;

        for (Method method : RbacCatalogController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            boolean isRequestMapped = REQUEST_MAPPING_ANNOTATIONS.stream()
                    .anyMatch(annotationType -> method.getAnnotation(annotationType) != null);
            if (!isRequestMapped) {
                continue;
            }

            inspected++;

            PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
            assertThat(preAuthorize)
                    .as("method %s must carry @PreAuthorize", method.getName())
                    .isNotNull();

            String value = preAuthorize.value();
            assertThat(value)
                    .as("method %s's @PreAuthorize must be exactly a bare "
                            + "@rbacAuthz.hasPermission('ROLES','<VERB>') expression (D-02/D-03)",
                            method.getName())
                    .matches("^@rbacAuthz\\.hasPermission\\('ROLES','[A-Z]+'\\)$");
            assertThat(value)
                    .as("method %s's @PreAuthorize must not re-introduce a coarse role gate", method.getName())
                    .doesNotContain("hasRole")
                    .doesNotContain("hasAnyRole");
        }

        assertThat(inspected)
                .as("the sweep must inspect exactly the one declared catalog endpoint")
                .isEqualTo(1);
    }

    // ─── Section D — allow-list validity for the pinned (resource, verb) pair ──

    @Test
    void everyGateNamesAnAllowListedResourceVerbPair() {
        Matcher matcher = BARE_GATE.matcher(READ_LITERAL);
        assertThat(matcher.matches())
                .as("%s must be a bare @rbacAuthz.hasPermission('R','V') expression", READ_LITERAL)
                .isTrue();

        // An unknown literal must fail the test here, not be silently skipped.
        Resource resource = Resource.valueOf(matcher.group(1));
        Operation operation = Operation.valueOf(matcher.group(2));

        assertThat(resource.supports(operation))
                .as("%s:%s is not an allow-listed (resource, verb) pair (D-10) — RbacAuthz would "
                        + "resolve it to false for every non-SUPADM caller, permanently", resource, operation)
                .isTrue();

        assertThat(Resource.ROLES.supports(Operation.ADMIN))
                .as("ROLES has no ADMIN verb — the coarse ADMIN/SUPADM gate the D-02/D-03 harmonization "
                        + "removed is not re-expressible as a dynamic ROLES:ADMIN grant")
                .isFalse();
    }

    // ─── Section C — standalone MockMvc request/response coverage ──

    @Test
    void catalog_returns200AndDelegatesToServiceExactlyOnce() throws Exception {
        RbacCatalogResourceResponse stub = new RbacCatalogResourceResponse(
                "ROLES", "Roles",
                List.of(
                        new VerbDto("READ", "Read", "View records", false),
                        new VerbDto("UPDATE", "Update", "Edit existing records", true)));
        when(rbacCatalogService.catalog()).thenReturn(List.of(stub));

        mockMvc.perform(get("/api/admin/roles/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].resource").value("ROLES"))
                .andExpect(jsonPath("$[0].label").value("Roles"))
                .andExpect(jsonPath("$[0].verbs[0].value").value("READ"))
                .andExpect(jsonPath("$[0].verbs[0].label").value("Read"))
                .andExpect(jsonPath("$[0].verbs[1].value").value("UPDATE"));

        verify(rbacCatalogService).catalog();
    }
}
