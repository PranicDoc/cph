package com.pranicdoc.cph.healing.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pranicdoc.cph.analytics.dto.DashboardPendingReport;
import com.pranicdoc.cph.analytics.dto.DashboardTopProtocol;
import com.pranicdoc.cph.analytics.dto.DashboardTopSymptom;
import com.pranicdoc.cph.analytics.dto.DashboardUpcomingSession;
import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.auth.service.HealerService;
import com.pranicdoc.cph.common.enums.LinkStatus;
import com.pranicdoc.cph.notification.repository.FeedbackLinkRepository;
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
import com.pranicdoc.cph.payments.service.CreditGateService;
import com.pranicdoc.cph.healing.service.MessageService;
import com.pranicdoc.cph.healing.service.NotificationService;
import com.pranicdoc.cph.patient.repository.RegistrationLinkRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.AccessDeniedException;
import com.pranicdoc.cph.healing.dto.*;
import com.pranicdoc.cph.healing.mapper.CphCaseMapper;
import com.pranicdoc.cph.healing.mapper.DeclarationMapper;
import com.pranicdoc.cph.healing.mapper.HealingCaseMapper;
import com.pranicdoc.cph.healing.model.*;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.healing.repository.HealingDetailsRepository;
import com.pranicdoc.cph.healing.repository.PdfJobRepository;
import com.pranicdoc.cph.patient.model.Patient;
import com.pranicdoc.cph.patient.model.PatientMedicalHistory;
import com.pranicdoc.cph.patient.repository.PatientRepository;
import com.pranicdoc.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
    private final CreditGateService creditGateService;
    private final RegistrationLinkRepository registrationLinkRepository;
    private final HealingDetailsRepository healingDetailsRepository;
    private final JwtService jwtService;
    private final CookieUtil cookieUtil;
    private final FeedbackLinkRepository feedbackLinkRepository;
    // D-18: self-injection via ObjectProvider so postMessageToGeneratePdf can
    // invoke validateCaseExists/buildAndEnqueuePdfJob THROUGH the Spring
    // proxy — a plain `this.foo()` call would not cross the @Transactional
    // AOP proxy (self-invocation), silently running without a transaction.
    // ObjectProvider defers bean lookup past construction, avoiding a
    // circular-dependency error from injecting CaseService into itself.
    private final HealerCacheService healerService;
    private final ObjectProvider<CaseService> self;

    /**
     * Create case → auto-snapshot PatientMedicalHistory → CaseMedicalSnapshot.
     */
    @Auditable(action = "CREATE_CASE")
    @Transactional
    public CaseResponse create(CaseCreateRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", request.patientId()));
        boolean exists = caseRepository.existsByPatientIdAndHealerIdAndStatus(
                request.patientId(),
                securityContextUtil.getCurrentHealerId(),
                CaseStatus.DRAFT
        );
        if (exists) {
            throw new IllegalStateException("Draft already exists for this patient");
        }
        Declaration declaration = declarationMapper.toEntity(request.declaration());
        String healerName = healerService.getName(securityContextUtil.getCurrentHealerId());
        declaration.setHealerSignature(healerName);
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
            // D-26-04 (PATIENT_CASE half, 26-07): the just-saved HealingCase already carries
            // the real healerId — no extra lookup needed. 26-11 flips the JwtService claim
            // itself to a real healerId claim; this call site already passes that value.
            String patientCaseToken = jwtService.generatePatientCaseToken(
                    saved.getPatientId(), saved.getId(), saved.getHealerId());
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
                .morningDose(Boolean.TRUE.equals(request.morningDose()))
                .afternoonDose(Boolean.TRUE.equals(request.afternoonDose()))
                .nightDose(Boolean.TRUE.equals(request.nightDose()))
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
        if (sessions.isEmpty()) {
            throw new IllegalArgumentException("Cannot close a case with no recorded sessions");
        }
        UUID currentHealerId = securityContextUtil.getCurrentHealerId();
        String healerName = healerService.getName(currentHealerId);
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
                .healerSignature(healerName)
                .closedBy(currentHealerId)
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

    @Transactional(readOnly = true)
    public CaseSummaryResponse getCaseSummary(UUID healingCaseId) {
        HealingCase healingCase = findOrThrow(healingCaseId);
        Patient patient = patientRepository.findById(healingCase.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", healingCase.getPatientId()));
        return new CaseSummaryResponse(
                patient.getFirstName(), patient.getLastName(),
                patient.getDob(), patient.getGender(), patient.getMaritalStatus(),
                patient.getContact(), patient.getOccupation(),
                healingCase.getId(), healingCase.getMedicalCondition(),
                parseLabFindingKeys(healingCase.getLabFindings()),
                healingCase.getComplementaryTreatments(), healingCase.getComplementaryDetails(),
                healingCase.getInfectiousIllness(), healingCase.getInfectiousDetails(),
                healingCase.getBasicBookPg(), healingCase.getAdvancedBookPg(),
                healingCase.getPsychotherapyPg(),
                healingCase.getCreatedAt(),
                healingCase.getMedicalSnapshot(),
                healingCase.getSymptoms(),
                healingCase.getMedications(),
                healingCase.getSessions().stream()
                        .sorted(Comparator.comparingInt(HealingSession::getSessionNumber))
                        .toList(),
                healingCase.getClosure()
        );
    }

    private List<String> parseLabFindingKeys(String labFindings) {
        if (labFindings == null || labFindings.isBlank()) return List.of();
        try {
            return objectMapper.readValue(labFindings, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private CaseResponse toResponse(HealingCase c) {
        return new CaseResponse(
                c.getId(), c.getPatientId(), c.getHealerId(),
                c.getMedicalCondition(), c.getHealingDetails(), parseLabFindingKeys(c.getLabFindings()),
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
    public CaseResponse updateCaseDetails(CaseUpdateRequest caseUpdateRequest, List<MultipartFile> labFindings) {
        if(!securityContextUtil.getCurrentRole().equalsIgnoreCase("Patient")
                && caseUpdateRequest.healingDetails().detailedProtocol().isBlank() &&
                caseUpdateRequest.healingDetails().protocolName().isBlank()
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
            if (!entity.getHealerId().equals(securityContextUtil.getCurrentHealerId())) {
                throw new AccessDeniedException("Case does not belong to you");
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
        if (labFindings != null && !labFindings.isEmpty()) {
            List<String> keys = new ArrayList<>();
            for (MultipartFile file : labFindings) {
                String original = file.getOriginalFilename();
                String ext = (original != null && original.contains("."))
                        ? original.substring(original.lastIndexOf('.')) : "";
                String key = "lab-findings/" + entity.getId() + "/" + UUID.randomUUID() + ext;
                try {
                    storageService.upload(key, file.getBytes(), file.getContentType());
                } catch (Exception e) {
                    throw new RuntimeException("Failed to upload lab finding: " + original, e);
                }
                keys.add(key);
            }
            try {
                entity.setLabFindings(objectMapper.writeValueAsString(keys));
            } catch (Exception e) {
                throw new RuntimeException("Failed to persist lab findings", e);
            }
        }
        if(securityContextUtil.getCurrentRole().equalsIgnoreCase("Healer")
            && CaseStatus.DRAFT.equals(entity.getStatus())
            && entity.getHealerId() == null
            && securityContextUtil.getCurrentHealerId() != null
        ) {
            entity.setHealerId(securityContextUtil.getCurrentHealerId());
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

    /**
     * D-18 (T-23-04-CG): the credit-gate HTTP call (checkAndDeduct) must run
     * with NO active DB transaction — a slow/hung creditService must not pin
     * a Hikari connection. Propagation.NOT_SUPPORTED overrides the class-level
     * @Transactional so this entry method itself opens no transaction; the
     * existence check and the DB work below each run in their OWN short
     * transaction, invoked through the self-proxy (see `self` field javadoc).
     * Ordering (validate → deduct → DB work), the unconditional deduct, the
     * existing-job branch, and the afterCommit publish are all unchanged.
     */
    @Auditable(action = "SEND_MESSAGE")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public JobAckResponse postMessageToGeneratePdf(UUID healingCaseId) {
        self.getObject().validateCaseExists(healingCaseId);
        UUID caseHealerId = caseRepository.findById(healingCaseId)
                .map(HealingCase::getHealerId)
                .orElseThrow(() -> new ResourceNotFoundException("HealingCase", healingCaseId));
        creditGateService.checkAndDeduct(securityContextUtil.getCurrentHealerId(),
                caseHealerId, healingCaseId);
        return self.getObject().buildAndEnqueuePdfJob(healingCaseId);
    }

    /**
     * D-18 step 1: brief existence check in its own read-only transaction —
     * opens and closes a Hikari connection before the credit-gate HTTP call
     * below, preserving the pre-refactor "case not found" -> throw-before-
     * deduct behavior.
     */
    @Transactional(readOnly = true)
    public void validateCaseExists(UUID healingCaseId) {
        if (!caseRepository.existsById(healingCaseId)) {
            throw new ResourceNotFoundException("HealingCase", healingCaseId);
        }
    }

    /**
     * D-18 step 2 / D-25 /INFRA-01: snapshot build + existing-job branch +
     * save + afterCommit publish, all inside one transaction — runs AFTER the
     * credit-gate HTTP call returns, so no connection is held during that
     * round-trip. Uses the bounded @EntityGraph load (findWithSessionsById)
     * instead of the plain findOrThrow + full lazy traversal that caused the
     * PDF-path N+1.
     */
    @Transactional
    public JobAckResponse buildAndEnqueuePdfJob(UUID healingCaseId) {
        HealingCase healingCase = caseRepository.findWithSessionsById(healingCaseId)
                .orElseThrow(() -> new ResourceNotFoundException("HealingCase", healingCaseId));
        Patient patientEntity = patientRepository.findById(healingCase.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("No Patient found"));

        com.pranicdoc.common.dto.CaseSnapshot caseSnapshot = cphCaseMapper.toCaseSnapshot(healingCase);
        com.pranicdoc.common.dto.Patient patientSnapshot = cphCaseMapper.toPatientSnapshot(patientEntity);

        JsonNode newJsonNode;
        String healingCaseData;
        try {
            ObjectNode root = objectMapper.valueToTree(caseSnapshot);
            root.set("patient", objectMapper.valueToTree(patientSnapshot));
            root.put("healerSign", healingCase.getDeclaration().getHealerSignature());
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
        // D-26-03: healer-derived prefix ('H' + first 8 hex of healerId, uppercased,
        // hyphens stripped); uniqueness comes from the trailing UUID, not the prefix.
        // FLAG(Phase 28/pdfWorker): confirm jobId is treated as opaque or update pdfWorker's parser.
        String jobId = "H" + securityContextUtil.getCurrentHealerId().toString()
                .replace("-", "").substring(0, 8).toUpperCase() + "-" + UUID.randomUUID();
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

    @Transactional(readOnly = true)
    public List<DashboardTopSymptom> getTopSymptoms(UUID healerId, LocalDate from, LocalDate to) {
        return caseRepository.getTopSymptoms(healerId, from, to, PageRequest.of(0, 10));
    }

    @Transactional(readOnly = true)
    public List<DashboardTopProtocol> getTopProtocols(UUID healerId, LocalDate from, LocalDate to) {
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return healingDetailsRepository.getTopProtocols(healerId, fromInstant, toInstant, PageRequest.of(0, 10));
    }

    @Transactional(readOnly = true)
    public List<DashboardUpcomingSession> getActiveCasesForQueue(UUID healerId) {
        return caseRepository.getActiveCasesForQueue(healerId).stream()
                .map(p -> new DashboardUpcomingSession(
                        p.getId(),
                        p.getPatientFirstName(),
                        p.getPatientLastName(),
                        p.getLastSessionDate(),
                        null,
                        "ACTIVE_CASE"
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DashboardPendingReport> getPendingReports(UUID healerId) {
        return caseRepository.getPendingReportsForQueue(healerId).stream()
                .map(p -> new DashboardPendingReport(
                        p.getCaseId(),
                        p.getPatientFirstName(),
                        p.getPatientLastName(),
                        p.getClosureDate()
                ))
                .toList();
    }

    @Auditable(action = "RESEND_FEEDBACK")
    @Transactional
    public void resendFeedback(UUID caseId, boolean isFinal) {
        HealingCase healingCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("HealingCase", caseId));
        if (!healingCase.getHealerId().equals(securityContextUtil.getCurrentHealerId())) {
            throw new org.springframework.security.access.AccessDeniedException("Not authorized");
        }
        feedbackLinkRepository.markUsedBySessionId(caseId, LinkStatus.USED, LinkStatus.ACTIVE);
        HealingSession latestSession = healingCase.getSessions().stream()
                .filter(s -> s.getSessionDate() != null)
                .max(java.util.Comparator.comparing(HealingSession::getSessionDate))
                .orElseThrow(() -> new ResourceNotFoundException("HealingSession", caseId));
        notificationService.sendFeedbackNotification(latestSession, isFinal);
    }
}