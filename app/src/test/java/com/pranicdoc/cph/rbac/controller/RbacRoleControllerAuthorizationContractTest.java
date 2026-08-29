package com.pranicdoc.cph.rbac.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.rbac.dto.ResourceGrantDto;
import com.pranicdoc.cph.rbac.dto.RoleCreateRequest;
import com.pranicdoc.cph.rbac.dto.RoleResponse;
import com.pranicdoc.cph.rbac.dto.RoleUpdateRequest;
import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * {@code RbacAuthzTest} (unit) and, at request time through the real filter
 * chain, in {@code RbacMethodSecuritySliceContractTest} (plan 40-01), which
 * drives this exact controller's {@code list} endpoint and asserts a 403 when
 * the grant lookup denies, a 200 when it grants, and a SUPADM short-circuit
 * (36-RESEARCH.md Pitfall 2). No test in this file asserts a 403 for an
 * under-permissioned caller — such an assertion would pass here for the wrong
 * reason (there is no filter chain to deny anything) and would create false
 * confidence in a gate that was never actually exercised.
 *
 * <p>Phase 40 D-02/D-03 harmonized this surface to the bare
 * {@code @rbacAuthz.hasPermission('ROLES','<VERB>')} form: the coarse
 * {@code ADMIN}/{@code SUPADM} role prefix was removed, so Section A pins the
 * bare literals and Section B's sweep asserts exact bare-form equality rather
 * than a {@code startsWith} on the removed prefix. Section D newly asserts
 * every pinned pair is allow-listed and that {@code ROLES} has no
 * {@code ADMIN} verb.
 */
class RbacRoleControllerAuthorizationContractTest {

    private static final String LIST_LITERAL =
            "@rbacAuthz.hasPermission('ROLES','READ')";
    private static final String GET_BY_ID_LITERAL =
            "@rbacAuthz.hasPermission('ROLES','READ')";
    private static final String CREATE_LITERAL =
            "@rbacAuthz.hasPermission('ROLES','CREATE')";
    private static final String UPDATE_LITERAL =
            "@rbacAuthz.hasPermission('ROLES','UPDATE')";
    private static final String DELETE_LITERAL =
            "@rbacAuthz.hasPermission('ROLES','DELETE')";

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
    void list_isAnnotatedWithBareReadPermissionGate() throws Exception {
        Method method = RbacRoleController.class.getMethod(
                "list", String.class, org.springframework.data.domain.Pageable.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(LIST_LITERAL);
    }

    @Test
    void getById_isAnnotatedWithBareReadPermissionGate() throws Exception {
        Method method = RbacRoleController.class.getMethod("getById", UUID.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(GET_BY_ID_LITERAL);
    }

    @Test
    void create_isAnnotatedWithBareCreatePermissionGate() throws Exception {
        Method method = RbacRoleController.class.getMethod("create", RoleCreateRequest.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(CREATE_LITERAL);
    }

    @Test
    void update_isAnnotatedWithBareUpdatePermissionGate() throws Exception {
        Method method = RbacRoleController.class.getMethod("update", UUID.class, RoleUpdateRequest.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(UPDATE_LITERAL);
    }

    @Test
    void delete_isAnnotatedWithBareDeletePermissionGate() throws Exception {
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

    /**
     * Anchored bare-form matcher: the whole {@code @PreAuthorize} value must be
     * exactly {@code @rbacAuthz.hasPermission('<RESOURCE>','<VERB>')} with
     * nothing before or after. A re-introduced {@code hasAnyRole(...) and ...}
     * prefix, or any wider expression, fails {@code matches(...)} — unlike the
     * old {@code contains(...)} check, which such a prefix would still pass.
     */
    private static final Pattern BARE_GATE =
            Pattern.compile("^@rbacAuthz\\.hasPermission\\('([A-Z_]+)','([A-Z]+)'\\)$");

    @Test
    void everyRequestMappedMethodCarriesABareDynamicGate() {
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
                .as("the sweep must inspect exactly the five declared role-CRUD endpoints")
                .isEqualTo(5);
    }

    // ─── Section D — allow-list validity for every pinned (resource, verb) pair ──

    @Test
    void everyGateNamesAnAllowListedResourceVerbPair() {
        List<String> expressions = List.of(
                LIST_LITERAL, GET_BY_ID_LITERAL, CREATE_LITERAL, UPDATE_LITERAL, DELETE_LITERAL);

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
                .as("ROLES has no ADMIN verb — no role-management endpoint may ever be mapped to one; "
                        + "the coarse ADMIN/SUPADM gate the D-02/D-03 harmonization removed is not "
                        + "re-expressible as a dynamic ROLES:ADMIN grant")
                .isFalse();
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
