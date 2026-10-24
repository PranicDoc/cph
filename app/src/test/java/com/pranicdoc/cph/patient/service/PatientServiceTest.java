package com.pranicdoc.cph.patient.service;

import com.pranicdoc.cph.common.enums.CaseStatus;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.tenant.TenantContext;
import com.pranicdoc.cph.healing.dto.DeclarationRequest;
import com.pranicdoc.cph.healing.mapper.DeclarationMapper;
import com.pranicdoc.cph.healing.model.CaseMedicalSnapshot;
import com.pranicdoc.cph.healing.model.Declaration;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.patient.dto.MedicalHistoryRequest;
import com.pranicdoc.cph.patient.dto.PatientCaseResponse;
import com.pranicdoc.cph.patient.dto.PatientCreateRequest;
import com.pranicdoc.cph.patient.dto.PatientRegisterRequest;
import com.pranicdoc.cph.patient.dto.PatientRegisterResponse;
import com.pranicdoc.cph.patient.dto.PatientSort;
import com.pranicdoc.cph.patient.model.Patient;
import com.pranicdoc.cph.patient.repository.PatientRepository;
import com.pranicdoc.cph.patient.repository.RegistrationLinkRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock private PatientRepository patientRepository;
    @Mock private RegistrationLinkRepository registrationLinkRepository;
    @Mock private CaseRepository caseRepository;
    @Mock private DeclarationMapper declarationMapper;
    @Mock private SecurityContextUtil securityContextUtil;

    @InjectMocks private PatientService patientService;

    private static final Instant MAX_CASE_DATE = Instant.parse("9999-12-31T00:00:00Z");

    private UUID healerId;

    @BeforeEach
    void setUp() {
        healerId = UUID.randomUUID();
        TenantContext.set(healerId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAllPatients_noFilters_queriesAllStatusesOpenDateRangeAndDefaultSort() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        List<PatientCaseResponse> expected = List.of(mock(PatientCaseResponse.class));
        when(patientRepository.fetchPatientsWithLatestCaseStatus(
                eq(healerId), any(Collection.class), any(Instant.class), any(Instant.class), any(Sort.class)))
                .thenReturn(expected);

        List<PatientCaseResponse> result = patientService.getAllPatients(null, null, null, null);

        assertEquals(expected, result);
        verify(patientRepository).fetchPatientsWithLatestCaseStatus(
                healerId, List.of(CaseStatus.values()), Instant.EPOCH, MAX_CASE_DATE,
                PatientSort.CASE_DATE_DESC.toSort());
    }

    @Test
    void getAllPatients_statusFilter_passesSingleStatus() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);

        patientService.getAllPatients(CaseStatus.ACTIVE, null, null, null);

        verify(patientRepository).fetchPatientsWithLatestCaseStatus(
                healerId, List.of(CaseStatus.ACTIVE), Instant.EPOCH, MAX_CASE_DATE,
                PatientSort.CASE_DATE_DESC.toSort());
    }

    @Test
    void getAllPatients_dateRange_convertsToUtcBoundsWithExclusiveEnd() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);

        patientService.getAllPatients(
                null, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 5), null);

        verify(patientRepository).fetchPatientsWithLatestCaseStatus(
                healerId,
                List.of(CaseStatus.values()),
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-06T00:00:00Z"),
                PatientSort.CASE_DATE_DESC.toSort());
    }

    @Test
    void getAllPatients_explicitSort_passesMappedSort() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);

        patientService.getAllPatients(null, null, null, PatientSort.NAME_ASC);

        verify(patientRepository).fetchPatientsWithLatestCaseStatus(
                healerId, List.of(CaseStatus.values()), Instant.EPOCH, MAX_CASE_DATE,
                PatientSort.NAME_ASC.toSort());
    }

    @Test
    void getAllPatients_endDateBeforeStartDate_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                patientService.getAllPatients(
                        null, LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 1), null));
        verifyNoInteractions(patientRepository);
    }

    @Test
    void register_withMedicalHistory_attachesCaseMedicalSnapshotMatchingHistory() {
        when(securityContextUtil.getCurrentHealerIdOrNull()).thenReturn(healerId);
        when(patientRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(caseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Declaration declaration = Declaration.builder()
                .patientSignature("Santhosh G N")
                .patientConsent(true)
                .build();
        when(declarationMapper.toEntity(any(DeclarationRequest.class))).thenReturn(declaration);

        MedicalHistoryRequest historyRequest = new MedicalHistoryRequest(
                "GOOD", "MIXED", "", "DAILY",
                false, false, true, false, false, "", "", "");
        PatientCreateRequest patientRequest = new PatientCreateRequest(
                "Santhosh", "G N", null, null, null, "santhoshgn9@gmail.com",
                "7708680144", "Doctor", "No.34", "Perumal nagar",
                "Coimbatore", "Tamil Nadu", "641042", historyRequest);
        DeclarationRequest declarationRequest = new DeclarationRequest(
                true, true, "Santhosh G N", null, Instant.now().toString());

        PatientRegisterResponse response = patientService.register(
                new PatientRegisterRequest(patientRequest, declarationRequest));

        assertNotNull(response.caseId());
        ArgumentCaptor<HealingCase> caseCaptor = ArgumentCaptor.forClass(HealingCase.class);
        verify(caseRepository).save(caseCaptor.capture());
        HealingCase savedCase = caseCaptor.getValue();

        assertEquals(CaseStatus.LOCKED, savedCase.getStatus());
        CaseMedicalSnapshot snapshot = savedCase.getMedicalSnapshot();
        assertNotNull(snapshot, "REGISTER_PATIENT_WITH_CASE must snapshot the patient's "
                + "medical history onto the case, same as the self-register flow does");
        assertEquals("GOOD", snapshot.getGeneralHealth());
        assertEquals("MIXED", snapshot.getDiet());
        assertEquals("DAILY", snapshot.getExerciseFrequency());
        assertEquals(Boolean.TRUE, snapshot.getStressDepressionAnxiety());
        assertEquals(Boolean.FALSE, snapshot.getHypertension());
    }

    private PatientRegisterRequest registerRequest(boolean healerDeclaration) {
        PatientCreateRequest patientRequest = new PatientCreateRequest(
                "Santhosh", "G N", null, null, null, "santhoshgn9@gmail.com",
                "7708680144", "Doctor", "No.34", "Perumal nagar",
                "Coimbatore", "Tamil Nadu", "641042", null);
        return new PatientRegisterRequest(patientRequest, new DeclarationRequest(
                true, healerDeclaration, "Santhosh G N", null, Instant.now().toString()));
    }

    @Test
    void register_withoutTheHealersConsentConfirmation_isRejectedBeforeAnythingIsSaved() {
        when(securityContextUtil.getCurrentHealerIdOrNull()).thenReturn(healerId);

        assertThrows(IllegalArgumentException.class, () -> patientService.register(registerRequest(false)));

        verify(patientRepository, never()).save(any());
        verify(caseRepository, never()).save(any());
    }

    @Test
    void register_signatureThatIsNotThePatientsName_isRejected() {
        when(securityContextUtil.getCurrentHealerIdOrNull()).thenReturn(healerId);
        when(patientRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(declarationMapper.toEntity(any(DeclarationRequest.class))).thenReturn(
                Declaration.builder().patientSignature("Someone Else").patientConsent(true).build());

        assertThrows(IllegalStateException.class, () -> patientService.register(registerRequest(true)));

        // the registration is one transaction: the exception rolls the patient back, no case is written
        verify(caseRepository, never()).save(any());
    }

    @Test
    void discardUnclaimedSelfRegistered_deletesAPatientWithNoHealerAndNoCase() {
        UUID id = UUID.randomUUID();
        Patient patient = mock(Patient.class);
        when(patient.getHealerId()).thenReturn(null);
        when(patientRepository.findById(id)).thenReturn(Optional.of(patient));
        when(caseRepository.existsByPatientId(id)).thenReturn(false);

        assertTrue(patientService.discardUnclaimedSelfRegistered(id));

        verify(patient).softDelete();
        verify(patientRepository).save(patient);
    }

    @Test
    void discardUnclaimedSelfRegistered_leavesAPatientThatAlreadyHasACase() {
        UUID id = UUID.randomUUID();
        Patient patient = mock(Patient.class);
        when(patient.getHealerId()).thenReturn(null);
        when(patientRepository.findById(id)).thenReturn(Optional.of(patient));
        when(caseRepository.existsByPatientId(id)).thenReturn(true);

        assertFalse(patientService.discardUnclaimedSelfRegistered(id));

        verify(patient, never()).softDelete();
    }

    @Test
    void discardUnclaimedSelfRegistered_neverDeletesAPatientOwnedByAHealer() {
        UUID id = UUID.randomUUID();
        Patient patient = mock(Patient.class);
        when(patient.getHealerId()).thenReturn(healerId);
        when(patientRepository.findById(id)).thenReturn(Optional.of(patient));

        assertFalse(patientService.discardUnclaimedSelfRegistered(id));

        verify(patient, never()).softDelete();
    }

    @Test
    void discardUnclaimedSelfRegistered_unknownPatientIsANoOp() {
        UUID id = UUID.randomUUID();
        when(patientRepository.findById(id)).thenReturn(Optional.empty());

        assertFalse(patientService.discardUnclaimedSelfRegistered(id));
    }
}
