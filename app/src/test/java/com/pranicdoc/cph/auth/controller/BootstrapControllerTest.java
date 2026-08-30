package com.pranicdoc.cph.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pranicdoc.cph.auth.dto.BootstrapAssignRoleRequest;
import com.pranicdoc.cph.auth.dto.BootstrapRequest;
import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.enums.Role;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.common.security.CookieUtil;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.rbac.dto.RoleAssignmentRequest;
import com.pranicdoc.cph.rbac.dto.RoleAssignmentResponse;
import com.pranicdoc.cph.rbac.service.RoleAssignmentService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization contract + web-layer coverage for {@link BootstrapController} —
 * the first test this controller has ever had.
 *
 * <p>Standalone MockMvc runs with no Spring Security filter chain, so the
 * per-method authorization annotation is never evaluated at request time in
 * this file. That is exactly why Section A is a reflection contract over the
 * annotation literal rather than a request-time allow/deny test, and it is
 * why <b>no test in this file asserts a 403 for an under-permissioned
 * caller</b> — such an assertion would pass here for the wrong reason (there
 * is no filter chain to deny anything) and would manufacture confidence in a
 * gate that was never actually exercised.
 *
 * <p>Residual gap, stated plainly: nothing in this repository proves the real
 * Spring Security filter chain evaluates the super-admin tier gate on
 * {@code /api/v1/bootstrap/assign-role} end-to-end ({@code @SpringBootTest}
 * context load is broken repo-wide). That end-to-end proof is transferred to
 * the phase-closing plan's blocking live-verification gate.
 */
class BootstrapControllerTest {

    /**
     * The exact authorization literal the assign-role endpoint must carry,
     * written out in full. Not assembled from a prefix + a tier name — a typo
     * in a shared fragment would then slip through unnoticed.
     */
    private static final String ASSIGN_ROLE_LITERAL = "hasRole('SUPADM')";

    private HealerRepository healerRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private CookieUtil cookieUtil;
    private RoleAssignmentService roleAssignmentService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        healerRepository = mock(HealerRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        cookieUtil = mock(CookieUtil.class);
        roleAssignmentService = mock(RoleAssignmentService.class);

        BootstrapController controller = new BootstrapController(
                healerRepository, passwordEncoder, jwtService, cookieUtil, roleAssignmentService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(new LocalValidatorFactoryBean())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ─── Section A — reflection contract ──

    @Test
    void assignRole_carriesExactSuperAdminAuthorizationLiteral() throws Exception {
        Method method = BootstrapController.class.getMethod("assignRole", BootstrapAssignRoleRequest.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(ASSIGN_ROLE_LITERAL);
    }

    @Test
    void bootstrap_carriesNoAuthorizationAnnotation() throws Exception {
        // Regression guard: the original one-time bootstrap endpoint must stay
        // reachable without authentication, because it runs before any account
        // exists to authenticate as. Silently adding an authorization
        // annotation here would break first-run installation.
        Method method = BootstrapController.class.getMethod(
                "bootstrap", BootstrapRequest.class, HttpServletResponse.class);

        assertThat(method.getAnnotation(PreAuthorize.class)).isNull();
    }

    @SuppressWarnings("unchecked")
    private static final List<Class<? extends Annotation>> REQUEST_MAPPING_ANNOTATIONS = List.of(
            GetMapping.class, PostMapping.class, PutMapping.class, PatchMapping.class,
            DeleteMapping.class, RequestMapping.class);

    @Test
    void exactlyTwoRequestMappedEndpoints_exactlyOneCarriesAuthorization() {
        int inspected = 0;
        int authorized = 0;

        for (Method method : BootstrapController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            boolean isRequestMapped = REQUEST_MAPPING_ANNOTATIONS.stream()
                    .anyMatch(annotationType -> method.getAnnotation(annotationType) != null);
            if (!isRequestMapped) {
                continue;
            }

            inspected++;
            if (method.getAnnotation(PreAuthorize.class) != null) {
                authorized++;
            }
        }

        assertThat(inspected)
                .as("the sweep must inspect exactly the two declared bootstrap endpoints — "
                        + "a third shipping without a deliberate posture decision must break this")
                .isEqualTo(2);
        assertThat(authorized)
                .as("exactly one endpoint (assign-role) carries an authorization annotation; "
                        + "the original bootstrap endpoint is deliberately unauthenticated")
                .isEqualTo(1);
    }

    // ─── Section B — standalone MockMvc coverage ──

    @Test
    void assignRole_validRequest_returns201_andTakesAccountIdFromEmailLookup() throws Exception {
        UUID resolvedAccountId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        Instant assignedAt = Instant.parse("2026-08-27T00:00:00Z");

        Healer target = mock(Healer.class);
        when(target.getId()).thenReturn(resolvedAccountId);
        when(healerRepository.findByEmail("operator@example.com")).thenReturn(Optional.of(target));
        when(roleAssignmentService.assign(any(RoleAssignmentRequest.class)))
                .thenReturn(new RoleAssignmentResponse(assignmentId, resolvedAccountId, roleId, assignedAt));

        String body = """
                {"email":"operator@example.com","roleId":"%s"}
                """.formatted(roleId);

        mockMvc.perform(post("/api/v1/bootstrap/assign-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(assignmentId.toString()))
                .andExpect(jsonPath("$.principalId").value(resolvedAccountId.toString()))
                .andExpect(jsonPath("$.roleId").value(roleId.toString()));

        ArgumentCaptor<RoleAssignmentRequest> captor = ArgumentCaptor.forClass(RoleAssignmentRequest.class);
        verify(roleAssignmentService).assign(captor.capture());
        assertThat(captor.getValue().principalId())
                .as("the account id must be the one resolved from the email lookup, not anything from the body")
                .isEqualTo(resolvedAccountId);
        assertThat(captor.getValue().roleId()).isEqualTo(roleId);
    }

    @Test
    void assignRole_unknownEmail_returns404_andServiceNeverCalled() throws Exception {
        when(healerRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        String body = """
                {"email":"missing@example.com","roleId":"%s"}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/bootstrap/assign-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());

        verifyNoInteractions(roleAssignmentService);
    }

    @Test
    void assignRole_malformedEmail_returns400_andNeitherCollaboratorCalled() throws Exception {
        String body = """
                {"email":"not-an-email","roleId":"%s"}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/bootstrap/assign-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(healerRepository);
        verifyNoInteractions(roleAssignmentService);
    }

    @Test
    void assignRole_duplicateAssignment_surfacesAs400FromServiceUnchanged() throws Exception {
        UUID roleId = UUID.randomUUID();
        Healer target = mock(Healer.class);
        when(target.getId()).thenReturn(UUID.randomUUID());
        when(healerRepository.findByEmail("operator@example.com")).thenReturn(Optional.of(target));
        when(roleAssignmentService.assign(any(RoleAssignmentRequest.class)))
                .thenThrow(new IllegalArgumentException("This user is already assigned this role."));

        String body = """
                {"email":"operator@example.com","roleId":"%s"}
                """.formatted(roleId);

        mockMvc.perform(post("/api/v1/bootstrap/assign-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(roleAssignmentService).assign(any(RoleAssignmentRequest.class));
    }

    @Test
    void assignRole_missingRoleId_returns400() throws Exception {
        String body = """
                {"email":"operator@example.com"}
                """;

        mockMvc.perform(post("/api/v1/bootstrap/assign-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(roleAssignmentService);
    }

    @Test
    void bootstrap_emptySystem_creates201_withSupadmRole() throws Exception {
        // IN-04: the only other non-assignRole bootstrap test is the 409 self-lock path.
        // The BootstrapRequest DTO dropped its `role` field (D-02); this proves the first
        // healer is still seeded active with Role.SUPADM via the 4-field request.
        when(healerRepository.count()).thenReturn(0L);
        ArgumentCaptor<Healer> healerCaptor = ArgumentCaptor.forClass(Healer.class);
        when(healerRepository.save(healerCaptor.capture())).thenAnswer(invocation -> {
            Healer h = invocation.getArgument(0);
            h.setId(UUID.randomUUID());
            return h;
        });

        String body = """
                {"fullName":"First Admin","email":"admin@example.com",
                 "password":"secret123","phone":"555-0100"}
                """;

        mockMvc.perform(post("/api/v1/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("SUPADM"));

        Healer saved = healerCaptor.getValue();
        assertThat(saved.getRole()).isEqualTo(Role.SUPADM);
        assertThat(saved.isActive()).isTrue();
        verifyNoInteractions(roleAssignmentService);
    }

    @Test
    void bootstrap_whenAccountsAlreadyExist_returns409_andSavesNothing() throws Exception {
        when(healerRepository.count()).thenReturn(1L);

        String body = """
                {"fullName":"First Admin","email":"admin@example.com",
                 "password":"secret123"}
                """;

        mockMvc.perform(post("/api/v1/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());

        verify(healerRepository, never()).save(any());
        verifyNoInteractions(roleAssignmentService);
    }
}
