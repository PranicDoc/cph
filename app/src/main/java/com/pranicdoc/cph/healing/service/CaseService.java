package com.pranicdoc.cph.healing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.dto.PageResponse;
import com.pranicdoc.cph.common.enums.CaseStatus;
import com.pranicdoc.cph.common.enums.JobStatus;
import com.pranicdoc.cph.common.exception.ConsentRequiredException;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.security.CookieUtil;
import com.pranicdoc.cph.common.security.JwtAuthFilter;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.patient.repository.RegistrationLinkRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import com.pranicdoc.cph.healing.dto.*;
import com.pranicdoc.cph.healing.mapper.CphCaseMapper;
import com.pranicdoc.cph.healing.mapper.DeclarationMapper;
import com.pranicdoc.cph.healing.mapper.HealingCaseMapper;
import com.pranicdoc.cph.healing.model.*;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.healing.repository.PdfJobRepository;
import com.pranicdoc.cph.patient.model.Patient;
import com.pranicdoc.cph.patient.model.PatientMedicalHistory;
import com.pranicdoc.cph.patient.repository.PatientRepository;
import com.pranicdoc.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class CaseService {

    private final CaseRepository caseRepository;
    private final PatientRepository patientRepository;
    private final SecurityContextUtil securityContextUtil;
    private final DeclarationMapper declarationMapper;
    private final HealingCaseMapper healingCaseMapper;
    private final CphCaseMapper cphCaseMapper;
    private final MessageService messageService;
    private final PdfJobRepository pdfJobRepository;
    private final StorageService storageService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final RegistrationLinkRepository registrationLinkRepository;
    private final JwtService jwtService;
    private final CookieUtil cookieUtil;
    /**
     * Create case → auto-snapshot PatientMedicalHistory → CaseMedicalSnapshot.
     */
    @Auditable(action = "CREATE_CASE")
    @Transactional
    public CaseResponse create(CaseCreateRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
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
        HealingCase saved = caseRepository.save(healingCase);
        if ("PATIENT".equalsIgnoreCase(securityContextUtil.getCurrentRole())) {
            String selfRegToken = JwtAuthFilter.extractTokenFromCookie(httpRequest, "accessToken");
            if (selfRegToken != null) {
                String regUuid = jwtService.extractRegUuid(selfRegToken);
                if (regUuid != null) registrationLinkRepository.markUsed(regUuid);
            }
            String patientCaseToken = jwtService.generatePatientCaseToken(
                    saved.getPatientId(), saved.getId(), securityContextUtil.getCurrentClinicId());
            cookieUtil.setPatientCaseSessionCookie(httpResponse, patientCaseToken);
        }
        return toResponse(saved);
    }

    // TODO make this robust like adding actual digital signature in this
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

    @Auditable(action = "GET_SYMPTOMS_FOR_FEEDBACK")
    @Transactional(readOnly = true)
    public PageResponse<SymptomFeedbackDTO> getSymptomsForFeedback(UUID caseId, Pageable pageable) {
        return PageResponse.from(caseRepository.findAllSymptomsByCaseId(caseId, pageable));
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
        List<HealingSession> sessions = healingCase.getSessions();
        int healingCount = sessions.size();
        HealingSession lastSession = sessions.get(sessions.size() - 1);
        LocalDate lastFeedbackDate = lastSession.getQualitativeFeedbacks()
                .stream()
                .map(QualitativeFeedback::getCreatedAt)
                .filter(Objects::nonNull)
                .map(instant -> instant.atZone(ZoneId.systemDefault()).toLocalDate())
                .max(LocalDate::compareTo)
                .orElse(null);
        CaseClosure closure = CaseClosure.builder()
                .healingCase(healingCase)
                .closureDate(LocalDate.now())
                .additionalComments(request.additionalComments())
                .healingCount(healingCount)
                .isHealingContinued(request.isHealingContinued())
                .lastFeedbackDate(lastFeedbackDate)
//                .healerSignature()
                .closedBy(securityContextUtil.getCurrentHealerId())
                .build();
        healingCase.setClosure(closure);
        healingCase.setStatus(CaseStatus.CLOSED);
        caseRepository.save(healingCase);
        if(!request.isHealingContinued()) {
            notificationService.sendFinalFeedbackNotification(lastSession);
        }
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
                c.getMedications(),
                c.getClosure()
        );
    }

    @Auditable(action = "UPDATE_CASE")
    @Transactional
    public CaseResponse updateCaseDetails(CaseUpdateRequest caseUpdateRequest) {
        if(!securityContextUtil.getCurrentRole().equalsIgnoreCase("Patient")
                && caseUpdateRequest.healingDetails().detailedProtocol().isBlank() &&
            (caseUpdateRequest.healingDetails().protocolName().isBlank() ||
                    caseUpdateRequest.healingDetails().bookReference().isBlank())
        ) throw new IllegalArgumentException("No valid protocol found");
        HealingCase entity = caseRepository.findById(caseUpdateRequest.healingCaseId())
                .orElseThrow(() -> new ResourceNotFoundException("Healing Case not found"));
        String role = securityContextUtil.getCurrentRole();
        if ("PATIENT".equalsIgnoreCase(role)) {
            UUID tokenPatientId = securityContextUtil.getCurrentPatientId();
            UUID tokenCaseId = securityContextUtil.getCurrentCaseId();
            if (!entity.getPatientId().equals(tokenPatientId) || !entity.getId().equals(tokenCaseId)) {
                throw new AccessDeniedException("Not authorized to update this case");
            }
        } else {
            if (!entity.getClinicId().equals(securityContextUtil.getCurrentClinicId())) {
                throw new AccessDeniedException("Case does not belong to your clinic");
            }
        }
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
        HealingDetails existingDetails = entity.getHealingDetails();
        if (existingDetails == null) {
            HealingDetails newDetails = healingCaseMapper.toHealingDetails(caseUpdateRequest.healingDetails());
            newDetails.setHealingCase(entity);
            if (newDetails.getTreatmentSteps() != null) {
                for (TreatmentStep step : newDetails.getTreatmentSteps()) {
                    step.setHealingDetails(newDetails);
                }
            }
            entity.setHealingDetails(newDetails);
        } else {
            healingCaseMapper.updateHealingDetails(caseUpdateRequest.healingDetails(), existingDetails);
            if (caseUpdateRequest.healingDetails().treatmentSteps() != null) {
                existingDetails.getTreatmentSteps().clear();
                for (TreatmentStepRequest stepReq : caseUpdateRequest.healingDetails().treatmentSteps()) {
                    TreatmentStep step = healingCaseMapper.toTreatmentStep(stepReq);
                    step.setHealingDetails(existingDetails);
                    existingDetails.getTreatmentSteps().add(step);
                }
            }
        }
        if(!securityContextUtil.getCurrentRole().equalsIgnoreCase("Patient")
                && CaseStatus.DRAFT.equals(entity.getStatus())) {
            entity.setStatus(CaseStatus.ACTIVE);
        }
        return toResponse(entity);
    }

    @Auditable(action = "GET_ALL_PDF")
    @Transactional(readOnly = true)
    public Window<PdfJobDTO> getCompletedPdfJobs(ScrollPosition scrollPosition, int limitSize) {
        UUID healerId = securityContextUtil.getCurrentHealerId();
        List<UUID> healingCaseIds = caseRepository.findAllHealingCaseIdsByHealerId(healerId);
        if (healingCaseIds.isEmpty()) {
            return Window.from(Collections.emptyList(), value -> ScrollPosition.keyset());
        }
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt")
                .and(Sort.by(Sort.Direction.DESC, "id"));
        Window<PdfJobEntity> jobWindow = pdfJobRepository.findByStatusAndHealingCaseIdIn(
                JobStatus.COMPLETED,
                healingCaseIds,
                scrollPosition != null ? scrollPosition : ScrollPosition.keyset(),
                Limit.of(limitSize),
                sort
        );
        return jobWindow.map(job -> {
//            String signedUrl = storageService.generatePresignedDownloadUrl(job.getFileUrl()).toString();
            return new PdfJobDTO(job.getHealingCaseId(), job.getFileUrl());
        });
    }

    @Auditable(action = "DOWNLOAD_PDF")
    @Transactional
    public PreSignedUrlResponse getPreSignedDownloadUrl(String fileUrl) {
        return new PreSignedUrlResponse(
                storageService.generatePresignedDownloadUrl(fileUrl).toString(),
                Instant.now().plus(Duration.ofMinutes(10))
        );
    }

    @Auditable(action = "SEND_MESSAGE")
    @Transactional
    public JobAckResponse postMessageToGeneratePdf(UUID healingCaseId) {
        HealingCase healingCase = findOrThrow(healingCaseId);
        Patient patientEntity = patientRepository.findById(healingCase.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("No Patient found"));

        com.pranicdoc.common.dto.CaseSnapshot caseSnapshot = cphCaseMapper.toCaseSnapshot(healingCase);
        com.pranicdoc.common.dto.Patient patientSnapshot = cphCaseMapper.toPatientSnapshot(patientEntity);

        JsonNode newJsonNode;
        String healingCaseData;
        try {
            ObjectNode root = objectMapper.valueToTree(caseSnapshot);
            root.set("patient", objectMapper.valueToTree(patientSnapshot));
            newJsonNode = root;
            healingCaseData = objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize healing case snapshot", e);
        }
        Optional<PdfJobEntity> existingJobOpt =
                pdfJobRepository.findTopByHealingCaseIdOrderByCreatedAtDesc(healingCaseId);
        if (existingJobOpt.isPresent()) {
            PdfJobEntity existingJob = existingJobOpt.get();
            JobStatus currentStatus = existingJob.getStatus();
            if (JobStatus.STARTED.equals(currentStatus) || JobStatus.PROCESSING.equals(currentStatus)) {
                throw new IllegalStateException("PDF generation is already in process for this case.");
            }
            if (JobStatus.COMPLETED.equals(currentStatus)) {
                try {
                    JsonNode existingJsonNode = objectMapper.readTree(existingJob.getHealingCaseData());
                    if (newJsonNode.equals(existingJsonNode)) {
                        return new JobAckResponse(existingJob.getJobId(), currentStatus);
                    }
                } catch (Exception e) {
                    // If parsing the old data fails for any reason, assume it changed and proceed.
                }
                existingJob.setHealingCaseData(healingCaseData);
                existingJob.setStatus(JobStatus.DRAFT);
                pdfJobRepository.save(existingJob);
                String existingJobId = existingJob.getJobId();
                publishJobMessage(existingJobId, healingCaseId);
                return new JobAckResponse(existingJobId, JobStatus.PROCESSING);
            }
        }
        String jobId = securityContextUtil.getCurrentClinicCode() + "-" + UUID.randomUUID();
        PdfJobEntity entity = PdfJobEntity.create(jobId, healingCaseId, healingCaseData);
        pdfJobRepository.save(entity);
        publishJobMessage(jobId, healingCaseId);
        return new JobAckResponse(jobId, JobStatus.PROCESSING);
    }

    private void publishJobMessage(String jobId, UUID healingCaseId) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        messageService.publish(jobId, healingCaseId);
                    }
                }
        );
    }
}
