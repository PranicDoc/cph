package com.pranicdoc.cph.rbac.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.rbac.dto.ResourceGrantDto;
import com.pranicdoc.cph.rbac.dto.RoleCreateRequest;
import com.pranicdoc.cph.rbac.dto.RoleResponse;
import com.pranicdoc.cph.rbac.dto.RoleUpdateRequest;
import com.pranicdoc.cph.rbac.service.RbacRoleService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization contract + web-layer coverage for {@link RbacRoleController}
 * (RBAC-02, D-05, PD-36-17).
 *
 * <p>Standalone MockMvc has no Spring Security filter chain, so
 * authorization annotations are never evaluated at request time in this
 * file — that is exactly why the reflection contract tests in Section A/B
 * exist, and why the real allow/deny logic is proven separately in
 * {@code RbacAuthzTest} (unit) and only end-to-end in plan 36-05's live
 * {@code checkpoint:human-verify} gate (36-RESEARCH.md Pitfall 2). No test
 * in this file asserts a 403 for an under-permissioned caller — such an
 * assertion would pass here for the wrong reason (there is no filter chain
 * to deny anything) and would create false confidence in a gate that was
 * never actually exercised.
 */
class RbacRoleControllerAuthorizationContractTest {

    private static final String LIST_LITERAL =
            "hasAnyRole('ADMIN','SUPADM') and @rbacAuthz.hasPermission('ROLES','READ')";
    private static final String GET_BY_ID_LITERAL =
            "hasAnyRole('ADMIN','SUPADM') and @rbacAuthz.hasPermission('ROLES','READ')";
    private static final String CREATE_LITERAL =
            "hasAnyRole('ADMIN','SUPADM') and @rbacAuthz.hasPermission('ROLES','CREATE')";
    private static final String UPDATE_LITERAL =
            "hasAnyRole('ADMIN','SUPADM') and @rbacAuthz.hasPermission('ROLES','UPDATE')";
    private static final String DELETE_LITERAL =
            "hasAnyRole('ADMIN','SUPADM') and @rbacAuthz.hasPermission('ROLES','DELETE')";

    private MockMvc mockMvc;
    private RbacRoleService rbacRoleService;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        rbacRoleService = mock(RbacRoleService.class);
        RbacRoleController controller = new RbacRoleController(rbacRoleService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(new LocalValidatorFactoryBean())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ─── Section A — reflection contract tests, one per endpoint ──

    @Test
    void list_isAnnotatedWithCombinedReadPermissionGate() throws Exception {
        Method method = RbacRoleController.class.getMethod(
                "list", String.class, org.springframework.data.domain.Pageable.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(LIST_LITERAL);
    }

    @Test
    void getById_isAnnotatedWithCombinedReadPermissionGate() throws Exception {
        Method method = RbacRoleController.class.getMethod("getById", UUID.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(GET_BY_ID_LITERAL);
    }

    @Test
    void create_isAnnotatedWithCombinedCreatePermissionGate() throws Exception {
        Method method = RbacRoleController.class.getMethod("create", RoleCreateRequest.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(CREATE_LITERAL);
    }

    @Test
    void update_isAnnotatedWithCombinedUpdatePermissionGate() throws Exception {
        Method method = RbacRoleController.class.getMethod("update", UUID.class, RoleUpdateRequest.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(UPDATE_LITERAL);
    }

    @Test
    void delete_isAnnotatedWithCombinedDeletePermissionGate() throws Exception {
        Method method = RbacRoleController.class.getMethod("delete", UUID.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(DELETE_LITERAL);
    }

    // ─── Section B — the ungated-endpoint sweep (the real RBAC-02 build gate) ──

    @SuppressWarnings("unchecked")
    private static final List<Class<? extends Annotation>> REQUEST_MAPPING_ANNOTATIONS = List.of(
            GetMapping.class, PostMapping.class, PutMapping.class, PatchMapping.class,
            DeleteMapping.class, RequestMapping.class);

    @Test
    void everyRequestMappedMethodCarriesTheCombinedPermissionGate() {
        int inspected = 0;

        for (Method method : RbacRoleController.class.getDeclaredMethods()) {
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
                .as("the sweep must inspect exactly the five declared role-CRUD endpoints")
                .isEqualTo(5);
    }

    // ─── Section C — standalone MockMvc request/response coverage ──

    @Test
    void create_returns201AndDelegatesToService() throws Exception {
        RoleResponse response = sampleResponse();
        when(rbacRoleService.create(any(RoleCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/admin/roles")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(sampleCreateRequest())))
                .andExpect(status().isCreated());

        ArgumentCaptor<RoleCreateRequest> captor = ArgumentCaptor.forClass(RoleCreateRequest.class);
        verify(rbacRoleService).create(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Case Reviewer");
        assertThat(captor.getValue().grants()).hasSize(1);
        assertThat(captor.getValue().grants().get(0).resource()).isEqualTo("ROLES");
    }

    @Test
    void create_blankName_returns400() throws Exception {
        String body = """
                {"name":"","description":"desc","grants":[{"resource":"ROLES","operations":["READ"]}]}
                """;

        mockMvc.perform(post("/api/admin/roles")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_emptyGrants_returns400() throws Exception {
        String body = """
                {"name":"Case Reviewer","description":"desc","grants":[]}
                """;

        mockMvc.perform(post("/api/admin/roles")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_unknownResourceLiteral_returns400NotServerError() throws Exception {
        when(rbacRoleService.create(any(RoleCreateRequest.class)))
                .thenThrow(new IllegalArgumentException("'NOT_A_RESOURCE' is not a recognised resource."));

        String body = """
                {"name":"Case Reviewer","description":"desc","grants":[{"resource":"NOT_A_RESOURCE","operations":["READ"]}]}
                """;

        mockMvc.perform(post("/api/admin/roles")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(status().is(org.hamcrest.Matchers.not(500)));
    }

    @Test
    void update_passesRequestDtoFirstToService() throws Exception {
        UUID id = UUID.randomUUID();
        RoleResponse response = sampleResponse();
        when(rbacRoleService.update(any(RoleUpdateRequest.class), any(UUID.class))).thenReturn(response);

        mockMvc.perform(put("/api/admin/roles/{id}", id)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(sampleUpdateRequest())))
                .andExpect(status().isOk());

        ArgumentCaptor<RoleUpdateRequest> requestCaptor = ArgumentCaptor.forClass(RoleUpdateRequest.class);
        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(rbacRoleService).update(requestCaptor.capture(), idCaptor.capture());

        assertThat(requestCaptor.getValue().name()).isEqualTo("Case Reviewer");
        assertThat(idCaptor.getValue()).isEqualTo(id);
    }

    @Test
    void delete_returns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/admin/roles/{id}", id))
                .andExpect(status().isNoContent());

        verify(rbacRoleService).delete(id);
    }

    private static RoleCreateRequest sampleCreateRequest() {
        return new RoleCreateRequest(
                "Case Reviewer", "Reviews cases", List.of(new ResourceGrantDto("ROLES", List.of("READ"))));
    }

    private static RoleUpdateRequest sampleUpdateRequest() {
        return new RoleUpdateRequest(
                "Case Reviewer", "Reviews cases", List.of(new ResourceGrantDto("ROLES", List.of("READ"))));
    }

    private static RoleResponse sampleResponse() {
        return new RoleResponse(
                UUID.randomUUID(), "Case Reviewer", "Reviews cases",
                List.of(new ResourceGrantDto("ROLES", List.of("READ"))),
                Instant.now(), Instant.now());
    }
}
