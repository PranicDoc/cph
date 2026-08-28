package com.pranicdoc.cph.rbac.controller;

import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.rbac.dto.RoleAssignedUserResponse;
import com.pranicdoc.cph.rbac.dto.RoleAssignmentRequest;
import com.pranicdoc.cph.rbac.dto.RoleAssignmentResponse;
import com.pranicdoc.cph.rbac.dto.RoleUserAssignmentBody;
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
 * and only end-to-end in the phase's live filter-chain walkthrough. No test
 * in this file asserts a 403/401 for an under-permissioned caller — such an
 * assertion would pass here for the wrong reason (there is no filter chain to
 * deny anything) and would create false confidence in a gate that was never
 * actually exercised (36-RESEARCH Pitfall 2 / 38-RESEARCH Pitfall 4).
 */
class RoleUserAssignmentControllerAuthorizationContractTest {

    private static final String READ_LITERAL =
            "hasAnyRole('ADMIN','SUPADM') and @rbacAuthz.hasPermission('ROLES','READ')";
    private static final String UPDATE_LITERAL =
            "hasAnyRole('ADMIN','SUPADM') and @rbacAuthz.hasPermission('ROLES','UPDATE')";

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
    void list_isAnnotatedWithCombinedReadPermissionGate() throws Exception {
        Method method = RoleUserAssignmentController.class.getMethod("list", UUID.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(READ_LITERAL);
    }

    @Test
    void assign_isAnnotatedWithCombinedUpdatePermissionGate() throws Exception {
        Method method = RoleUserAssignmentController.class.getMethod(
                "assign", UUID.class, RoleUserAssignmentBody.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(UPDATE_LITERAL);
    }

    @Test
    void unassign_isAnnotatedWithCombinedUpdatePermissionGate() throws Exception {
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

    @Test
    void everyRequestMappedMethodCarriesTheCombinedPermissionGate() {
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
            assertThat(preAuthorize.value())
                    .as("method %s's @PreAuthorize must start with the coarse role gate", method.getName())
                    .startsWith("hasAnyRole('ADMIN','SUPADM') and ");
            assertThat(preAuthorize.value())
                    .as("method %s's @PreAuthorize must reference the ROLES resource dynamic gate", method.getName())
                    .contains("@rbacAuthz.hasPermission('ROLES',");
        }

        assertThat(inspected)
                .as("the sweep must inspect exactly the three declared assignment sub-resource endpoints")
                .isEqualTo(3);
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
