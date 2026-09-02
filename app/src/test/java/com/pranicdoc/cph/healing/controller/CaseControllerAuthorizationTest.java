package com.pranicdoc.cph.healing.controller;

import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.dto.PageResponse;
import com.pranicdoc.cph.common.enums.JobStatus;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.security.OAuth2AuthenticationSuccessHandler;
import com.pranicdoc.cph.common.security.SecurityConfig;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.tenant.TenantInterceptor;
import com.pranicdoc.cph.healing.dto.CaseResponse;
import com.pranicdoc.cph.healing.dto.CaseSummaryResponse;
import com.pranicdoc.cph.healing.dto.HealerProtocolUnderstandingRequest;
import com.pranicdoc.cph.healing.dto.JobAckResponse;
import com.pranicdoc.cph.healing.dto.PdfJobDTO;
import com.pranicdoc.cph.healing.dto.PreSignedUrlResponse;
import com.pranicdoc.cph.healing.dto.SymptomFeedbackDTO;
import com.pranicdoc.cph.healing.service.impl.CaseService;
import com.pranicdoc.cph.healing.service.impl.HealerProtocolUnderstandingService;
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
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Request-level authorization suite for {@link CaseController} — the SC3 / SC4 half of plan
 * 42-09 (the reflection/contract half is {@code CaseControllerAuthorizationContractTest},
 * plan 42-03). Plan 42-03 proves the 14 {@code @PreAuthorize} annotations are present and
 * well-formed across {@code CASE}/{@code PDF}/{@code FEEDBACK}; it cannot prove the Spring
 * Security filter chain <em>evaluates</em> them. This class is that request-time proof: a
 * {@code standaloneSetup} MockMvc or a mis-assembled slice would report a green suite while
 * {@code @PreAuthorize} is inert.
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
 * <p><b>Ordering fact:</b> {@code @Valid} request-body binding, multipart part binding and
 * required-{@code @RequestParam} resolution all run <em>before</em> the method-security
 * interceptor, so a malformed request yields 400 without ever consulting the gate. Every
 * request built by {@link #gatedEndpoints()} is therefore schema-valid — 7 of the 14 gated
 * endpoints carry a request body and 3 carry required query params — and every collaborator
 * is stubbed non-null in {@link #stubHappyPath()} so the happy path is a clean 2xx, not a
 * 500 that would make a "not 403" assertion pass for the wrong reason.
 *
 * <p>This class asserts <b>behaviour only</b> (HTTP status + mock interaction). It pins no
 * {@code @PreAuthorize} literal — that belongs to {@code CaseControllerAuthorizationContractTest}
 * (42-03) — so a later annotation reformat cannot redden it.
 */
@WebMvcTest(CaseController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class,
        CaseControllerAuthorizationTest.RbacAuthzSliceConfig.class})
@ActiveProfiles("test")
class CaseControllerAuthorizationTest {

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

    // ─── per-controller collaborators ──────────────────────────────────────
    @MockBean
    private CaseService caseService;
    @MockBean
    private HealerProtocolUnderstandingService healerProtocolUnderstandingService;

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
    private static final UUID PATIENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String BASE = "/api/v1/cases";

    // Schema-valid request bodies — argument resolution runs before the gate, so a malformed
    // body would 400 before @PreAuthorize is ever consulted.
    private static final String SYMPTOM_JSON = "{\"symptomName\":\"Headache\"}";
    private static final String MEDICATION_JSON = "{\"medicationName\":\"Aspirin\"}";
    private static final String DECLARATION_JSON =
            "{\"patientConsent\":true,\"healerDeclaration\":true,\"patientSignature\":\"P\","
                    + "\"healerSignature\":\"H\",\"signedAt\":\"2026-01-01T00:00:00Z\"}";
    private static final String CLOSURE_JSON =
            "{\"additionalComments\":\"done\",\"isHealingContinued\":false}";
    private static final String PROTOCOL_UNDERSTANDING_JSON =
            "{\"chakraSelections\":[{\"chakra\":\"HEART\",\"energisingColors\":[],\"cleansingColors\":[]}]}";
    static final String CASE_CREATE_JSON =
            "{\"patientId\":\"" + PATIENT_ID + "\",\"declaration\":" + DECLARATION_JSON + "}";
    static final String CASE_UPDATE_JSON = "{}";

    private static final CaseResponse CASE_RESPONSE = new CaseResponse(
            ROW_ID, PATIENT_ID, HEALER_ID, null, null, List.of(),
            null, null, null, null, null, null, null,
            null, null, null, null, List.of(), List.of(), List.of(), null);
    private static final CaseSummaryResponse CASE_SUMMARY = new CaseSummaryResponse(
            "Test", "Patient", null, null, null, "1234567890", null,
            ROW_ID, null, List.of(), null, null, null, null, null, null, null,
            Instant.now(), null, List.of(), List.of(), List.of(), null);
    private static final PageResponse<CaseResponse> CASE_PAGE =
            new PageResponse<>(List.of(), 0, 10, 0L, 0, true);
    private static final PageResponse<SymptomFeedbackDTO> SYMPTOM_FEEDBACK_PAGE =
            new PageResponse<>(List.of(), 0, 10, 0L, 0, true);
    private static final HealerProtocolUnderstandingRequest EMPTY_HPU =
            new HealerProtocolUnderstandingRequest(List.of());
    private static final PreSignedUrlResponse PRESIGNED =
            new PreSignedUrlResponse("https://r2.example/lab-findings/x/y.pdf", Instant.now().plusSeconds(600));
    private static final JobAckResponse JOB_ACK = new JobAckResponse("job-1", JobStatus.PROCESSING);

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
        when(caseService.create(any(), any(), any())).thenReturn(CASE_RESPONSE);
        when(caseService.getById(any())).thenReturn(CASE_RESPONSE);
        when(caseService.getByPatientId(any(), any())).thenReturn(CASE_PAGE);
        when(caseService.updateCaseDetails(any(), any())).thenReturn(CASE_RESPONSE);
        when(caseService.getSymptomsForFeedback(any(), any())).thenReturn(SYMPTOM_FEEDBACK_PAGE);
        when(caseService.getCaseSummary(any())).thenReturn(CASE_SUMMARY);
        when(caseService.getCompletedPdfJobs(any(), anyInt()))
                .thenReturn(Window.<PdfJobDTO>from(List.of(), i -> ScrollPosition.keyset()));
        when(caseService.getPreSignedDownloadUrl(any())).thenReturn(PRESIGNED);
        when(caseService.postMessageToGeneratePdf(any())).thenReturn(JOB_ACK);
        when(healerProtocolUnderstandingService.getExistingSelection(any())).thenReturn(EMPTY_HPU);
    }

    /**
     * One gated {@link CaseController} method, its expected {@link Resource}/{@link Operation}
     * pair, and the concrete schema-valid request that drives it. {@code toString()} reads
     * {@code method (RESOURCE:VERB)} so the parameterized display name is legible.
     */
    record EndpointSpec(String methodName, Resource resource, Operation operation, RequestBuilder request) {
        @Override
        public String toString() {
            return methodName + " (" + resource + ":" + operation + ")";
        }
    }

    private static Arguments spec(String methodName, Resource resource, Operation operation,
                                 RequestBuilder request) {
        return Arguments.of(new EndpointSpec(methodName, resource, operation, request));
    }

    /**
     * Exactly the 14 gated CaseController methods, one schema-valid request each, per the
     * plan's {@code <interfaces>} table. Consumed by all three trio sweeps (SUPADM / granted /
     * ungranted) so coverage cannot drift between the three cases. Split: CASE 10, PDF 3,
     * FEEDBACK 1 — asserted by {@link #gatedEndpointsSourceSuppliesExactlyThe14GatedMethods()}.
     */
    static Stream<Arguments> gatedEndpoints() {
        return Stream.of(
                spec("getByPatientId", Resource.CASE, Operation.READ,
                        get(BASE + "/patient/" + PATIENT_ID).cookie(accessCookie())),
                spec("addSymptom", Resource.CASE, Operation.UPDATE,
                        post(BASE + "/" + ROW_ID + "/symptoms")
                                .contentType(MediaType.APPLICATION_JSON).content(SYMPTOM_JSON)
                                .cookie(accessCookie())),
                spec("resolveSymptom", Resource.CASE, Operation.UPDATE,
                        patch(BASE + "/" + ROW_ID + "/symptoms/" + ROW_ID + "/resolve")
                                .cookie(accessCookie())),
                spec("addMedication", Resource.CASE, Operation.UPDATE,
                        post(BASE + "/" + ROW_ID + "/medications")
                                .contentType(MediaType.APPLICATION_JSON).content(MEDICATION_JSON)
                                .cookie(accessCookie())),
                spec("signDeclaration", Resource.CASE, Operation.UPDATE,
                        post(BASE + "/" + ROW_ID + "/declaration")
                                .contentType(MediaType.APPLICATION_JSON).content(DECLARATION_JSON)
                                .cookie(accessCookie())),
                spec("closeCase", Resource.CASE, Operation.UPDATE,
                        post(BASE + "/" + ROW_ID + "/close")
                                .contentType(MediaType.APPLICATION_JSON).content(CLOSURE_JSON)
                                .cookie(accessCookie())),
                spec("saveProtocolUnderstanding", Resource.CASE, Operation.UPDATE,
                        post(BASE + "/" + ROW_ID + "/protocol-understanding")
                                .contentType(MediaType.APPLICATION_JSON).content(PROTOCOL_UNDERSTANDING_JSON)
                                .cookie(accessCookie())),
                spec("getProtocolUnderstanding", Resource.CASE, Operation.READ,
                        get(BASE + "/" + ROW_ID + "/protocol-understanding").cookie(accessCookie())),
                spec("delete", Resource.CASE, Operation.DELETE,
                        delete(BASE + "/" + ROW_ID).cookie(accessCookie())),
                spec("generatePdf", Resource.PDF, Operation.CREATE,
                        post(BASE + "/generate-pdf").param("healingCaseId", ROW_ID.toString())
                                .cookie(accessCookie())),
                spec("getAllPdfs", Resource.PDF, Operation.READ,
                        get(BASE + "/pdfs").param("limit", "10").cookie(accessCookie())),
                spec("getDownloadLink", Resource.PDF, Operation.EXPORT,
                        get(BASE + "/download").param("fileUrl", "lab-findings/x/y.pdf")
                                .cookie(accessCookie())),
                spec("getCaseSummary", Resource.CASE, Operation.READ,
                        get(BASE + "/" + ROW_ID + "/summary").cookie(accessCookie())),
                spec("resendFeedback", Resource.FEEDBACK, Operation.CREATE,
                        post(BASE + "/" + ROW_ID + "/resend-feedback").param("isFinal", "false")
                                .cookie(accessCookie())));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Anti-vacuous guard — the @MethodSource can never silently shrink,
    //  duplicate, or drift its CASE/PDF/FEEDBACK partition
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void gatedEndpointsSourceSuppliesExactlyThe14GatedMethods() {
        assertThat(gatedEndpoints().count())
                .as("the trio @MethodSource must supply exactly the 14 gated CaseController methods")
                .isEqualTo(14L);

        assertThat(gatedEndpoints()
                .map(a -> ((EndpointSpec) a.get()[0]).methodName())
                .toList())
                .as("a shrunken or duplicated @MethodSource must redden the build, not quietly change coverage")
                .containsExactlyInAnyOrder(
                        "getByPatientId", "addSymptom", "resolveSymptom", "addMedication", "signDeclaration",
                        "closeCase", "saveProtocolUnderstanding", "getProtocolUnderstanding", "delete",
                        "generatePdf", "getAllPdfs", "getDownloadLink", "getCaseSummary", "resendFeedback");

        Map<Resource, Long> byResource = gatedEndpoints()
                .map(a -> ((EndpointSpec) a.get()[0]).resource())
                .collect(Collectors.groupingBy(r -> r, Collectors.counting()));
        assertThat(byResource)
                .as("CaseController's 14 gates must partition CASE 10 / PDF 3 / FEEDBACK 1 (plan 42-03) — a "
                        + "copy-paste that duplicates a CASE endpoint instead of covering getDownloadLink fails here")
                .containsOnly(
                        entry(Resource.CASE, 10L),
                        entry(Resource.PDF, 3L),
                        entry(Resource.FEEDBACK, 1L));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  (a) SUPADM sweep — all 14 gated endpoints, zero grant lookups
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

    // ═══════════════════════════════════════════════════════════════════════
    //  (b)/(c) granted + ungranted sweeps — one code path, one differing boolean
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * The single shared code path for cases (b) and (c): authenticates the same HEALER,
     * stubs exactly the {@code (spec.resource(), spec.operation())} grant to {@code granted}
     * — never a three-way {@code any()} catch-all, so a gate naming a different {@code (resource,
     * verb)} pair (say {@code getDownloadLink} written {@code PDF:READ} instead of
     * {@code PDF:EXPORT}) falls through to Mockito's default {@code false} and fails the
     * granted sweep loudly — and performs the spec's request. Both parameterized tests below
     * call this and nothing else, so (b) and (c) are the same code path with a single
     * differing boolean: the anti-vacuous identity SC3 depends on.
     */
    private org.springframework.test.web.servlet.ResultActions performAsHealerWithGrant(
            EndpointSpec spec, boolean granted) throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), eq(spec.resource()), eq(spec.operation())))
                .thenReturn(granted);
        return mockMvc.perform(spec.request());
    }

    @ParameterizedTest(name = "granted HEALER reaches {0}")
    @MethodSource("gatedEndpoints")
    void grantedHealerReachesEveryGatedEndpoint(EndpointSpec spec) throws Exception {
        performAsHealerWithGrant(spec, true)
                .andExpect(status().is(not(403)));
    }

    @ParameterizedTest(name = "ungranted HEALER is 403 on {0} and the @rbacAuthz bean ran")
    @MethodSource("gatedEndpoints")
    void ungrantedHealerIsForbiddenOnEveryGatedEndpoint(EndpointSpec spec) throws Exception {
        performAsHealerWithGrant(spec, false)
                .andExpect(status().isForbidden());

        // Direct proof the @rbacAuthz bean method executed on the exact (resource, verb) the
        // annotation names — a slice that ignored @PreAuthorize would never reach this call.
        verify(lookupService).hasPermission(any(), eq(spec.resource()), eq(spec.operation()));
    }

    /**
     * {@code resendFeedback} is the ONLY request-time exercise of {@code Resource.FEEDBACK}
     * anywhere in the codebase: {@code FEEDBACK:CREATE} has exactly one enforcement site
     * (D-19 / plan 42-03), so if the granted/ungranted trio above is wrong for this row the
     * entire {@code FEEDBACK} resource is unverified. This test re-states the (c) assertion
     * for that single row under a name that makes the coverage dependency explicit.
     */
    @Test
    @DisplayName("resendFeedback is the sole request-time FEEDBACK:CREATE enforcement site — "
            + "ungranted HEALER is 403 and @rbacAuthz consulted FEEDBACK:CREATE")
    void resendFeedback_isTheSoleFeedbackEnforcementSite() throws Exception {
        EndpointSpec resendFeedback = gatedEndpoints()
                .map(a -> (EndpointSpec) a.get()[0])
                .filter(s -> s.methodName().equals("resendFeedback"))
                .findFirst()
                .orElseThrow();
        assertThat(resendFeedback.resource()).isEqualTo(Resource.FEEDBACK);
        assertThat(resendFeedback.operation()).isEqualTo(Operation.CREATE);

        performAsHealerWithGrant(resendFeedback, false)
                .andExpect(status().isForbidden());
        verify(lookupService).hasPermission(any(), eq(Resource.FEEDBACK), eq(Operation.CREATE));
    }
}
