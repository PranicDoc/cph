package com.pranicdoc.cph.healing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pranicdoc.cph.healing.service.NotificationService;
import com.pranicdoc.cph.common.enums.CaseStatus;
import com.pranicdoc.cph.common.enums.ScanType;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.healing.mapper.CphCaseMapper;
import com.pranicdoc.cph.healing.mapper.CphCaseMapperImpl;
import com.pranicdoc.cph.healing.mapper.DeclarationMapper;
import com.pranicdoc.cph.healing.mapper.HealingCaseMapper;
import com.pranicdoc.cph.healing.model.*;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.healing.repository.PdfJobRepository;
import com.pranicdoc.cph.healing.service.impl.CaseService;
import com.pranicdoc.cph.patient.model.Patient;
import com.pranicdoc.cph.patient.repository.PatientRepository;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pranicdoc.cph.payments.CreditGateService;
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
    @Mock private MessageService messageService;
    @Mock private PdfJobRepository pdfJobRepository;
    @Mock private StorageService storageService;
    @Mock private NotificationService notificationService;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    @Mock private CreditGateService creditGateService;

    @InjectMocks
    private CaseService caseService;

    @BeforeEach
    void initTransactionSync() {
        TransactionSynchronizationManager.initSynchronization();
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
                .healerId(UUID.randomUUID())
                .status(CaseStatus.CLOSED)
                .sessions(new ArrayList<>(List.of(session)))
                .closure(closure)
                .symptoms(new ArrayList<>())
                .medications(new ArrayList<>())
                .build();

        Patient patient = org.mockito.Mockito.mock(Patient.class);

        // Wire mocks
        doNothing().when(creditGateService).checkAndDeduct(any(), any(), any());
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(healingCase));
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(pdfJobRepository.findTopByHealingCaseIdOrderByCreatedAtDesc(caseId))
                .thenReturn(Optional.empty());
        when(securityContextUtil.getCurrentClinicCode()).thenReturn("CLINIC01");

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
                .healerId(UUID.randomUUID())
                .status(CaseStatus.DRAFT)
                .sessions(new ArrayList<>())
                .symptoms(new ArrayList<>())
                .medications(new ArrayList<>())
                .build();
        Patient patient = org.mockito.Mockito.mock(Patient.class);

        doNothing().when(creditGateService).checkAndDeduct(any(), any(), any());
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(healingCase));
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(pdfJobRepository.findTopByHealingCaseIdOrderByCreatedAtDesc(caseId)).thenReturn(Optional.empty());
        when(securityContextUtil.getCurrentClinicCode()).thenReturn("CLINIC01");
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
                .healerId(UUID.randomUUID())
                .status(CaseStatus.DRAFT)
                .sessions(new ArrayList<>())
                .symptoms(new ArrayList<>())
                .medications(new ArrayList<>())
                .build();

        doThrow(new InsufficientCreditsException()).when(creditGateService).checkAndDeduct(any(), any(), any());
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(healingCase));

        assertThrows(InsufficientCreditsException.class,
                () -> caseService.postMessageToGeneratePdf(caseId));

        verify(pdfJobRepository, never()).save(any());
    }
}
