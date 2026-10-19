package com.pranicdoc.cph.analytics;

import com.pranicdoc.cph.support.TestRedisConfig;
import org.springframework.context.annotation.Import;
import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.rbac.baseline.BaselineRoleAssigner;
import com.pranicdoc.cph.common.enums.CaseStatus;
import com.pranicdoc.cph.common.enums.FeedbackType;
import com.pranicdoc.cph.common.enums.Role;
import com.pranicdoc.cph.common.enums.ScanType;
import com.pranicdoc.cph.common.enums.SessionStatus;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.tenant.TenantContext;
import com.pranicdoc.cph.healing.model.ChakraScan;
import com.pranicdoc.cph.healing.model.Declaration;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.model.HealingDetails;
import com.pranicdoc.cph.healing.model.HealingSession;
import com.pranicdoc.cph.healing.model.QualitativeFeedback;
import com.pranicdoc.cph.healing.model.SymptomFeedback;
import com.pranicdoc.cph.healing.model.TreatmentStep;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.patient.dto.PatientCreateRequest;
import com.pranicdoc.cph.patient.model.Patient;
import com.pranicdoc.cph.patient.repository.PatientRepository;
import com.pranicdoc.cph.payments.CreditServiceClient;
import com.pranicdoc.cph.payments.dto.BalanceCreditResponse;
import com.pranicdoc.cph.payments.dto.OwnerType;
import com.pranicdoc.cph.payments.service.CreditGateService;
import com.pranicdoc.cph.payments.service.CreditService;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Permanent SQL-count regression guard (D-19, INFRA-01).
 *
 * Uses Hibernate's real Statistics API (hibernate.generate_statistics=true, see
 * application.yaml) against a full @SpringBootTest context + real H2-backed
 * EntityManagerFactory — NOT a Mockito unit test, which cannot observe Hibernate
 * session/statement activity (see DashboardServiceImplTest's class Javadoc for why).
 *
 * Auth: JwtAuthFilter reads the "accessToken" cookie; SecurityContextUtil requires
 * a real TenantDetails-carrying Authentication, so requests are authenticated with
 * a real JwtService-issued token (not @WithMockUser, which does not populate
 * TenantDetails and would NPE inside SecurityContextUtil).
 */
@SpringBootTest
@Import(TestRedisConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SqlCountAssertionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private PatientRepository patientRepository;

    // 26-07 Task 2: CaseService.postMessageToGeneratePdf now looks up the case's
    // owning healer to source the credit-boundary clinic-shaped ownerId from the
    // healer bridge — the fixture below must persist a real Healer row.
    @Autowired
    private HealerRepository healerRepository;

    @Autowired
    private BaselineRoleAssigner baselineRoleAssigner;

    @MockBean
    private CreditService creditService;

    @MockBean
    private CreditGateService creditGateService;

    // ClinicService depends on CreditServiceClient directly (bypassing the
    // CreditService/CreditGateService interfaces), and CreditServiceClient's
    // constructor requires ${credit-service.base-url} — unset in the test
    // profile (no WireMock server is started by this test). Mocking it here
    // avoids that unresolved-placeholder failure without needing WireMock.
    @MockBean
    private CreditServiceClient creditServiceClient;

    // Observed count for GET /api/v1/dashboard/summary against an empty (zero
    // patients/sessions/pdfs) tenant: 6 prepared statements (three count
    // queries — patientService/sessionService/pdfJobService — plus supporting
    // lookups; creditService is mocked, no SQL). Bound leaves modest headroom
    // for schema-neutral drift without letting a per-row N+1 (which scales
    // with data volume, not +1/+2) slip through.
    private static final long DASHBOARD_SUMMARY_BOUND = 10L;

    // Observed count for POST /api/v1/cases/generate-pdf on a single-session
    // case: 19 prepared statements (case + patient + per-collection loads +
    // pdf_job existence check + insert + audit). Deliberately generous in
    // Task 1 (harness-establishing baseline) — Task 2's multi-session growth
    // assertion is the one that actually proves the bound stays flat as
    // session count increases.
    private static final long GENERATE_PDF_SINGLE_SESSION_BOUND = 30L;

    // Task 2 / D-25: proves the N+1 fix — observed 36 (vs. 19 for the
    // single-session case above), NOT the ~114 a linear per-session N+1
    // would produce, even though this fixture has 6x the sessions and 2
    // TreatmentSteps. Before the fix (EAGER targets, no batch-fetch,
    // per-session HPU lookup, full lazy graph traversal) this scaled
    // linearly with session count and blew well past this bound.
    private static final long GENERATE_PDF_MULTI_SESSION_BOUND = 40L;

    @Test
    void dashboardSummary_boundedQueryCount() throws Exception {
        UUID healerId = UUID.randomUUID();
        when(creditService.getBalance(eq(OwnerType.HEALER), eq(healerId)))
                .thenReturn(new BalanceCreditResponse(healerId, "HEALER", 42, false, false));
        seedHealer(healerId);

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .cookie(accessTokenCookie(healerId)))
                .andExpect(status().isOk());

        long queryCount = stats.getPrepareStatementCount();
        System.out.println("[SqlCountAssertionTest] dashboard/summary prepared statements = " + queryCount);
        assertThat(queryCount)
                .as("GET /api/v1/dashboard/summary must issue a bounded SQL statement count")
                .isLessThanOrEqualTo(DASHBOARD_SUMMARY_BOUND);
    }

    @Test
    void generatePdf_singleSession_boundedQueryCount() throws Exception {
        UUID healerId = UUID.randomUUID();
        doNothing().when(creditGateService).checkAndDeduct(any(), any(), any());

        HealingCase healingCase = createCaseWithSessions(healerId, 1, false);

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        mockMvc.perform(post("/api/v1/cases/generate-pdf")
                        .param("healingCaseId", healingCase.getId().toString())
                        .cookie(accessTokenCookie(healerId)))
                .andExpect(status().isAccepted());

        long queryCount = stats.getPrepareStatementCount();
        System.out.println("[SqlCountAssertionTest] generate-pdf (1 session) prepared statements = " + queryCount);
        assertThat(queryCount)
                .as("POST /api/v1/cases/generate-pdf must issue a bounded SQL statement count")
                .isLessThanOrEqualTo(GENERATE_PDF_SINGLE_SESSION_BOUND);
    }

    @Test
    void generatePdf_multiSession_boundedQueryCount_provesNoNPlus1() throws Exception {
        UUID healerId = UUID.randomUUID();
        doNothing().when(creditGateService).checkAndDeduct(any(), any(), any());

        // 6 sessions (each with a scan/symptomFeedback/qualitativeFeedback) +
        // a HealingDetails with 2 TreatmentSteps — if the N+1 fix regresses,
        // this fixture's query count grows proportionally with session
        // count and blows past GENERATE_PDF_MULTI_SESSION_BOUND.
        HealingCase healingCase = createCaseWithSessions(healerId, 6, true);

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        mockMvc.perform(post("/api/v1/cases/generate-pdf")
                        .param("healingCaseId", healingCase.getId().toString())
                        .cookie(accessTokenCookie(healerId)))
                .andExpect(status().isAccepted());

        long queryCount = stats.getPrepareStatementCount();
        System.out.println("[SqlCountAssertionTest] generate-pdf (6 sessions + treatmentSteps) prepared statements = " + queryCount);
        assertThat(queryCount)
                .as("POST /api/v1/cases/generate-pdf must stay bounded as session count grows (no N+1)")
                .isLessThanOrEqualTo(GENERATE_PDF_MULTI_SESSION_BOUND);
    }

    @Test
    void generatePdf_creditGate_runsOutsideActiveTransaction() throws Exception {
        UUID healerId = UUID.randomUUID();

        // D-18 (T-23-04-CG): record whether a DB transaction is active at the
        // exact moment checkAndDeduct is invoked. Must be false — proves the
        // credit-gate HTTP call no longer holds a Hikari connection.
        AtomicBoolean txActiveDuringDeduct = new AtomicBoolean(true);
        doAnswer(invocation -> {
            txActiveDuringDeduct.set(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        }).when(creditGateService).checkAndDeduct(any(), any(), any());

        HealingCase healingCase = createCaseWithSessions(healerId, 1, false);

        mockMvc.perform(post("/api/v1/cases/generate-pdf")
                        .param("healingCaseId", healingCase.getId().toString())
                        .cookie(accessTokenCookie(healerId)))
                .andExpect(status().isAccepted());

        assertThat(txActiveDuringDeduct.get())
                .as("creditGateService.checkAndDeduct must run with NO active DB transaction (D-18)")
                .isFalse();
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    /**
     * Builds and persists a HealingCase with {@code sessionCount} sessions, each
     * with one ChakraScan (+1 reading-free scan), one SymptomFeedback, and one
     * QualitativeFeedback — the exact collection shape
     * CphCaseMapper.toCaseSnapshot traverses on the PDF-generation path. When
     * {@code withTreatmentSteps} is true, also attaches a HealingDetails row
     * with two TreatmentStep entries (each with a `targets` element
     * collection) to exercise the TreatmentStep EAGER-&gt;LAZY fix.
     *
     * Entities are built via plain setters (not the static create() factories,
     * which require TenantContext to be populated by a live HTTP request) and
     * flushed + detached so the MockMvc-driven request below triggers fresh
     * lazy loads instead of reusing this method's persistence context.
     *
     * 26-03: the Hibernate {@code tenantFilter} now scopes by {@code healer_id}
     * (populated from the request's authenticated {@code healerId}, see
     * {@code accessTokenCookie} below) — every seeded row's tenant column must
     * therefore be set to {@code healerId}, or the filter will scope these rows
     * out and the MockMvc requests below will 404/fail.
     */
    private HealingCase createCaseWithSessions(UUID healerId, int sessionCount,
                                                boolean withTreatmentSteps) {
        // 26-07 Task 2: postMessageToGeneratePdf now looks up this healerId via
        // HealerRepository to source the credit-boundary clinic-shaped ownerId
        // from the healer bridge — a real Healer row must exist or the request
        // 404s before reaching the PDF-generation logic under test. Healer no
        // longer carries a clinic-shaped bridge field (a7fcafe) — no value to seed.
        seedHealer(healerId);

        // Patient has no public constructor/builder — only the static create()
        // factory, which reads the tenant value from TenantContext (normally
        // populated by TenantInterceptor per-request) as a bootstrap value,
        // then Patient.create() itself overwrites it with the healerId arg
        // below — so the bootstrap value here is inconsequential.
        TenantContext.set(healerId);
        Patient patient;
        try {
            patient = Patient.create(new PatientCreateRequest(
                    "Test", "Patient", null, null, null, null, "9999999999",
                    null, null, null, null, null, null, null), healerId);
        } finally {
            TenantContext.clear();
        }
        patient = patientRepository.save(patient);

        HealingCase healingCase = HealingCase.builder()
                .patientId(patient.getId())
                .status(CaseStatus.ACTIVE)
                .build();
        // healerId is inherited from TenantEntity (post-26-03) — plain @Builder
        // does not expose superclass properties, so set it via the inherited
        // setter after build(), scoped to the tenant filter's healerId.
        healingCase.setHealerId(healerId);

        for (int i = 1; i <= sessionCount; i++) {
            HealingSession session = HealingSession.builder()
                    .sessionNumber(i)
                    .sessionDate(LocalDate.now())
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(9, 30))
                    .status(SessionStatus.COMPLETED)
                    .build();
            session.setHealerId(healerId);
            session.setHealingCase(healingCase);

            ChakraScan scan = ChakraScan.builder().scanType(ScanType.PRE).build();
            scan.setHealerId(healerId);
            scan.setHealingSession(session);
            session.getScans().add(scan);

            SymptomFeedback feedback = SymptomFeedback.builder()
                    .caseSymptomId(UUID.randomUUID())
                    .rating(5)
                    .build();
            feedback.setHealerId(healerId);
            feedback.setHealingSession(session);
            session.getSymptomFeedbacks().add(feedback);

            QualitativeFeedback qualitative = QualitativeFeedback.builder()
                    .feedbackType(FeedbackType.INTERIM)
                    .feedbackText("Doing well")
                    .build();
            qualitative.setHealerId(healerId);
            qualitative.setHealingSession(session);
            session.getQualitativeFeedbacks().add(qualitative);

            healingCase.getSessions().add(session);
        }

        // A real case reaches PDF generation only after consent, so it has a declaration;
        // the snapshot reads the healer signature from it.
        Declaration declaration = Declaration.builder()
                .healingCase(healingCase)
                .patientConsent(true)
                .healerDeclaration(true)
                .signedAt(Instant.now())
                .patientSignature("Test Patient")
                .healerSignature("Test Healer")
                .build();
        healingCase.setDeclaration(declaration);

        if (withTreatmentSteps) {
            HealingDetails details = new HealingDetails();
            details.setHealingCase(healingCase);
            details.setProtocolName("General Vitality");
            for (int i = 1; i <= 2; i++) {
                TreatmentStep step = new TreatmentStep();
                step.setHealingDetails(details);
                step.setTag("tag-" + i);
                step.setStep(i);
                step.setCycles(3);
                step.setTargets(List.of("Solar Plexus", "Basic"));
                details.getTreatmentSteps().add(step);
            }
            healingCase.setHealingDetails(details);
        }

        // caseRepository.save() runs (and commits) its own transaction —
        // SimpleJpaRepository.save is @Transactional — cascading the full
        // session/scan/feedback graph. No test-level transaction exists, so
        // the persistence context closes on return and the MockMvc-driven
        // request below performs genuinely fresh loads.
        return caseRepository.save(healingCase);
    }

    /**
     * A real, active healer with the baseline HEALER role, created the way the invite flow
     * does it. JwtAuthFilter rejects tokens for healers that don't exist or aren't active
     * (32ad9f8), and every endpoint is RBAC-gated, so a bare JWT is no longer enough.
     */
    private void seedHealer(UUID healerId) {
        Healer healer = Healer.builder()
                .fullName("Test Healer")
                .email("healer-" + healerId + "@test.local")
                .role(Role.HEALER)
                .build();
        healer.setId(healerId);
        healerRepository.save(healer);
        baselineRoleAssigner.assignTo(healerId);
    }

    private Cookie accessTokenCookie(UUID healerId) {
        // 26-09/29-01: JwtService.generateAccessToken is clinic-free (2-arg) and carries
        // no clinic-shaped claim at all — this helper needs only the healerId.
        String token = jwtService.generateAccessToken(healerId, "HEALER");
        return new Cookie("accessToken", token);
    }
}
