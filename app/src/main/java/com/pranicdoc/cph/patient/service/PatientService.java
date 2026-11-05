package com.pranicdoc.cph.patient.service;

import com.pranicdoc.cph.analytics.dto.DashboardPatientGrowth;
import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.dto.PageResponse;
import com.pranicdoc.cph.common.enums.CaseStatus;
import com.pranicdoc.cph.common.exception.CaseLockedException;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.tenant.TenantContext;
import com.pranicdoc.cph.healing.mapper.DeclarationMapper;
import com.pranicdoc.cph.healing.model.CaseMedicalSnapshot;
import com.pranicdoc.cph.healing.model.Declaration;
import com.pranicdoc.cph.healing.model.DeclarationDates;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.patient.dto.*;
import com.pranicdoc.cph.patient.model.Patient;
import com.pranicdoc.cph.patient.model.PatientMedicalHistory;
import com.pranicdoc.cph.patient.model.RegistrationLink;
import com.pranicdoc.cph.patient.repository.PatientRepository;
import com.pranicdoc.cph.patient.repository.RegistrationLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PatientService {

    private final PatientRepository patientRepository;
    private final PatientTrashService trashService;
    private final RegistrationLinkRepository registrationLinkRepository;
    private final CaseRepository caseRepository;
    private final DeclarationMapper declarationMapper;
    private final SecurityContextUtil securityContextUtil;
    @Value("${app.base.url:http://localhost:3000}")
    private String baseUrl;

    // Upper bound for the open-ended case-date filter; must stay within the DB timestamp range.
    private static final Instant MAX_CASE_DATE = Instant.parse("9999-12-31T00:00:00Z");

    @Auditable(action = "CREATE_PATIENT")
    public PatientResponse create(PatientCreateRequest request) {
        UUID healerId = securityContextUtil.getCurrentHealerIdOrNull();
        if (healerId == null) {
            throw new IllegalStateException("Missing healer context");
        }
        Patient patient = Patient.create(request, healerId);
        return toResponse(patientRepository.save(patient));
    }

    // TODO Add SSE for created self reg patients to that healer alone
    @Auditable(action = "SELF_REGISTER_PATIENT")
    public PatientResponse selfRegister(PatientCreateRequest request) {
        // SELF_REG tokens carry a random UUID as subject — no real healer in context.
        // healerId is left null; these patients are excluded from dashboard queries
        // until a healer manually claims them or V4 is applied after orphan cleanup.
        Patient patient = Patient.create(request, null);
        return toResponse(patientRepository.save(patient));
    }

    @Auditable(action = "REGISTER_PATIENT_WITH_CASE")
    @Transactional
    public PatientRegisterResponse register(PatientRegisterRequest request) {
        UUID healerId = securityContextUtil.getCurrentHealerIdOrNull();
        if (healerId == null) throw new IllegalStateException("Missing healer context");

        // The healer fills this form for the patient, so they must confirm the patient agreed to it.
        if (!request.declaration().healerDeclaration()) {
            throw new IllegalArgumentException(
                    "Please confirm that you have the patient's consent to fill in their details");
        }

        Patient patient = patientRepository.save(Patient.create(request.patient(), healerId));

        Declaration declaration = declarationMapper.toEntity(request.declaration());
        if (!declaration.getPatientSignature().equals(patient.getFirstName().strip() + " " + patient.getLastName().strip())) {
            throw new IllegalStateException("Signature mismatch in declaration");
        }

        HealingCase healingCase = HealingCase.create(patient.getId(), healerId, declaration);

        PatientMedicalHistory history = patient.getMedicalHistory();
        if (history != null) {
            CaseMedicalSnapshot snapshot = CaseMedicalSnapshot.builder()
                    .healingCase(healingCase)
                    .generalHealth(history.getGeneralHealth())
                    .diet(history.getDiet())
                    .exercise(history.getExercise())
                    .exerciseFrequency(history.getExerciseFrequency())
                    .hypertension(history.getHypertension())
                    .giIssues(history.getGiIssues())
                    .stressDepressionAnxiety(history.getStressDepressionAnxiety())
                    .pregnant(history.getPregnant())
                    .cancer(history.getCancer())
                    .cancerLocation(history.getCancerLocation())
                    .cancerDetails(history.getCancerDetails())
                    .additionalNotes(history.getAdditionalNotes())
                    .build();
            healingCase.setMedicalSnapshot(snapshot);
        }

        HealingCase savedCase = caseRepository.save(healingCase);

        return new PatientRegisterResponse(patient.getId(), savedCase.getId(), patient.getFirstName(), patient.getLastName());
    }

    /**
     * Removes a self-registered patient whose registration could not be completed (the declaration
     * step failed), so no half-registered record is left behind. Only an unclaimed patient (no healer)
     * with no case is removed; runs in its own transaction so it survives the caller's rollback.
     *
     * @return true if the patient was removed
     */
    @Auditable(action = "DISCARD_SELF_REGISTERED_PATIENT")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean discardUnclaimedSelfRegistered(UUID patientId) {
        Patient patient = patientRepository.findById(patientId).orElse(null);
        if (patient == null || patient.getHealerId() != null || caseRepository.existsByPatientId(patientId)) {
            return false;
        }
        patient.softDelete();
        patientRepository.save(patient);
        return true;
    }

    @Transactional(readOnly = true)
    public PatientResponse getById(UUID id) {
        return toResponse(findOwnedOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<PatientResponse> search(String query, Pageable pageable) {
        Page<PatientResponse> page = patientRepository.searchByName(query, pageable)
                .map(this::toResponse);
        return PageResponse.from(page);
    }

    @Auditable(action = "DELETE_PATIENT")
    public void softDelete(UUID id) {
        findOwnedOrThrow(id);
        trashService.moveToTrash(id);
    }

    private void assertSuperAdmin() {
        if (!"SUPADM".equals(securityContextUtil.getCurrentRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a super admin can do this");
        }
    }

    @Transactional(readOnly = true)
    public List<TrashedPatientResponse> listTrash() {
        assertSuperAdmin();
        return trashService.listTrash();
    }

    @Auditable(action = "RESTORE_PATIENT")
    public void restore(UUID id) {
        assertSuperAdmin();
        trashService.restore(id);
    }

    // --- Medical History ---

    @Auditable(action = "UPDATE_MEDICAL_HISTORY")
    public MedicalHistoryResponse updateMedicalHistory(UUID patientId, MedicalHistoryRequest request) {
        Patient patient = findOwnedOrThrow(patientId);
        PatientMedicalHistory history = patient.getMedicalHistory();

        if (history == null) {
            history = new PatientMedicalHistory();
            history.setPatient(patient);
            patient.setMedicalHistory(history);
        }

        history.setGeneralHealth(request.generalHealth());
        history.setDiet(request.diet());
        history.setExercise(request.exercise());
        history.setExerciseFrequency(request.exerciseFrequency());
        history.setHypertension(request.hypertension());
        history.setGiIssues(request.giIssues());
        history.setStressDepressionAnxiety(request.stressDepressionAnxiety());
        history.setPregnant(request.pregnant());
        history.setCancer(request.cancer());
        history.setCancerLocation(request.cancerLocation());
        history.setCancerDetails(request.cancerDetails());
        history.setAdditionalNotes(request.additionalNotes());

        patientRepository.save(patient);
        return toMedicalHistoryResponse(history);
    }

    @Transactional(readOnly = true)
    public MedicalHistoryResponse getMedicalHistory(UUID patientId) {
        Patient patient = findOwnedOrThrow(patientId);
        if (patient.getMedicalHistory() == null) {
            throw new ResourceNotFoundException("Medical history not found for patient: " + patientId);
        }
        return toMedicalHistoryResponse(patient.getMedicalHistory());
    }

    // --- Declaration date (of the patient's latest case) ---

    @Transactional(readOnly = true)
    public DeclarationDateResponse getDeclarationDate(UUID patientId) {
        findOwnedOrThrow(patientId);
        HealingCase latest = caseRepository.findFirstByPatientIdOrderByCreatedAtDesc(patientId).orElse(null);
        if (latest == null || latest.getDeclaration() == null) {
            return new DeclarationDateResponse(null, false, false);
        }
        return new DeclarationDateResponse(
                DeclarationDates.toDate(latest.getDeclaration().getSignedAt()),
                true,
                latest.getStatus() != CaseStatus.LOCKED);
    }

    @Auditable(action = "UPDATE_DECLARATION_DATE")
    public DeclarationDateResponse updateDeclarationDate(UUID patientId, DeclarationDateRequest request) {
        findOwnedOrThrow(patientId);
        HealingCase latest = caseRepository.findFirstByPatientIdOrderByCreatedAtDesc(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("No case found for patient: " + patientId));
        if (latest.getStatus() == CaseStatus.LOCKED) {
            throw new CaseLockedException();
        }
        Declaration declaration = latest.getDeclaration();
        if (declaration == null) {
            throw new ResourceNotFoundException("No declaration found for patient: " + patientId);
        }
        declaration.setSignedAt(DeclarationDates.toSignedAt(request.declarationDate()));
        caseRepository.save(latest);
        return new DeclarationDateResponse(request.declarationDate(), true, true);
    }

    // --- Helpers ---

    private Patient findOrThrow(UUID id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", id));
    }

    /**
     * Loading by id skips the tenant filter, so check the owner here: a healer reaches only their own
     * patients (a super admin reaches any); anyone else gets "not found", as if it did not exist.
     */
    private Patient findOwnedOrThrow(UUID id) {
        Patient patient = findOrThrow(id);
        if (!"SUPADM".equals(securityContextUtil.getCurrentRole())) {
            UUID healerId = securityContextUtil.getCurrentHealerIdOrNull();
            if (healerId == null || !healerId.equals(patient.getHealerId())) {
                throw new ResourceNotFoundException("Patient", id);
            }
        }
        return patient;
    }

    private PatientResponse toResponse(Patient p) {
        return new PatientResponse(
                p.getId(), p.getFirstName(), p.getLastName(), p.getDob(),
                p.getGender(), p.getMaritalStatus(), p.getEmail(), p.getContact(),
                p.getOccupation(), p.getAddressLine1(), p.getAddressLine2(),
                p.getCity(), p.getState(), p.getZip(),
                p.getCreatedAt(), p.getUpdatedAt(), p.getPatientCode()
        );
    }

    private MedicalHistoryResponse toMedicalHistoryResponse(PatientMedicalHistory h) {
        return new MedicalHistoryResponse(
                h.getId(), h.getPatient().getId(),
                h.getGeneralHealth(), h.getDiet(), h.getExercise(), h.getExerciseFrequency(),
                h.getHypertension(), h.getGiIssues(), h.getStressDepressionAnxiety(),
                h.getPregnant(), h.getCancer(), h.getCancerLocation(),
                h.getCancerDetails(), h.getAdditionalNotes()
        );
    }

    @Auditable(action = "GENERATE_LINK")
    public Map<String, String> generateLink() {
        // Value is already the healerId post-26-03 (TenantContext now holds the real
        // healerId) — this rename just makes the healerId URL segment name honest. The
        // consuming frontend route param is renamed in 26-10; the SELF_REG token identity
        // itself is hardened in 26-11.
        String healerId = TenantContext.get().toString();
        String randomUuid = UUID.randomUUID().toString();
        RegistrationLink link = new RegistrationLink();
        link.setUuid(randomUuid);
        link.setStatus(RegistrationLink.Status.ACTIVE);
        link.setExpiresAt(Instant.now().plusSeconds(172800)); // 48 hours
        registrationLinkRepository.save(link);
        return Map.of(
                "link", baseUrl + "/self-register/" + healerId + "/" + randomUuid
        );
    }

    @Auditable(action = "FETCH_PATIENTS")
    @Transactional(readOnly = true)
    public List<PatientCaseResponse> getAllPatients(CaseStatus status, LocalDate startDate, LocalDate endDate,
                                                    PatientSort sort) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must not be before startDate");
        }
        UUID healerId = securityContextUtil.getCurrentHealerId();
        List<CaseStatus> statuses = status != null ? List.of(status) : List.of(CaseStatus.values());
        Instant caseFrom = startDate != null
                ? startDate.atStartOfDay(ZoneOffset.UTC).toInstant()
                : Instant.EPOCH;
        Instant caseTo = endDate != null
                ? endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                : MAX_CASE_DATE;
        PatientSort effectiveSort = sort != null ? sort : PatientSort.DEFAULT;
        return patientRepository.fetchPatientsWithLatestCaseStatus(
                healerId, statuses, caseFrom, caseTo, effectiveSort.toSort());
    }

    @Auditable(action = "FETCH_ACTIVE_PATIENTS")
    @Transactional(readOnly = true)
    public long getAllActivePatientsCount() {
        UUID healerId = securityContextUtil.getCurrentHealerId();
        return patientRepository.fetchAllActivePatientsCount(healerId);
    }
    @Auditable(action = "FETCH_PATIENT_GROWTH")
    @Transactional(readOnly = true)
    public List<DashboardPatientGrowth> getPatientGrowth(UUID healerId, LocalDate from, LocalDate to) {
        return patientRepository.getPatientGrowth(healerId, from, to).stream()
                .map(p -> new DashboardPatientGrowth(p.getDate(), p.getCount()))
                .toList();
    }

}