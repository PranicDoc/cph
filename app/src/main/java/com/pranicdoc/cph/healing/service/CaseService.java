package com.pranicdoc.cph.healing.service;

import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.dto.PageResponse;
import com.pranicdoc.cph.common.enums.CaseStatus;
import com.pranicdoc.cph.common.exception.ConsentRequiredException;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.healing.dto.*;
import com.pranicdoc.cph.healing.mapper.DeclarationMapper;
import com.pranicdoc.cph.healing.mapper.HealingCaseMapper;
import com.pranicdoc.cph.healing.model.*;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.patient.model.Patient;
import com.pranicdoc.cph.patient.model.PatientMedicalHistory;
import com.pranicdoc.cph.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CaseService {

    private final CaseRepository caseRepository;
    private final PatientRepository patientRepository;
    private final SecurityContextUtil securityContextUtil;
    private final DeclarationMapper declarationMapper;
    private final HealingCaseMapper healingCaseMapper;

    /**
     * Create case → auto-snapshot PatientMedicalHistory → CaseMedicalSnapshot.
     */
    @Auditable(action = "CREATE_CASE")
    @Transactional
    public CaseResponse create(CaseCreateRequest request) {
        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", request.patientId()));
        boolean exists = caseRepository.existsByPatientIdAndClinicIdAndStatus(
                request.patientId(),
                securityContextUtil.getCurrentClinicId(),
                CaseStatus.DRAFT
        );
        if (exists) {
            throw new IllegalStateException("Draft already exists for this patient");
        }
        Declaration declaration = declarationMapper.toEntity(request.declaration());
        if(!checkSignature(declaration, patient)) throw new IllegalStateException("Signature Mismatch in declaration");
        HealingCase healingCase = HealingCase.create(
                request.patientId(),
                securityContextUtil.getCurrentHealerId(),
                declaration
        );
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
        return toResponse(caseRepository.save(healingCase));
    }

    private boolean checkSignature(Declaration declaration, Patient patient) {
        return declaration.getPatientSignature()
                .equals(patient.getFirstName() + " " + patient.getLastName());
    }

    @Transactional(readOnly = true)
    public CaseResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<CaseResponse> getByPatientId(UUID patientId, Pageable pageable) {
        return PageResponse.from(
                caseRepository.findByPatientId(patientId, pageable).map(this::toResponse));
    }

    // --- Symptoms ---

    @Auditable(action = "ADD_SYMPTOM")
    public void addSymptom(UUID caseId, SymptomRequest request) {
        HealingCase healingCase = findOrThrow(caseId);
        CaseSymptom symptom = CaseSymptom.builder()
                .healingCase(healingCase)
                .symptomName(request.symptomName())
                .description(request.description())
                .severity(request.severity())
                .addedDate(LocalDate.now())
                .resolved(false)
                .build();
        healingCase.getSymptoms().add(symptom);
        caseRepository.save(healingCase);
    }

    @Auditable(action = "RESOLVE_SYMPTOM")
    public void resolveSymptom(UUID caseId, UUID symptomId) {
        HealingCase healingCase = findOrThrow(caseId);
        healingCase.getSymptoms().stream()
                .filter(s -> s.getId().equals(symptomId))
                .findFirst()
                .ifPresent(s -> {
                    s.setResolved(true);
                    s.setResolvedDate(LocalDate.now());
                });
        caseRepository.save(healingCase);
    }

    // --- Medications ---

    @Auditable(action = "ADD_MEDICATION")
    public void addMedication(UUID caseId, MedicationRequest request) {
        HealingCase healingCase = findOrThrow(caseId);
        CaseMedication medication = CaseMedication.builder()
                .healingCase(healingCase)
                .medicationName(request.medicationName())
                .dosage(request.dosage())
                .frequency(request.frequency())
                .notes(request.notes())
                .build();
        healingCase.getMedications().add(medication);
        caseRepository.save(healingCase);
    }

    // --- Declaration ---

    @Auditable(action = "SIGN_DECLARATION")
    public void signDeclaration(UUID caseId, DeclarationRequest request) {
        HealingCase healingCase = findOrThrow(caseId);
        Declaration declaration = Declaration.builder()
                .healingCase(healingCase)
                .patientConsent(request.patientConsent())
                .healerDeclaration(request.healerDeclaration())
                .signedAt(Instant.now())
                .patientSignature(request.patientSignature())
                .healerSignature(request.healerSignature())
                .build();
        healingCase.setDeclaration(declaration);
        caseRepository.save(healingCase);
    }

    // --- Case Closure ---

    @Auditable(action = "CLOSE_CASE")
    public void closeCase(UUID caseId, CaseClosureRequest request) {
        HealingCase healingCase = findOrThrow(caseId);
        CaseClosure closure = CaseClosure.builder()
                .healingCase(healingCase)
                .closureDate(LocalDate.now())
                .outcome(request.outcome())
                .remarks(request.remarks())
                .closedBy(securityContextUtil.getCurrentHealerId())
                .build();
        healingCase.setClosure(closure);
        healingCase.setStatus(CaseStatus.CLOSED);
        caseRepository.save(healingCase);
    }

    @Auditable(action = "DELETE_CASE")
    public void softDelete(UUID id) {
        HealingCase healingCase = findOrThrow(id);
        healingCase.softDelete();
        caseRepository.save(healingCase);
    }

    // --- Consent check (used by other services) ---

    public void requireConsent(UUID caseId) {
        HealingCase healingCase = findOrThrow(caseId);
        if (healingCase.getDeclaration() == null || !healingCase.getDeclaration().isPatientConsent()) {
            throw new ConsentRequiredException();
        }
    }

    public HealingCase findOrThrow(UUID id) {
        return caseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("HealingCase", id));
    }

    private CaseResponse toResponse(HealingCase c) {
        return new CaseResponse(
                c.getId(), c.getPatientId(), c.getHealerId(),
                c.getMedicalCondition(), c.getHealingDetails(), c.getLabFindings(),
                c.getComplementaryTreatments(), c.getComplementaryDetails(),
                c.getInfectiousIllness(), c.getInfectiousDetails(),
                c.getBasicBookPg(), c.getAdvancedBookPg(), c.getPsychotherapyPg(),
                c.getStatus(), c.getCreatedAt(), c.getUpdatedAt(), c.getMedicalSnapshot(),
                c.getSessions().stream().sorted(
                        Comparator.comparingInt(HealingSession::getSessionNumber)).toList(),
                c.getSymptoms(),
                c.getMedications()
        );
    }

    @Auditable(action = "UPDATE_CASE")
    @Transactional
    public CaseResponse updateCaseDetails(CaseUpdateRequest caseUpdateRequest) {
        HealingCase entity = caseRepository.findById(caseUpdateRequest.healingCaseId()).orElseThrow(
                () -> new ResourceNotFoundException("Healing Case not found"));
        healingCaseMapper.toEntity(caseUpdateRequest, entity);
        if(caseUpdateRequest.symptoms() != null && !caseUpdateRequest.symptoms().isEmpty()) {
            entity.getSymptoms().clear();
            for (SymptomRequest symptomRequest : caseUpdateRequest.symptoms()) {
                CaseSymptom symptom = healingCaseMapper.toSymptom(symptomRequest);
                symptom.setHealingCase(entity);
                entity.getSymptoms().add(symptom);
            }
        }
        if(caseUpdateRequest.medications() != null && !caseUpdateRequest.medications().isEmpty()) {
            entity.getMedications().clear();
            for(MedicationRequest medicationRequest : caseUpdateRequest.medications()) {
                CaseMedication medication = healingCaseMapper.toMedication(medicationRequest);
                medication.setHealingCase(entity);
                entity.getMedications().add(medication);
            }
        }
        if (caseUpdateRequest.healingDetails() != null) {
            entity.setHealingDetails(null);
            HealingDetails details = healingCaseMapper
                    .toHealingDetails(caseUpdateRequest.healingDetails());
            details.setHealingCase(entity);
            if (details.getTreatmentSteps() != null) {
                for (TreatmentStep step : details.getTreatmentSteps()) {
                    step.setHealingDetails(details);
                }
            }
            entity.setHealingDetails(details);
        }
        if(CaseStatus.DRAFT.equals(entity.getStatus())) entity.setStatus(CaseStatus.ACTIVE);
        return toResponse(caseRepository.save(entity));
    }
}
