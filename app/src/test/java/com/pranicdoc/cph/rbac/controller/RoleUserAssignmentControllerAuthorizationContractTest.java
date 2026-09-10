package com.pranicdoc.cph.rbac.controller;

import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.rbac.dto.RoleAssignedUserResponse;
import com.pranicdoc.cph.rbac.dto.RoleAssignmentRequest;
import com.pranicdoc.cph.rbac.dto.RoleAssignmentResponse;
import com.pranicdoc.cph.rbac.dto.RoleUserAssignmentBody;
import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;
import com.pranicdoc.cph.rbac.service.RoleAssignmentQueryService;
import com.pranicdoc.cph.rbac.service.RoleAssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization contract + web-layer coverage for
 * {@link RoleUserAssignmentController} (ASSIGN-01/02/03, 38-CONTEXT D-05;
 * threat register T-38-01 / T-38-02 / T-38-06 / T-38-14 / T-38-15).
 *
 * <p>Standalone MockMvc has no Spring Security filter chain, so authorization
 * annotations are never evaluated at request time in this file — that is
 * exactly why the reflection contract tests in Section A/B exist, and why the
 * real allow/deny logic is proven separately in {@code RbacAuthzTest} (unit)
 * and, at request time through the real filter chain, in
 * {@code RbacMethodSecuritySliceContractTest} (plan 40-01). No test in this
 * file asserts a 403/401 for an under-permissioned caller — such an assertion
 * would pass here for the wrong reason (there is no filter chain to deny
 * anything) and would create false confidence in a gate that was never
 * actually exercised (36-RESEARCH Pitfall 2 / 38-RESEARCH Pitfall 4).
 *
 * <p>Phase 40 D-02/D-03 harmonized this sub-resource to the bare
 * {@code @rbacAuthz.hasPermission('ROLES','<VERB>')} form: the coarse
 * {@code ADMIN}/{@code SUPADM} role prefix was removed, so Section A pins the
 * bare literals, Section B's sweep asserts exact bare-form equality rather
 * than a {@code startsWith} on the removed prefix, and Section D asserts every
 * pinned pair is allow-listed and that {@code ROLES} has no {@code ADMIN} verb.
 */
class RoleUserAssignmentControllerAuthorizationContractTest {

    private static final String READ_LITERAL =
            "@rbacAuthz.hasPermission('ROLES','READ')";
    private static final String UPDATE_LITERAL =
            "@rbacAuthz.hasPermission('ROLES','UPDATE')";

    private MockMvc mockMvc;
    private RoleAssignmentService roleAssignmentService;
    private RoleAssignmentQueryService roleAssignmentQueryService;

    @BeforeEach
    void setUp() {
        roleAssignmentService = mock(RoleAssignmentService.class);
        roleAssignmentQueryService = mock(RoleAssignmentQueryService.class);
        RoleUserAssignmentController controller =
                new RoleUserAssignmentController(roleAssignmentService, roleAssignmentQueryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(new LocalValidatorFactoryBean())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ─── Section A — reflection contract tests, one per handler ──

    @Test
    void list_isAnnotatedWithBareReadPermissionGate() throws Exception {
        Method method = RoleUserAssignmentController.class.getMethod("list", UUID.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(READ_LITERAL);
    }

    @Test
    void assign_isAnnotatedWithBareUpdatePermissionGate() throws Exception {
        Method method = RoleUserAssignmentController.class.getMethod(
                "assign", UUID.class, RoleUserAssignmentBody.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(UPDATE_LITERAL);
    }

    @Test
    void unassign_isAnnotatedWithBareUpdatePermissionGate() throws Exception {
        Method method = RoleUserAssignmentController.class.getMethod(
                "unassign", UUID.class, UUID.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(UPDATE_LITERAL);
    }

    // ─── Section B — the ungated-endpoint sweep (anti-vacuous: inspected == 3) ──

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

        for (Method method : RoleUserAssignmentController.class.getDeclaredMethods()) {
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
                .as("the sweep must inspect exactly the three declared assignment sub-resource endpoints")
                .isEqualTo(3);
    }

    // ─── Section D — allow-list validity for every pinned (resource, verb) pair ──

    @Test
    void everyGateNamesAnAllowListedResourceVerbPair() {
        List<String> expressions = List.of(READ_LITERAL, UPDATE_LITERAL, UPDATE_LITERAL);

        for (String expression : expressions) {
            Matcher matcher = BARE_GATE.matcher(expression);
            assertThat(matcher.matches())
                    .as("%s must be a bare @rbacAuthz.hasPermission('R','V') expression", expression)
                    .isTrue();

            // An unknown literal must fail the test here, not be silently skipped.
            Resource resource = Resource.valueOf(matcher.group(1));
            Operation operation = Operation.valueOf(matcher.group(2));

            assertThat(resource.supports(operation))
                    .as("%s:%s is not an allow-listed (resource, verb) pair (D-10) — RbacAuthz would "
                            + "resolve it to false for every non-SUPADM caller, permanently", resource, operation)
                    .isTrue();
        }

        assertThat(Resource.ROLES.supports(Operation.ADMIN))
                .as("ROLES has no ADMIN verb — the coarse ADMIN/SUPADM gate the D-02/D-03 harmonization "
                        + "removed is not re-expressible as a dynamic ROLES:ADMIN grant")
                .isFalse();
    }

    // ─── Section C — standalone MockMvc request/response coverage ──

    @Test
    void assign_returns201AndDelegatesWholeRequestRecordWithFieldsInOrder() throws Exception {
        UUID roleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(roleAssignmentService.assign(any(RoleAssignmentRequest.class)))
                .thenReturn(new RoleAssignmentResponse(UUID.randomUUID(), userId, roleId, Instant.now()));

        mockMvc.perform(post("/api/admin/roles/{id}/users", roleId)
                        .contentType("application/json")
                        .content("{\"userId\":\"" + userId + "\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<RoleAssignmentRequest> captor = ArgumentCaptor.forClass(RoleAssignmentRequest.class);
        verify(roleAssignmentService).assign(captor.capture());
        assertThat(captor.getValue().principalId()).isEqualTo(userId);
        assertThat(captor.getValue().roleId()).isEqualTo(roleId);
    }

    @Test
    void unassign_returns204AndDelegatesWholeRequestRecordWithFieldsInOrder() throws Exception {
        UUID roleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(delete("/api/admin/roles/{id}/users/{userId}", roleId, userId))
                .andExpect(status().isNoContent());

        ArgumentCaptor<RoleAssignmentRequest> captor = ArgumentCaptor.forClass(RoleAssignmentRequest.class);
        verify(roleAssignmentService).unassign(captor.capture());
        assertThat(captor.getValue().principalId()).isEqualTo(userId);
        assertThat(captor.getValue().roleId()).isEqualTo(roleId);
    }

    @Test
    void assign_missingUserId_returns400AndNeverCallsService() throws Exception {
        UUID roleId = UUID.randomUUID();

        mockMvc.perform(post("/api/admin/roles/{id}/users", roleId)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(roleAssignmentService);
    }

    @Test
    void assign_nullUserId_returns400AndNeverCallsService() throws Exception {
        UUID roleId = UUID.randomUUID();

        mockMvc.perform(post("/api/admin/roles/{id}/users", roleId)
                        .contentType("application/json")
                        .content("{\"userId\":null}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(roleAssignmentService);
    }

    @Test
    void list_returns200AndDelegatesToQueryService() throws Exception {
        UUID roleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant assignedAt = Instant.parse("2026-01-02T03:04:05Z");
        when(roleAssignmentQueryService.listAssignedUsers(roleId)).thenReturn(List.of(
                new RoleAssignedUserResponse(userId, "Alice Anderson", "alice@example.com", assignedAt)));

        mockMvc.perform(get("/api/admin/roles/{id}/users", roleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$[0].name").value("Alice Anderson"))
                .andExpect(jsonPath("$[0].email").value("alice@example.com"));

        verify(roleAssignmentQueryService).listAssignedUsers(roleId);
    }
}
