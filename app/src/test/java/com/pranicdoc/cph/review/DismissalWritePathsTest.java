package com.pranicdoc.cph.review;

import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.enums.CaseStatus;
import com.pranicdoc.cph.common.enums.FeedbackType;
import com.pranicdoc.cph.common.enums.ScanType;
import com.pranicdoc.cph.common.enums.SessionStatus;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.tenant.TenantContext;
import com.pranicdoc.cph.healing.dto.CaseClosureRequest;
import com.pranicdoc.cph.healing.dto.CaseUpdateRequest;
import com.pranicdoc.cph.healing.dto.CreateSessionRequest;
import com.pranicdoc.cph.healing.dto.DeclarationRequest;
import com.pranicdoc.cph.healing.dto.HealerProtocolUnderstandingRequest;
import com.pranicdoc.cph.healing.dto.HealingDetailsRequest;
import com.pranicdoc.cph.healing.dto.ManualFeedbackRequest;
import com.pranicdoc.cph.healing.dto.MedicationRequest;
import com.pranicdoc.cph.healing.dto.ProtocolRequest;
import com.pranicdoc.cph.healing.dto.QualitativeFeedbackRequest;
import com.pranicdoc.cph.healing.dto.ScanRequest;
import com.pranicdoc.cph.healing.dto.SymptomFeedbackRequest;
import com.pranicdoc.cph.healing.dto.SymptomRequest;
import com.pranicdoc.cph.healing.dto.UpdateSessionRequest;
import com.pranicdoc.cph.healing.model.CaseSymptom;
import com.pranicdoc.cph.healing.model.Declaration;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.model.HealingSession;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.healing.repository.SessionRepository;
import com.pranicdoc.cph.healing.service.NotificationService;
import com.pranicdoc.cph.healing.service.impl.CaseService;
import com.pranicdoc.cph.healing.service.impl.FeedbackService;
import com.pranicdoc.cph.healing.service.impl.HealerProtocolUnderstandingService;
import com.pranicdoc.cph.healing.service.impl.ProtocolService;
import com.pranicdoc.cph.healing.service.impl.ScanService;
import com.pranicdoc.cph.healing.service.impl.SessionService;
import com.pranicdoc.cph.patient.dto.DeclarationDateRequest;
import com.pranicdoc.cph.patient.dto.MedicalHistoryRequest;
import com.pranicdoc.cph.patient.dto.PatientCreateRequest;
import com.pranicdoc.cph.patient.model.Patient;
import com.pranicdoc.cph.patient.repository.PatientRepository;
import com.pranicdoc.cph.patient.service.PatientService;
import com.pranicdoc.cph.review.model.ReviewRevision;
import com.pranicdoc.cph.review.model.ReviewStage;
import com.pranicdoc.cph.review.model.ReviewStatus;
import com.pranicdoc.cph.review.model.ReviewSubmission;
import com.pranicdoc.cph.review.model.SubmittedVia;
import com.pranicdoc.cph.review.repository.ReviewRevisionRepository;
import com.pranicdoc.cph.review.repository.ReviewSubmissionRepository;
import com.pranicdoc.cph.support.TestRedisConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Every write that changes a case's data dismisses that case's APPROVED submission. Real service
 * beans on H2; each test invokes one write path on a case whose latest submission is APPROVED and
 * asserts it is now DISMISSED from approval with a reason.
 *
 * <p>Write paths (from {@code @Transactional} / class-level-transactional public methods in
 * healing/service/impl and the patient medical-history upsert), each with a test below:
 * <ul>
 *   <li>CaseService: addSymptom, resolveSymptom, addMedication, signDeclaration, closeCase,
 *       updateCaseDetails (healer), updateCaseDetails (patient self-service health form)</li>
 *   <li>SessionService: create(caseId) (legacy), create(request[, idempotencyKey]) — create(request)
 *       delegates to the keyed overload — update, delete, complete</li>
 *   <li>ScanService: saveScan</li>
 *   <li>ProtocolService: saveProtocol</li>
 *   <li>FeedbackService: saveSymptomFeedback and saveQualitativeFeedback (patient WhatsApp link),
 *       saveManualFeedback (healer)</li>
 *   <li>HealerProtocolUnderstandingService: saveFromHealerSelection</li>
 *   <li>PatientService: updateMedicalHistory (upserts the medical snapshot of every case of the
 *       patient, so each approved case is dismissed), updateDeclarationDate (latest case)</li>
 * </ul>
 *
 * <p>Deliberately not dismissing (no change to an existing reviewed case's content):
 * CaseService.create / PatientService.create, selfRegister, register (a new case has no
 * submission); CaseService.unlock / markUnlocked (only a LOCKED case changes, and a locked case has
 * no PDF so it was never reviewed); postMessageToGeneratePdf / buildAndEnqueuePdfJob (generating a
 * PDF does not change the case); resendFeedback (re-sends a link, case data unchanged);
 * refreshClosureFromSessions (an internal step of the session writes, which already dismiss);
 * CaseService.softDelete and PatientService.softDelete / restore / discardUnclaimedSelfRegistered
 * (trash is reversible and changes no case content, so a restored case keeps its approval);
 * getPreSignedDownloadUrl, requireConsent, findOrThrow and the other reads.
 */
@SpringBootTest
@Import(TestRedisConfig.class)
@ActiveProfiles("test")
@Transactional
class DismissalWritePathsTest {

    private static final UUID HEALER = UUID.randomUUID();

    @MockBean private SecurityContextUtil security;
    @MockBean private NotificationService notificationService;
    @MockBean private HealerCacheService healerCacheService;

    @Autowired private CaseService caseService;
    @Autowired private SessionService sessionService;
    @Autowired private ScanService scanService;
    @Autowired private ProtocolService protocolService;
    @Autowired private FeedbackService feedbackService;
    @Autowired private HealerProtocolUnderstandingService hpuService;
    @Autowired private PatientService patientService;

    @Autowired private PatientRepository patients;
    @Autowired private CaseRepository cases;
    @Autowired private SessionRepository sessions;
    @Autowired private ReviewSubmissionRepository submissions;
    @Autowired private ReviewRevisionRepository revisions;
    @Autowired private EntityManager em;

    private Patient patient;
    private HealingCase healingCase;
    private HealingSession session;
    private CaseSymptom symptom;
    private ReviewSubmission approved;

    @BeforeEach
    void seed() {
        TenantContext.set(HEALER);
        asHealer();
        when(healerCacheService.getName(any())).thenReturn("Healer One");
        when(healerCacheService.isActive(any())).thenReturn(true);

        patient = patients.save(Patient.create(new PatientCreateRequest("Asha", "Rao", null, null, null, null,
                "9876543210", null, null, null, null, null, null, null), HEALER));
        healingCase = newCase(Instant.parse("2026-10-01T00:00:00Z"));
        symptom = CaseSymptom.builder().healingCase(healingCase).symptomName("Headache")
                .addedDate(LocalDate.now().minusDays(5)).build();
        healingCase.getSymptoms().add(symptom);
        healingCase = cases.saveAndFlush(healingCase);
        symptom = healingCase.getSymptoms().get(0);

        session = sessions.saveAndFlush(HealingSession.builder().healingCase(healingCase).sessionNumber(1)
                .sessionDate(LocalDate.now().minusDays(1)).startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(11, 0))
                .status(SessionStatus.COMPLETED).build());
        healingCase.getSessions().add(session);

        approved = approvedSubmission(healingCase.getId());
        em.flush();
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private void asHealer() {
        when(security.getCurrentRole()).thenReturn("HEALER");
        when(security.getCurrentHealerId()).thenReturn(HEALER);
        when(security.getCurrentHealerIdOrNull()).thenReturn(HEALER);
        when(security.getCurrentHealerEmail()).thenReturn("healer@example.com");
    }

    private void asPatient(UUID caseId) {
        when(security.getCurrentRole()).thenReturn("PATIENT");
        when(security.getCurrentPatientId()).thenReturn(patient.getId());
        when(security.getCurrentCaseId()).thenReturn(caseId);
    }

    private HealingCase newCase(Instant declaredAt) {
        Declaration declaration = Declaration.builder().patientConsent(true).healerDeclaration(true)
                .patientSignature("Asha Rao").healerSignature("Healer One").signedAt(declaredAt).build();
        HealingCase c = HealingCase.create(patient.getId(), HEALER, declaration);
        c.setStatus(CaseStatus.CONTINUED);
        c.setMedicalCondition("Migraine");
        return c;
    }

    private ReviewSubmission approvedSubmission(UUID caseId) {
        ReviewSubmission s = submissions.saveAndFlush(ReviewSubmission.builder().caseId(caseId).healerId(HEALER)
                .reviewerId(UUID.randomUUID()).stage(ReviewStage.REVIEWER).status(ReviewStatus.APPROVED)
                .submittedVia(SubmittedVia.MANUAL).build());
        revisions.saveAndFlush(ReviewRevision.builder().submissionId(s.getId()).revisionNo(1)
                .fileUrl("review/" + s.getId() + "/r1.pdf").submittedVia(SubmittedVia.MANUAL)
                .submittedAt(Instant.now()).build());
        return s;
    }

    private void assertDismissed(ReviewSubmission submission) {
        em.flush();
        em.clear();
        ReviewSubmission reloaded = submissions.findById(submission.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ReviewStatus.DISMISSED);
        assertThat(reloaded.isDismissedFromApproved()).isTrue();
        assertThat(reloaded.getDismissReason()).isNotBlank();
    }

    private void assertDismissed() {
        assertDismissed(approved);
    }

    // ── CaseService ──────────────────────────────────────────────────────────

    @Test
    void addSymptom() {
        caseService.addSymptom(healingCase.getId(), new SymptomRequest("Fatigue", null, 3));
        assertDismissed();
    }

    @Test
    void resolveSymptom() {
        caseService.resolveSymptom(healingCase.getId(), symptom.getId());
        assertDismissed();
    }

    @Test
    void addMedication() {
        caseService.addMedication(healingCase.getId(),
                new MedicationRequest("Aspirin", "10mg", true, false, false, null));
        assertDismissed();
    }

    @Test
    void signDeclaration() {
        // One declaration per case: drop the seeded one so the new one can be saved.
        Declaration existing = healingCase.getDeclaration();
        healingCase.setDeclaration(null);
        em.remove(existing);
        em.flush();

        caseService.signDeclaration(healingCase.getId(),
                new DeclarationRequest(true, true, "Asha Rao", "Healer One", "2026-10-02"));
        assertDismissed();
    }

    @Test
    void closeCase() {
        caseService.closeCase(healingCase.getId(), new CaseClosureRequest("Feeling better", false));
        assertDismissed();
    }

    @Test
    void updateCaseDetails_byHealer() {
        caseService.updateCaseDetails(caseUpdate(), null);
        assertDismissed();
    }

    @Test
    void updateCaseDetails_byPatientSelfServiceHealthForm() {
        asPatient(healingCase.getId());
        caseService.updateCaseDetails(caseUpdate(), null);
        assertDismissed();
    }

    private CaseUpdateRequest caseUpdate() {
        return new CaseUpdateRequest(healingCase.getId(), "Migraine and stress",
                List.of(new SymptomRequest("Headache", null, null)),
                new HealingDetailsRequest("General sweeping", "Basic", "Sweep the head", List.of()),
                null, null, null, null, null, null, false, null, false, null, List.of(), null);
    }

    // ── SessionService ───────────────────────────────────────────────────────

    @Test
    void createSession_legacy() {
        sessionService.create(healingCase.getId());
        assertDismissed();
    }

    @Test
    void createSession() {
        sessionService.create(new CreateSessionRequest(healingCase.getId(), 2, LocalDate.now(),
                LocalTime.of(10, 0), LocalTime.of(11, 0), List.of(), List.of(), List.of()));
        assertDismissed();
    }

    @Test
    void createSession_withIdempotencyKey() {
        sessionService.create(new CreateSessionRequest(healingCase.getId(), 2, LocalDate.now(),
                LocalTime.of(10, 0), LocalTime.of(11, 0), List.of(), List.of(), List.of()), UUID.randomUUID());
        assertDismissed();
    }

    @Test
    void updateSession() {
        sessionService.update(session.getId(), new UpdateSessionRequest(List.of(), LocalDate.now().minusDays(2)));
        assertDismissed();
    }

    @Test
    void deleteSession() {
        sessionService.delete(session.getId());
        assertDismissed();
    }

    @Test
    void completeSession() {
        sessionService.complete(session.getId());
        assertDismissed();
    }

    // ── ScanService / ProtocolService ────────────────────────────────────────

    @Test
    void saveScan() {
        scanService.saveScan(session.getId(), new ScanRequest(ScanType.PRE, "calm", List.of()));
        assertDismissed();
    }

    @Test
    void saveProtocol() {
        protocolService.saveProtocol(session.getId(),
                List.of(new ProtocolRequest(null, "Head", "Sweeping", null, null, null, null, null)));
        assertDismissed();
    }

    // ── FeedbackService ──────────────────────────────────────────────────────

    @Test
    void saveSymptomFeedback_byPatientLink() {
        asPatient(healingCase.getId());
        feedbackService.saveSymptomFeedback(healingCase.getId(),
                List.of(new SymptomFeedbackRequest(symptom.getId(), 4, null)));
        assertDismissed();
    }

    @Test
    void saveQualitativeFeedback_byPatientLink() {
        asPatient(healingCase.getId());
        feedbackService.saveQualitativeFeedback(healingCase.getId(),
                new QualitativeFeedbackRequest(FeedbackType.INTERIM, "Much better", null));
        assertDismissed();
    }

    @Test
    void saveManualFeedback() {
        feedbackService.saveManualFeedback(healingCase.getId(),
                new ManualFeedbackRequest(FeedbackType.INTERIM, session.getId(), List.of(), "Better", null));
        assertDismissed();
    }

    // ── HealerProtocolUnderstandingService ───────────────────────────────────

    @Test
    void saveHealerProtocolUnderstanding() {
        hpuService.saveFromHealerSelection(healingCase.getId(), new HealerProtocolUnderstandingRequest(List.of(
                new HealerProtocolUnderstandingRequest.ChakraColorSelection("Crown", List.of(), List.of(),
                        "Headache"))));
        assertDismissed();
    }

    // ── PatientService ───────────────────────────────────────────────────────

    @Test
    void updateMedicalHistory_dismissesEveryCaseOfThePatient() {
        HealingCase second = cases.saveAndFlush(newCase(Instant.parse("2026-10-20T00:00:00Z")));
        ReviewSubmission secondApproved = approvedSubmission(second.getId());

        patientService.updateMedicalHistory(patient.getId(), new MedicalHistoryRequest("Good", "Vegetarian",
                null, null, false, false, false, false, false, null, null, null));

        assertDismissed(approved);
        assertDismissed(secondApproved);
    }

    @Test
    void updateDeclarationDate() {
        patientService.updateDeclarationDate(patient.getId(), new DeclarationDateRequest(LocalDate.now().minusDays(3)));
        assertDismissed();
    }

    // ── Not a write ──────────────────────────────────────────────────────────

    @Test
    void readingTheCase_keepsTheApproval() {
        caseService.getById(healingCase.getId());
        caseService.getCaseSummary(healingCase.getId());
        em.flush();
        em.clear();
        assertThat(submissions.findById(approved.getId()).orElseThrow().getStatus()).isEqualTo(ReviewStatus.APPROVED);
    }
}
