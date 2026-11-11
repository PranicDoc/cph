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
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
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
    @Mock private com.pranicdoc.cph.healing.repository.CaseMedicalSnapshotRepository caseMedicalSnapshotRepository;
    @Mock private com.pranicdoc.cph.patient.repository.PatientMedicalHistoryRepository medicalHistoryRepository;
    @Mock private DeclarationMapper declarationMapper;
    @Mock private SecurityContextUtil securityContextUtil;
    @Mock private PatientTrashService trashService;

    @InjectMocks private PatientService patientService;

    private static final Instant MAX_CASE_DATE = Instant.parse("9999-12-31T00:00:00Z");

    private UUID healerId;

    @BeforeEach
    void setUp() {
        healerId = UUID.randomUUID();
        TenantContext.set(healerId);
    }

    // ── ownership: only the owner reads their patient ──

    private Patient ownedPatient(UUID owner) {
        Patient p = Patient.create(new PatientCreateRequest("Asha", "Rao", null, null, null, null,
                "+911111111111", null, null, null, "Pune", null, null, null), owner);
        p.setId(UUID.randomUUID());
        return p;
    }

    @Test
    void getById_anotherHealersPatientIsNotFound_butASuperAdminCanRead() {
        Patient p = ownedPatient(UUID.randomUUID());
        when(patientRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER", "SUPADM");
        when(securityContextUtil.getCurrentHealerIdOrNull()).thenReturn(healerId);

        assertThrows(ResourceNotFoundException.class, () -> patientService.getById(p.getId()));
        assertEquals("Asha", patientService.getById(p.getId()).firstName());
    }

    @Test
    void medicalHistory_anotherHealersPatientIsNotFound_forReadAndWrite() {
        Patient p = ownedPatient(UUID.randomUUID());
        when(patientRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerIdOrNull()).thenReturn(healerId);
        MedicalHistoryRequest request = new MedicalHistoryRequest("GOOD", "MIXED", "YOGA", "WEEKLY",
                false, false, false, false, false, null, null, null);

        assertThrows(ResourceNotFoundException.class, () -> patientService.getMedicalHistory(p.getId()));
        assertThrows(ResourceNotFoundException.class, () -> patientService.updateMedicalHistory(p.getId(), request));
        verify(patientRepository, never()).save(any());
    }

    // ── editing medical history refreshes every case's medical snapshot (the PDF reads the snapshot) ──

    private HealingCase caseWithSnapshot(Patient p, CaseStatus status, String notes) {
        HealingCase c = HealingCase.builder().build();
        c.setId(UUID.randomUUID());
        c.setPatientId(p.getId());
        c.setStatus(status);
        c.setMedicalSnapshot(CaseMedicalSnapshot.builder().healingCase(c)
                .generalHealth("FAIR").stressDepressionAnxiety(true).additionalNotes(notes).build());
        return c;
    }

    private Patient ownPatient() {
        Patient p = ownedPatient(healerId);
        when(patientRepository.findById(p.getId())).thenReturn(Optional.of(p));
        lenient().when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        lenient().when(securityContextUtil.getCurrentHealerIdOrNull()).thenReturn(healerId);
        return p;
    }

    @Test
    void updateMedicalHistory_refreshesSnapshotOfEveryCase_includingClearedNotes() {
        Patient p = ownPatient();
        HealingCase open = caseWithSnapshot(p, CaseStatus.ACTIVE, "old notes");
        HealingCase closed = caseWithSnapshot(p, CaseStatus.CLOSED, "old notes");
        when(caseRepository.findAllByPatientId(p.getId())).thenReturn(List.of(open, closed));

        patientService.updateMedicalHistory(p.getId(), new MedicalHistoryRequest("GOOD", "MIXED", "YOGA", "WEEKLY",
                true, false, false, false, false, null, null, null));

        for (HealingCase c : List.of(open, closed)) {
            CaseMedicalSnapshot snap = c.getMedicalSnapshot();
            assertNull(snap.getAdditionalNotes(), "cleared notes must not linger in the PDF");
            assertEquals("GOOD", snap.getGeneralHealth());
            assertEquals(Boolean.FALSE, snap.getStressDepressionAnxiety());
            assertEquals(Boolean.TRUE, snap.getHypertension());
            assertSame(c, snap.getHealingCase());
        }
    }

    @Test
    void updateMedicalHistory_createsSnapshotForCaseOpenedBeforeAnyHistory() {
        Patient p = ownPatient();
        HealingCase c = HealingCase.builder().build();
        c.setId(UUID.randomUUID());
        when(caseRepository.findAllByPatientId(p.getId())).thenReturn(List.of(c));

        patientService.updateMedicalHistory(p.getId(), new MedicalHistoryRequest("GOOD", null, null, null,
                null, null, null, null, null, null, null, "new notes"));

        assertNotNull(c.getMedicalSnapshot());
        assertEquals("new notes", c.getMedicalSnapshot().getAdditionalNotes());
        assertSame(c, c.getMedicalSnapshot().getHealingCase());
    }

    @Test
    void updateMedicalHistory_patientWithoutCases_justSavesHistory() {
        Patient p = ownPatient();
        when(caseRepository.findAllByPatientId(p.getId())).thenReturn(List.of());

        patientService.updateMedicalHistory(p.getId(), new MedicalHistoryRequest("GOOD", null, null, null,
                null, null, null, null, null, null, null, "n"));

        assertEquals("n", p.getMedicalHistory().getAdditionalNotes());
        verify(patientRepository).save(p);
    }

    // ── upsert after rows were deleted (hard or soft) ──

    @Test
    void updateMedicalHistory_restoresSoftDeletedCaseSnapshot_insteadOfInsertingASecondRow() {
        Patient p = ownPatient();
        HealingCase c = HealingCase.builder().build();       // snapshot hidden by is_deleted
        c.setId(UUID.randomUUID());
        CaseMedicalSnapshot deleted = CaseMedicalSnapshot.builder().healingCase(c).additionalNotes("old").build();
        deleted.softDelete();
        when(caseRepository.findAllByPatientId(p.getId())).thenReturn(List.of(c));
        when(caseMedicalSnapshotRepository.findIncludingDeletedByHealingCaseId(c.getId())).thenReturn(Optional.of(deleted));

        patientService.updateMedicalHistory(p.getId(), new MedicalHistoryRequest("GOOD", null, null, null,
                null, null, null, null, null, null, null, "new"));

        assertSame(deleted, c.getMedicalSnapshot(), "must reuse the row (uq_case_medical_snapshot_case)");
        assertFalse(deleted.isDeleted());
        assertEquals("new", deleted.getAdditionalNotes());
    }

    @Test
    void updateMedicalHistory_restoresSoftDeletedHistory_insteadOfInsertingASecondRow() {
        Patient p = ownPatient();                             // getMedicalHistory() == null: row hidden by is_deleted
        com.pranicdoc.cph.patient.model.PatientMedicalHistory deleted = new com.pranicdoc.cph.patient.model.PatientMedicalHistory();
        deleted.setAdditionalNotes("old");
        deleted.softDelete();
        when(medicalHistoryRepository.findIncludingDeletedByPatientId(p.getId())).thenReturn(Optional.of(deleted));
        when(caseRepository.findAllByPatientId(p.getId())).thenReturn(List.of());

        patientService.updateMedicalHistory(p.getId(), new MedicalHistoryRequest("GOOD", null, null, null,
                null, null, null, null, null, null, null, "new"));

        assertSame(deleted, p.getMedicalHistory(), "must reuse the row (uq_patient_medical_history_pid)");
        assertFalse(deleted.isDeleted());
        assertSame(p, deleted.getPatient());
        assertEquals("new", deleted.getAdditionalNotes());
    }

    @Test
    void updateMedicalHistory_hardDeletedHistoryAndSnapshot_areCreated() {
        Patient p = ownPatient();
        HealingCase c = HealingCase.builder().build();
        c.setId(UUID.randomUUID());
        when(medicalHistoryRepository.findIncludingDeletedByPatientId(p.getId())).thenReturn(Optional.empty());
        when(caseRepository.findAllByPatientId(p.getId())).thenReturn(List.of(c));
        when(caseMedicalSnapshotRepository.findIncludingDeletedByHealingCaseId(c.getId())).thenReturn(Optional.empty());

        patientService.updateMedicalHistory(p.getId(), new MedicalHistoryRequest("GOOD", null, null, null,
                null, null, null, null, null, null, null, "new"));

        assertEquals("new", p.getMedicalHistory().getAdditionalNotes());
        assertSame(p, p.getMedicalHistory().getPatient());
        assertEquals(p.getHealerId(), p.getMedicalHistory().getHealerId(), "new history belongs to the patient's healer");
        assertEquals("new", c.getMedicalSnapshot().getAdditionalNotes());
        assertSame(c, c.getMedicalSnapshot().getHealingCase());
    }

    /** Guards future fields: every PatientMedicalHistory field the snapshot also has must be copied. */
    @Test
    void snapshotCopiesEveryMedicalHistoryField() throws Exception {
        com.pranicdoc.cph.patient.model.PatientMedicalHistory h = new com.pranicdoc.cph.patient.model.PatientMedicalHistory();
        int copied = 0;
        for (java.lang.reflect.Field f : h.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            f.setAccessible(true);
            if (f.getType() == String.class) f.set(h, "v-" + f.getName());
            else if (f.getType() == Boolean.class) f.set(h, Boolean.TRUE);
        }
        CaseMedicalSnapshot snap = CaseMedicalSnapshot.upsert(HealingCase.builder().build(), h, id -> Optional.empty());
        for (java.lang.reflect.Field hf : h.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(hf.getModifiers())) continue;
            if (hf.getType() != String.class && hf.getType() != Boolean.class) continue;
            java.lang.reflect.Field sf;
            try { sf = CaseMedicalSnapshot.class.getDeclaredField(hf.getName()); }
            catch (NoSuchFieldException e) { fail("CaseMedicalSnapshot is missing medical-history field '" + hf.getName()
                    + "': add it (+ migration) and copy it in CaseMedicalSnapshot.copyFrom, or the PDF won't show it"); return; }
            hf.setAccessible(true); sf.setAccessible(true);
            assertEquals(hf.get(h), sf.get(snap), "CaseMedicalSnapshot.copyFrom does not copy '" + hf.getName() + "'");
            copied++;
        }
        assertTrue(copied >= 12, "expected all 12 medical-history fields, checked " + copied);
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

    @Test
    void softDelete_movesAnOwnedPatientToTheTrash() {
        UUID id = UUID.randomUUID();
        Patient patient = mock(Patient.class);
        when(patient.getHealerId()).thenReturn(healerId);
        when(patientRepository.findById(id)).thenReturn(Optional.of(patient));
        when(securityContextUtil.getCurrentRole()).thenReturn("PRACTITIONER");
        when(securityContextUtil.getCurrentHealerIdOrNull()).thenReturn(healerId);

        patientService.softDelete(id);

        verify(trashService).moveToTrash(id);
    }

    @Test
    void softDelete_ofAnotherHealersPatientIsNotFoundAndDeletesNothing() {
        UUID id = UUID.randomUUID();
        Patient patient = mock(Patient.class);
        when(patient.getHealerId()).thenReturn(UUID.randomUUID());
        when(patientRepository.findById(id)).thenReturn(Optional.of(patient));
        when(securityContextUtil.getCurrentRole()).thenReturn("PRACTITIONER");
        when(securityContextUtil.getCurrentHealerIdOrNull()).thenReturn(healerId);

        assertThrows(ResourceNotFoundException.class, () -> patientService.softDelete(id));

        verify(trashService, never()).moveToTrash(any());
    }

    @Test
    void trashAndRestore_areForSuperAdminsOnly() {
        UUID id = UUID.randomUUID();
        when(securityContextUtil.getCurrentRole()).thenReturn("PRACTITIONER");

        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> patientService.listTrash());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> patientService.restore(id));

        verify(trashService, never()).listTrash();
        verify(trashService, never()).restore(any());
    }

    @Test
    void restore_bringsThePatientBackForASuperAdmin() {
        UUID id = UUID.randomUUID();
        when(securityContextUtil.getCurrentRole()).thenReturn("SUPADM");

        patientService.restore(id);

        verify(trashService).restore(id);
    }
}
