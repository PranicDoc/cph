package com.pranicdoc.cph.healing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.enums.CaseStatus;
import com.pranicdoc.cph.common.enums.ScanType;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.healing.dto.CaseClosureRequest;
import com.pranicdoc.cph.healing.dto.CaseResponse;
import com.pranicdoc.cph.healing.dto.CaseUpdateRequest;
import com.pranicdoc.cph.healing.dto.HealingDetailsRequest;
import com.pranicdoc.cph.healing.dto.MedicationRequest;
import com.pranicdoc.cph.healing.mapper.CphCaseMapper;
import org.springframework.test.util.ReflectionTestUtils;
import com.pranicdoc.cph.healing.repository.HealerProtocolUnderstandingRepository;
import com.pranicdoc.cph.healing.mapper.CphCaseMapperImpl;
import com.pranicdoc.cph.healing.mapper.DeclarationMapper;
import com.pranicdoc.cph.healing.mapper.HealingCaseMapper;
import com.pranicdoc.cph.healing.model.*;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.healing.repository.PdfJobRepository;
import com.pranicdoc.cph.healing.service.impl.CaseService;
import com.pranicdoc.cph.patient.model.Patient;
import com.pranicdoc.cph.patient.repository.PatientRepository;
import com.pranicdoc.cph.payments.service.CreditGateService;
import com.pranicdoc.storage.service.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pranicdoc.cph.payments.service.CreditGateService;
import com.pranicdoc.cph.common.exception.InsufficientCreditsException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CaseServiceTest {

    @Mock private CaseRepository caseRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private SecurityContextUtil securityContextUtil;
    @Mock private DeclarationMapper declarationMapper;
    @Mock private HealingCaseMapper healingCaseMapper;
    @Spy  private CphCaseMapper cphCaseMapper = new CphCaseMapperImpl();
    @Mock private HealerProtocolUnderstandingRepository hpuRepository;
    @Mock private MessageService messageService;
    @Mock private PdfJobRepository pdfJobRepository;
    @Mock private StorageService storageService;
    @Mock private NotificationService notificationService;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    @Mock private CreditGateService creditGateService;
    // D-18: CaseService self-injects its own Spring proxy via ObjectProvider
    // (see CaseService's `self` field javadoc) so postMessageToGeneratePdf's
    // delegated calls cross the @Transactional AOP proxy instead of silently
    // skipping transactionality via self-invocation. In this plain-Mockito
    // unit test there is no real proxy, so self.getObject() is stubbed to
    // return the same @InjectMocks instance (see initSelfProxy() below).
    @Mock private org.springframework.beans.factory.ObjectProvider<CaseService> self;
    // 26-07 Task 2: CaseService.postMessageToGeneratePdf now looks up the case's owning
    // healer to source the credit-boundary clinic-shaped ownerId from the healer bridge.
    @Mock private HealerRepository healerRepository;
    @Mock private com.pranicdoc.cph.auth.service.HealerCacheService healerService;

    @InjectMocks
    private CaseService caseService;

    @BeforeEach
    void initTransactionSync() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @BeforeEach
    void initSelfProxy() {
        lenient().when(self.getObject()).thenReturn(caseService);
    }

    /**
     * CphCaseMapper field-injects its healer-protocol-understanding repository (Spring does it
     * in the app); the spied mapper here needs one too. No case in this class has notes.
     */
    @BeforeEach
    void initMapperRepositories() {
        lenient().when(hpuRepository.findByHealingCaseId(any())).thenReturn(Optional.empty());
        ReflectionTestUtils.setField(cphCaseMapper, "hpuRepository", hpuRepository);
    }

    @AfterEach
    void clearTransactionSync() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void postMessageToGeneratePdf_serializesTypedCaseSnapshotPayload() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        // Build a ChakraReading with canonical fields
        ChakraReading reading = ChakraReading.builder()
                .chakraId(UUID.randomUUID())
                .chakraName("Crown")
                .energyLevel("5")
                .activationLevel("6")
                .notes("test")
                .build();

        ChakraScan scan = ChakraScan.builder()
                .scanType(ScanType.PRE)
                .readings(new ArrayList<>(List.of(reading)))
                .build();

        HealingSession session = HealingSession.builder()
                .sessionNumber(1)
                .sessionDate(LocalDate.now())
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .scans(new ArrayList<>(List.of(scan)))
                .build();

        // Build CaseClosure with non-null primitives to test null-safe unboxing
        com.pranicdoc.cph.healing.model.CaseClosure closure =
                com.pranicdoc.cph.healing.model.CaseClosure.builder()
                        .closureDate(LocalDate.now())
                        .additionalComments("Healed")
                        .healingCount(3)
                        .isHealingContinued(false)
                        .lastFeedbackDate(LocalDate.now())
                        .build();

        HealingCase healingCase = HealingCase.builder()
                .patientId(patientId)
                .status(CaseStatus.CLOSED)
                .sessions(new ArrayList<>(List.of(session)))
                .closure(closure)
                .symptoms(new ArrayList<>())
                .medications(new ArrayList<>())
                .build();
        // healerId is inherited from TenantEntity (post-26-03) — plain @Builder does not
        // include superclass fields, so set it via the inherited setter after build().
        UUID healerId = UUID.randomUUID();
        healingCase.setHealerId(healerId);
        session.setHealingCase(healingCase);
        healingCase.setDeclaration(declaration(healingCase));

        Patient patient = org.mockito.Mockito.mock(Patient.class);

        // Wire mocks
        doNothing().when(creditGateService).checkAndDeduct(any(), any(), any());
        when(caseRepository.existsById(caseId)).thenReturn(true);
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(healingCase));
        when(caseRepository.findWithSessionsById(caseId)).thenReturn(Optional.of(healingCase));
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(pdfJobRepository.findTopByHealingCaseIdOrderByCreatedAtDesc(caseId))
                .thenReturn(Optional.empty());
        // 26-07 D-26-03: jobId prefix is now healer-derived, not tenant-code-derived
        // (the old tenant-code lookup stub was removed — no longer invoked by this path).
        when(securityContextUtil.getCurrentHealerId()).thenReturn(UUID.randomUUID());

        ArgumentCaptor<PdfJobEntity> captor = ArgumentCaptor.forClass(PdfJobEntity.class);
        when(pdfJobRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        caseService.postMessageToGeneratePdf(caseId);

        // Parse captured payload
        PdfJobEntity saved = captor.getValue();
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        JsonNode payload = mapper.readTree(saved.getHealingCaseData());

        // PRIMARY DISCRIMINATING ASSERTIONS — prove BUG-03 rewrite happened
        // Old CaseResponse.closure → wire key "closure"
        // New CaseSnapshot.caseClosure → wire key "caseClosure"
        assertNotNull(payload.get("caseClosure"),
                "caseClosure key must be present — proves common-dtos CaseSnapshot was used, not CaseResponse");
        assertNull(payload.get("closure"),
                "closure key must be absent — old CaseResponse serialization emits this key");

        // Secondary: structural validation
        assertNotNull(payload.get("patient"), "patient sibling node must be present");
        assertNotNull(payload.get("healingSession"), "healingSession key must be present (not 'sessions')");
    }

    @Test
    void postMessageToGeneratePdf_approved_proceedsAndSavesJob() {
        UUID caseId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        HealingCase healingCase = HealingCase.builder()
                .patientId(patientId)
                .status(CaseStatus.DRAFT)
                .sessions(new ArrayList<>())
                .symptoms(new ArrayList<>())
                .medications(new ArrayList<>())
                .build();
        // healerId is inherited from TenantEntity (post-26-03) — set via inherited setter.
        UUID healerId = UUID.randomUUID();
        healingCase.setHealerId(healerId);
        healingCase.setDeclaration(declaration(healingCase));
        Patient patient = org.mockito.Mockito.mock(Patient.class);

        doNothing().when(creditGateService).checkAndDeduct(any(), any(), any());
        when(caseRepository.existsById(caseId)).thenReturn(true);
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(healingCase));
        when(caseRepository.findWithSessionsById(caseId)).thenReturn(Optional.of(healingCase));
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(pdfJobRepository.findTopByHealingCaseIdOrderByCreatedAtDesc(caseId)).thenReturn(Optional.empty());
        // 26-07 D-26-03: jobId prefix is now healer-derived, not tenant-code-derived
        // (the old tenant-code lookup stub was removed — no longer invoked by this path).
        when(securityContextUtil.getCurrentHealerId()).thenReturn(UUID.randomUUID());
        when(pdfJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        caseService.postMessageToGeneratePdf(caseId);

        verify(pdfJobRepository).save(any());
    }

    @Test
    void postMessageToGeneratePdf_insufficient_throwsAndNeverSavesJob() {
        UUID caseId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        HealingCase healingCase = HealingCase.builder()
                .patientId(patientId)
                .status(CaseStatus.DRAFT)
                .sessions(new ArrayList<>())
                .symptoms(new ArrayList<>())
                .medications(new ArrayList<>())
                .build();
        // healerId is inherited from TenantEntity (post-26-03) — set via inherited setter.
        UUID healerId = UUID.randomUUID();
        healingCase.setHealerId(healerId);

        doThrow(new InsufficientCreditsException()).when(creditGateService).checkAndDeduct(any(), any(), any());
        when(caseRepository.existsById(caseId)).thenReturn(true);
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(healingCase));

        assertThrows(InsufficientCreditsException.class,
                () -> caseService.postMessageToGeneratePdf(caseId));

        verify(pdfJobRepository, never()).save(any());
    }

    // --- updateCaseDetails: lab findings merge + medications clear (lab-findings-draft-edit fix round 1) ---

    /**
     * Builds a minimal HealingCase that will pass updateCaseDetails' authorization and
     * protocol-validation checks: healerId matches the mocked current healer, and a
     * non-null HealingDetails is already attached so the "existing details" (else) branch
     * is taken instead of the "new details" branch that would otherwise call the mocked
     * (and unstubbed) healingCaseMapper.toHealingDetails(...) and NPE on the null result.
     */
    private HealingCase buildCaseForUpdate(UUID caseId, UUID healerId, UUID patientId, CaseStatus status,
                                            String existingLabFindingsJson,
                                            List<CaseMedication> existingMedications) {
        HealingDetails existingDetails = new HealingDetails();
        HealingCase entity = HealingCase.builder()
                .patientId(patientId)
                .status(status)
                .healingDetails(existingDetails)
                .symptoms(new ArrayList<>())
                .medications(new ArrayList<>(existingMedications))
                .sessions(new ArrayList<>())
                .build();
        entity.setId(caseId);
        entity.setHealerId(healerId);
        entity.setLabFindings(existingLabFindingsJson);
        existingDetails.setHealingCase(entity);
        return entity;
    }

    private CaseUpdateRequest buildUpdateRequest(UUID caseId, List<MedicationRequest> medications, List<String> labFindings) {
        return buildUpdateRequest(caseId, "Test condition", medications, labFindings);
    }

    private CaseUpdateRequest buildUpdateRequest(UUID caseId, String medicalCondition,
                                                 List<MedicationRequest> medications, List<String> labFindings) {
        HealingDetailsRequest healingDetailsRequest =
                new HealingDetailsRequest("Test Protocol", "Test Book", "", null);
        return new CaseUpdateRequest(
                caseId,
                medicalCondition,
                null,
                healingDetailsRequest,
                "Test Protocol",
                "Test Book",
                "",
                "",
                "",
                "",
                false,
                "",
                false,
                "",
                medications,
                labFindings
        );
    }

    private void stubHealerUpdateAuthorization(UUID caseId, UUID healerId, HealingCase entity) {
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(entity));
        when(securityContextUtil.getCurrentRole()).thenReturn("Healer");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
    }

    @Test
    void updateCaseDetails_existingKeysPlusNewFile_mergesIntoPersistedLabFindings() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID healerId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        String existingKey = "lab-findings/" + caseId + "/existing-report.pdf";

        HealingCase entity = buildCaseForUpdate(caseId, healerId, patientId, CaseStatus.ACTIVE,
                "[\"" + existingKey + "\"]", List.of());
        stubHealerUpdateAuthorization(caseId, healerId, entity);

        CaseUpdateRequest request = buildUpdateRequest(caseId, null, List.of(existingKey));
        MockMultipartFile newFile = new MockMultipartFile(
                "labFindings", "new-scan.png", "image/png", "dummy-bytes".getBytes());

        CaseResponse response = caseService.updateCaseDetails(request, List.of(newFile));

        List<String> persisted = new ObjectMapper()
                .readValue(entity.getLabFindings(), new TypeReference<List<String>>() {});
        assertEquals(2, persisted.size(), "existing key + newly uploaded key must both be persisted");
        assertTrue(persisted.contains(existingKey), "pre-existing key must be retained, not replaced");
        assertTrue(persisted.stream().anyMatch(k -> k.startsWith("lab-findings/" + caseId + "/") && k.endsWith(".png")),
                "newly uploaded file must be appended with a generated key");

        // Response DTO (built from the same persisted column) must reflect the merge too.
        assertEquals(2, response.labFindings().size());
        verify(storageService, times(1)).upload(anyString(), any(byte[].class), anyString());
    }

    @Test
    void updateCaseDetails_emptyExistingKeysNoNewFile_clearsPersistedLabFindings() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID healerId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        String existingKey = "lab-findings/" + caseId + "/existing-report.pdf";

        HealingCase entity = buildCaseForUpdate(caseId, healerId, patientId, CaseStatus.ACTIVE,
                "[\"" + existingKey + "\"]", List.of());
        stubHealerUpdateAuthorization(caseId, healerId, entity);

        // Healer removed the only file and saved with none new — labFindings() sent as [].
        CaseUpdateRequest request = buildUpdateRequest(caseId, null, List.of());

        CaseResponse response = caseService.updateCaseDetails(request, null);

        List<String> persisted = new ObjectMapper()
                .readValue(entity.getLabFindings(), new TypeReference<List<String>>() {});
        assertTrue(persisted.isEmpty(), "explicit empty list must clear previously-persisted lab findings");
        assertTrue(response.labFindings().isEmpty());
        verify(storageService, never()).upload(anyString(), any(byte[].class), anyString());
    }

    @Test
    void updateCaseDetails_nullLabFindingsFieldPlusNewFile_persistsOnlyNewFileKey() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID healerId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        // No pre-existing lab findings on the case, mirroring PatientHealthForm's flow
        // where the JSON labFindings field is never sent (stays null).
        HealingCase entity = buildCaseForUpdate(caseId, healerId, patientId, CaseStatus.ACTIVE, null, List.of());
        stubHealerUpdateAuthorization(caseId, healerId, entity);

        CaseUpdateRequest request = buildUpdateRequest(caseId, null, null);
        MockMultipartFile newFile = new MockMultipartFile(
                "labFindings", "intake-scan.jpg", "image/jpeg", "dummy-bytes".getBytes());

        caseService.updateCaseDetails(request, List.of(newFile));

        List<String> persisted = new ObjectMapper()
                .readValue(entity.getLabFindings(), new TypeReference<List<String>>() {});
        assertEquals(1, persisted.size());
        assertTrue(persisted.get(0).startsWith("lab-findings/" + caseId + "/") && persisted.get(0).endsWith(".jpg"));
    }

    @Test
    void updateCaseDetails_medicationsSentAsEmptyList_clearsExistingMedications() {
        UUID caseId = UUID.randomUUID();
        UUID healerId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        CaseMedication existingMedication = CaseMedication.builder()
                .medicationName("Aspirin")
                .dosage("500mg")
                .build();
        HealingCase entity = buildCaseForUpdate(caseId, healerId, patientId, CaseStatus.ACTIVE,
                null, List.of(existingMedication));
        stubHealerUpdateAuthorization(caseId, healerId, entity);

        // Healer deleted every medication row and saved — medications() sent as [].
        CaseUpdateRequest request = buildUpdateRequest(caseId, List.of(), null);

        caseService.updateCaseDetails(request, null);

        assertTrue(entity.getMedications().isEmpty(),
                "an explicit empty medications list must clear existing medications, not leave them untouched");
    }

    @Test
    void updateCaseDetails_medicationsAbsentFromRequest_leavesExistingMedicationsUntouched() {
        UUID caseId = UUID.randomUUID();
        UUID healerId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        CaseMedication existingMedication = CaseMedication.builder()
                .medicationName("Aspirin")
                .dosage("500mg")
                .build();
        HealingCase entity = buildCaseForUpdate(caseId, healerId, patientId, CaseStatus.ACTIVE,
                null, List.of(existingMedication));
        stubHealerUpdateAuthorization(caseId, healerId, entity);

        // Simulates PatientHealthForm.tsx omitting the medications key entirely when the
        // patient added none (fix round 2) — Jackson deserializes this as null, which must
        // be treated as "don't touch", not as an implicit clear.
        CaseUpdateRequest request = buildUpdateRequest(caseId, null, null);

        caseService.updateCaseDetails(request, null);

        assertEquals(1, entity.getMedications().size(),
                "medications() absent (null) must leave existing medications untouched");
        assertEquals("Aspirin", entity.getMedications().get(0).getMedicationName());
    }

    @Test
    void updateCaseDetails_blankMedicalCondition_isRejectedBeforeAnyMutation() {
        UUID caseId = UUID.randomUUID();
        when(securityContextUtil.getCurrentRole()).thenReturn("Healer");

        // Chief complaint left blank (whitespace only) — the case would otherwise be
        // persisted empty and render as a blank View page.
        CaseUpdateRequest request = buildUpdateRequest(caseId, "   ", null, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> caseService.updateCaseDetails(request, null));
        assertTrue(ex.getMessage().toLowerCase().contains("chief complaint"),
                "blank chief complaint must be rejected with a clear message, was: " + ex.getMessage());
        verify(caseRepository, never()).findById(any());
        verify(healingCaseMapper, never()).toEntity(any(), any());
    }

    @Test
    void updateCaseDetails_patientWithoutSymptoms_isRejectedBeforeAnyMutation() {
        UUID caseId = UUID.randomUUID();
        when(securityContextUtil.getCurrentRole()).thenReturn("PATIENT");

        // Self-registered patient submits a chief complaint but no symptoms.
        CaseUpdateRequest request = buildUpdateRequest(caseId, "Migraine", null, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> caseService.updateCaseDetails(request, null));
        assertTrue(ex.getMessage().toLowerCase().contains("symptom"),
                "missing symptoms must be rejected with a clear message, was: " + ex.getMessage());
        verify(caseRepository, never()).findById(any());
        verify(healingCaseMapper, never()).toEntity(any(), any());
    }

    // --- closeCase (V31: CaseStatus.CONTINUED) ---

    private HealingCase buildCaseForClosure(UUID caseId) {
        HealingSession session = HealingSession.builder()
                .sessionNumber(1)
                .sessionDate(LocalDate.now())
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .build();
        HealingCase healingCase = HealingCase.builder()
                .patientId(UUID.randomUUID())
                .status(CaseStatus.ACTIVE)
                .sessions(new ArrayList<>(List.of(session)))
                .build();
        healingCase.setId(caseId);
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(healingCase));
        when(securityContextUtil.getCurrentHealerId()).thenReturn(UUID.randomUUID());
        when(healerService.getName(any())).thenReturn("Test Healer");
        return healingCase;
    }

    @Test
    void closeCase_whenHealingContinued_setsStatusContinuedAndSkipsFinalFeedbackNotification() {
        UUID caseId = UUID.randomUUID();
        HealingCase healingCase = buildCaseForClosure(caseId);
        CaseClosureRequest request = new CaseClosureRequest("Feeling better, continuing treatment", true);

        caseService.closeCase(caseId, request);

        assertEquals(CaseStatus.CONTINUED, healingCase.getStatus(),
                "isHealingContinued=true must set status to CONTINUED, not CLOSED");
        assertTrue(healingCase.getClosure().getIsHealingContinued());
        verify(caseRepository).save(healingCase);
        verify(notificationService, never()).sendFinalFeedbackNotification(any());
    }

    @Test
    void closeCase_whenHealingNotContinued_setsStatusClosedAndSendsFinalFeedbackNotification() {
        UUID caseId = UUID.randomUUID();
        HealingCase healingCase = buildCaseForClosure(caseId);
        CaseClosureRequest request = new CaseClosureRequest("Fully healed", false);

        caseService.closeCase(caseId, request);

        assertEquals(CaseStatus.CLOSED, healingCase.getStatus(),
                "isHealingContinued=false must set status to CLOSED");
        assertFalse(healingCase.getClosure().getIsHealingContinued());
        verify(caseRepository).save(healingCase);
        verify(notificationService).sendFinalFeedbackNotification(healingCase.getSessions().get(0));
    }

    @Test
    void closeCase_whenClosureAlreadyExists_updatesInPlaceInsteadOfInserting() {
        UUID caseId = UUID.randomUUID();
        HealingCase healingCase = buildCaseForClosure(caseId);

        caseService.closeCase(caseId, new CaseClosureRequest("First pass, continuing", true));
        CaseClosure firstClosure = healingCase.getClosure();
        assertEquals(CaseStatus.CONTINUED, healingCase.getStatus());

        // Simulates SessionService.activateCaseIfDraftOrContinued flipping the case back
        // to ACTIVE when a new session is logged, then the healer closing it again.
        caseService.closeCase(caseId, new CaseClosureRequest("Second pass, fully healed", false));

        assertSame(firstClosure, healingCase.getClosure(),
                "re-closing a CONTINUED case must reuse the existing CaseClosure row " +
                        "(uq_case_closure_case is one-per-case), not build a new one");
        assertEquals(CaseStatus.CLOSED, healingCase.getStatus());
        assertEquals("Second pass, fully healed", healingCase.getClosure().getAdditionalComments());
        assertFalse(healingCase.getClosure().getIsHealingContinued());
    }

    @Test
    void closeCase_withNoSessions_throwsIllegalArgumentException() {
        UUID caseId = UUID.randomUUID();
        HealingCase healingCase = HealingCase.builder()
                .patientId(UUID.randomUUID())
                .status(CaseStatus.DRAFT)
                .sessions(new ArrayList<>())
                .build();
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(healingCase));
        CaseClosureRequest request = new CaseClosureRequest("n/a", false);

        assertThrows(IllegalArgumentException.class, () -> caseService.closeCase(caseId, request));
        verify(caseRepository, never()).save(any());
    }

    /** Cases reach PDF generation only after consent; the snapshot reads the healer signature. */
    private static Declaration declaration(HealingCase healingCase) {
        return Declaration.builder()
                .healingCase(healingCase)
                .patientConsent(true)
                .healerDeclaration(true)
                .healerSignature("Test Healer")
                .build();
    }

}
