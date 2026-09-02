package com.pranicdoc.cph.patient.controller;

import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.dto.PageResponse;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.security.OAuth2AuthenticationSuccessHandler;
import com.pranicdoc.cph.common.security.SecurityConfig;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.tenant.TenantInterceptor;
import com.pranicdoc.cph.patient.dto.MedicalHistoryResponse;
import com.pranicdoc.cph.patient.dto.PatientRegisterResponse;
import com.pranicdoc.cph.patient.dto.PatientResponse;
import com.pranicdoc.cph.patient.service.PatientService;
import com.pranicdoc.cph.rbac.RbacAuthz;
import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;
import com.pranicdoc.cph.rbac.service.RolePermissionLookupService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Request-level authorization suite for {@link PatientController} — the SC3 / SC4 half of
 * plan 42-08 (the reflection/contract half is {@code PatientControllerAuthorizationContractTest},
 * plan 42-02). Plan 42-02 proves the 9 {@code @PreAuthorize} annotations are present and
 * well-formed; it cannot prove the Spring Security filter chain <em>evaluates</em> them. This
 * class is that request-time proof: a {@code standaloneSetup} MockMvc or a mis-assembled
 * slice would report a green suite while {@code @PreAuthorize} is inert (40-RESEARCH Pitfall 2).
 *
 * <p>Built on the PROVEN {@code @WebMvcTest(controller) + @Import(SecurityConfig)} harness
 * from {@code 40-HARNESS-DECISION.md} (spike: {@code RbacMethodSecuritySliceContractTest},
 * {@code 9174e6e}). Three assembly facts are reproduced or the slice fails to load:
 * <ul>
 *   <li>{@code RbacAuthz} is contributed from a nested {@code @TestConfiguration}
 *       {@code @Bean} <em>named</em> {@code rbacAuthz} — NOT {@code @Import(RbacAuthz.class)},
 *       which registers it under its FQN and breaks the {@code @rbacAuthz} SpEL reference
 *       (HTTP 400).</li>
 *   <li>{@code HealerCacheService} and {@code OAuth2AuthenticationSuccessHandler} are
 *       {@code @MockBean}'d because the {@code SecurityConfig} slice context fails to load
 *       without them; {@code @ActiveProfiles("test")} supplies the dummy OAuth2 client
 *       registration.</li>
 *   <li>{@code @WithMockUser} does not survive {@code JwtAuthFilter}
 *       ({@code clearContext()} when no token cookie is present); callers are authenticated
 *       by stubbing {@link JwtService} to mint a valid {@code ACCESS} token and putting an
 *       {@code accessToken} cookie on the request.</li>
 * </ul>
 *
 * <p><b>Ordering fact:</b> {@code @Valid} request-body binding and required-{@code @RequestParam}
 * resolution run <em>before</em> the method-security interceptor, so a malformed request
 * yields 400 without ever consulting the gate. Every request built by {@link #gatedEndpoints()}
 * is therefore schema-valid, and {@code patientService} is stubbed non-null in
 * {@link #stubHappyPath()} so the happy path is a clean 2xx rather than a 500.
 *
 * <p>This class asserts <b>behaviour only</b> (HTTP status + mock interaction). It pins no
 * {@code @PreAuthorize} literal — that belongs to {@code PatientControllerAuthorizationContractTest}
 * (42-02) — so a later annotation reformat cannot redden it.
 */
@WebMvcTest(PatientController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class,
        PatientControllerAuthorizationTest.RbacAuthzSliceConfig.class})
@ActiveProfiles("test")
class PatientControllerAuthorizationTest {

    /**
     * Contributes the real {@link RbacAuthz} enforcement primitive under the bean name
     * {@code rbacAuthz} that {@code @PreAuthorize} SpEL references. {@code @Import(RbacAuthz.class)}
     * would name it by FQN and the lookup would fail — see the class javadoc.
     */
    @TestConfiguration
    static class RbacAuthzSliceConfig {
        @Bean
        RbacAuthz rbacAuthz(SecurityContextUtil securityContextUtil,
                            RolePermissionLookupService lookupService) {
            return new RbacAuthz(securityContextUtil, lookupService);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    // ─── per-controller collaborator ───────────────────────────────────────
    @MockBean
    private PatientService patientService;

    // ─── consumed by RbacAuthz ─────────────────────────────────────────────
    @MockBean
    private SecurityContextUtil securityContextUtil;
    @MockBean
    private RolePermissionLookupService lookupService;

    // ─── always required by the SecurityConfig slice context ───────────────
    @MockBean
    private JwtService jwtService;
    @MockBean
    private TenantInterceptor tenantInterceptor;
    @MockBean
    private HealerCacheService healerCacheService;
    @MockBean
    private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    private static final UUID HEALER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ROW_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String BASE = "/api/v1/patients";

    // Schema-valid request bodies — argument resolution runs before the gate, so a malformed
    // body would 400 before @PreAuthorize is ever consulted.
    private static final String PATIENT_JSON =
            "{\"firstName\":\"Test\",\"lastName\":\"Patient\",\"contact\":\"1234567890\"}";
    private static final String REGISTER_JSON =
            "{\"patient\":{\"firstName\":\"Test\",\"lastName\":\"Patient\",\"contact\":\"1234567890\"},"
                    + "\"declaration\":{\"patientConsent\":true,\"healerDeclaration\":true,"
                    + "\"patientSignature\":\"Test Patient\",\"healerSignature\":\"Test Healer\","
                    + "\"signedAt\":\"2026-01-01T00:00:00Z\"}}";
    private static final String UPDATE_JSON = "{\"firstName\":\"Updated\"}";
    private static final String MED_HISTORY_JSON = "{\"generalHealth\":\"Good\"}";

    private static final PatientResponse PATIENT_RESPONSE = new PatientResponse(
            ROW_ID, "Test", "Patient", null, null, null, null, "1234567890",
            null, null, null, null, null, null, Instant.now(), Instant.now());
    private static final MedicalHistoryResponse MED_HISTORY_RESPONSE = new MedicalHistoryResponse(
            ROW_ID, ROW_ID, "Good", null, null, null, null, null, null, null, null, null, null, null);
    private static final PatientRegisterResponse REGISTER_RESPONSE =
            new PatientRegisterResponse(ROW_ID, ROW_ID, "Test", "Patient");
    private static final PageResponse<PatientResponse> PAGE_RESPONSE =
            new PageResponse<>(List.of(), 0, 10, 0L, 0, true);

    private void authenticateAs(String role) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(HEALER_ID.toString());
        when(claims.get("role", String.class)).thenReturn(role);
        when(jwtService.isTokenValid(ACCESS_TOKEN)).thenReturn(true);
        when(jwtService.extractTokenType(ACCESS_TOKEN)).thenReturn("ACCESS");
        when(jwtService.parseToken(ACCESS_TOKEN)).thenReturn(claims);
        when(healerCacheService.isActive(any())).thenReturn(true);
        try {
            when(tenantInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Cookie accessCookie() {
        return new Cookie("accessToken", ACCESS_TOKEN);
    }

    @BeforeEach
    void stubHappyPath() {
        when(patientService.create(any())).thenReturn(PATIENT_RESPONSE);
        when(patientService.selfRegister(any())).thenReturn(PATIENT_RESPONSE);
        when(patientService.register(any())).thenReturn(REGISTER_RESPONSE);
        when(patientService.getAllPatients(any(), any(), any(), any())).thenReturn(List.of());
        when(patientService.getById(any())).thenReturn(PATIENT_RESPONSE);
        when(patientService.search(any(), any())).thenReturn(PAGE_RESPONSE);
        when(patientService.update(any(), any())).thenReturn(PATIENT_RESPONSE);
        when(patientService.updateMedicalHistory(any(), any())).thenReturn(MED_HISTORY_RESPONSE);
        when(patientService.getMedicalHistory(any())).thenReturn(MED_HISTORY_RESPONSE);
        when(patientService.generateLink())
                .thenReturn(Map.of("link", "http://localhost:3000/self-register/x/y"));
    }

    /**
     * One gated {@link PatientController} method, its expected {@link Operation}, and the
     * concrete schema-valid request that drives it. {@code toString()} is the handler name so
     * the parameterized display name reads cleanly.
     */
    private record EndpointSpec(String methodName, Operation operation, RequestBuilder request) {
        @Override
        public String toString() {
            return methodName + " (PATIENT:" + operation + ")";
        }
    }

    private static Arguments spec(String methodName, Operation operation, RequestBuilder request) {
        return Arguments.of(new EndpointSpec(methodName, operation, request));
    }

    /**
     * Exactly the 9 gated PatientController methods, one schema-valid request each, per the
     * plan's {@code <interfaces>} table. Consumed by all three trio sweeps so coverage cannot
     * drift between the SUPADM, granted, and ungranted cases.
     */
    static Stream<Arguments> gatedEndpoints() {
        return Stream.of(
                spec("create", Operation.CREATE,
                        post(BASE).contentType(MediaType.APPLICATION_JSON).content(PATIENT_JSON)
                                .cookie(accessCookie())),
                spec("register", Operation.CREATE,
                        post(BASE + "/register").contentType(MediaType.APPLICATION_JSON)
                                .content(REGISTER_JSON).cookie(accessCookie())),
                spec("getAllPatients", Operation.READ,
                        get(BASE).cookie(accessCookie())),
                spec("getById", Operation.READ,
                        get(BASE + "/" + ROW_ID).cookie(accessCookie())),
                spec("search", Operation.READ,
                        get(BASE + "/search").param("query", "smith").cookie(accessCookie())),
                spec("update", Operation.UPDATE,
                        put(BASE + "/" + ROW_ID).contentType(MediaType.APPLICATION_JSON)
                                .content(UPDATE_JSON).cookie(accessCookie())),
                spec("delete", Operation.DELETE,
                        delete(BASE + "/" + ROW_ID).cookie(accessCookie())),
                spec("updateMedicalHistory", Operation.UPDATE,
                        put(BASE + "/" + ROW_ID + "/medical-history")
                                .contentType(MediaType.APPLICATION_JSON).content(MED_HISTORY_JSON)
                                .cookie(accessCookie())),
                spec("getMedicalHistory", Operation.READ,
                        get(BASE + "/" + ROW_ID + "/medical-history").cookie(accessCookie())));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Anti-vacuous guard — the @MethodSource can never silently shrink
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void gatedEndpointsSourceSuppliesExactlyThe9GatedMethods() {
        assertThat(gatedEndpoints().count())
                .as("the trio @MethodSource must supply exactly the 9 gated PatientController methods")
                .isEqualTo(9L);
        assertThat(gatedEndpoints()
                .map(a -> ((EndpointSpec) a.get()[0]).methodName())
                .toList())
                .as("a shrunken @MethodSource must redden the build, not quietly reduce coverage")
                .containsExactlyInAnyOrder(
                        "create", "register", "getAllPatients", "getById", "search",
                        "update", "delete", "updateMedicalHistory", "getMedicalHistory");
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  (a) SUPADM sweep — all 9 gated endpoints, zero grant lookups
    // ═══════════════════════════════════════════════════════════════════════

    @ParameterizedTest(name = "SUPADM reaches {0} without a grant lookup")
    @MethodSource("gatedEndpoints")
    void supadmReachesEveryGatedEndpointWithoutAGrantLookup(EndpointSpec spec) throws Exception {
        authenticateAs("SUPADM");
        when(securityContextUtil.getCurrentRole()).thenReturn("SUPADM");

        mockMvc.perform(spec.request())
                .andExpect(status().is(not(403)));

        // RBAC-04: the SUPADM short-circuit in RbacAuthz returns true before any grant
        // lookup runs.
        verifyNoInteractions(lookupService);
    }
}
