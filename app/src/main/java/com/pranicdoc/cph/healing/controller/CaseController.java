package com.pranicdoc.cph.healing.controller;

import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.common.dto.PageResponse;
import com.pranicdoc.cph.healing.dto.*;
import com.pranicdoc.cph.healing.service.impl.CaseService;
import com.pranicdoc.cph.healing.service.impl.HealerProtocolUnderstandingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cases")
@RequiredArgsConstructor
public class CaseController {

    private final CaseService caseService;
    private final HealerProtocolUnderstandingService healerProtocolUnderstandingService;

    /**
     * Deliberately UNGATED (SC4 / D-19). Reached by a PATIENT-role token — the dynamic
     * permission check returns false for every real caller; gating would 403 the patient
     * self-registration / health-form / feedback flow. Also healer-reachable, so leaving it
     * open is a deliberate "both callers" decision, not an oversight. Named by plan 42-11's
     * open-list test.
     */
    @PostMapping
    public ResponseEntity<CaseResponse> create(@Valid @RequestBody CaseCreateRequest request,
                                               HttpServletRequest httpRequest,
                                               HttpServletResponse httpResponse) {
        return ResponseEntity.status(HttpStatus.CREATED).body(caseService.create(request, httpRequest, httpResponse));
    }

    /**
     * Gated CASE:READ on the healer path per the 2026-09-02 CR-01 ruling (D-19a) — every
     * sibling read of a clinical case record on this controller carries the same gate. The
     * patient caller (the health-form load) is served by the sibling
     * {@code GET /api/v1/cases/self-service} mapping ({@link #getByIdSelfService}), which is
     * caller-class-restricted to ROLE_PATIENT in SecurityConfig, not by an annotation here.
     * The {@code healer_id} row filter remains the record-level layer beneath this
     * capability gate.
     */
    @PreAuthorize("@rbacAuthz.hasPermission('CASE','READ')")
    @GetMapping("/{id}")
    public ResponseEntity<CaseResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(caseService.getById(id));
    }

    /**
     * Reached only by a ROLE_PATIENT principal (a SELF_REG or PATIENT_CASE token) — the
     * patient health-form load. Deliberately carries no {@code @PreAuthorize}: a SELF_REG
     * token's {@code TenantDetails.healerId} is the OWNING healer's id, so the dynamic
     * capability check would resolve the patient's request against the wrong principal's
     * grants. The caller-class restriction lives in SecurityConfig as a path-based
     * ROLE_PATIENT request-matcher rule on {@code /api/v1/cases/self-service}, not here.
     * Uses a query param, not a {@code /self-service/{id}} path template — a template at
     * that depth would collide with the sibling {@code /{id}/summary} pattern and produce an
     * ambiguous-handler 500. Named by plan 42-14's open-list test. Beyond that caller-class
     * restriction, the record-level control is {@link
     * com.pranicdoc.cph.healing.service.impl.CaseService#getByIdForSelfService}'s
     * patientId/caseId claim comparison against the loaded record — a patient token bound
     * to another case gets 403 (42-15 / CR-01).
     */
    @GetMapping("/self-service")
    public ResponseEntity<CaseResponse> getByIdSelfService(@RequestParam UUID caseId) {
        return ResponseEntity.ok(caseService.getByIdForSelfService(caseId));
    }

    @PreAuthorize("@rbacAuthz.hasPermission('CASE','READ')")
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<PageResponse<CaseResponse>> getByPatientId(
            @PathVariable UUID patientId,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(caseService.getByPatientId(patientId, pageable));
    }

    /** Spends one credit to unlock a new case; 402 when the healer has no credits left. */
    @PreAuthorize("@rbacAuthz.hasPermission('CASE','UPDATE')")
    @PostMapping("/{id}/unlock")
    public ResponseEntity<CaseResponse> unlock(@PathVariable UUID id) {
        return ResponseEntity.ok(caseService.unlock(id));
    }

    @PreAuthorize("@rbacAuthz.hasPermission('CASE','UPDATE')")
    @PostMapping("/{id}/symptoms")
    public ResponseEntity<Void> addSymptom(@PathVariable UUID id,
                                            @Valid @RequestBody SymptomRequest request) {
        caseService.addSymptom(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PreAuthorize("@rbacAuthz.hasPermission('CASE','UPDATE')")
    @PatchMapping("/{id}/symptoms/{symptomId}/resolve")
    public ResponseEntity<Void> resolveSymptom(@PathVariable UUID id,
                                                @PathVariable UUID symptomId) {
        caseService.resolveSymptom(id, symptomId);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("@rbacAuthz.hasPermission('CASE','UPDATE')")
    @PostMapping("/{id}/medications")
    public ResponseEntity<Void> addMedication(@PathVariable UUID id,
                                               @Valid @RequestBody MedicationRequest request) {
        caseService.addMedication(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PreAuthorize("@rbacAuthz.hasPermission('CASE','UPDATE')")
    @PostMapping("/{id}/declaration")
    public ResponseEntity<Void> signDeclaration(@PathVariable UUID id,
                                                 @Valid @RequestBody DeclarationRequest request) {
        caseService.signDeclaration(id, request);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("@rbacAuthz.hasPermission('CASE','UPDATE')")
    @PostMapping("/{id}/close")
    public ResponseEntity<Void> closeCase(@PathVariable UUID id,
                                           @Valid @RequestBody CaseClosureRequest request) {
        caseService.closeCase(id, request);
        return ResponseEntity.ok().build();
    }

    // D-06: CASE:*, not SESSION_PROTOCOL:* — this is the healer's protocol understanding for
    // a CASE; the per-session protocol resource is scoped to ProtocolController's submission.
    @PreAuthorize("@rbacAuthz.hasPermission('CASE','UPDATE')")
    @PostMapping("/{id}/protocol-understanding")
    public ResponseEntity<Void> saveProtocolUnderstanding(
            @PathVariable UUID id,
            @Valid @RequestBody HealerProtocolUnderstandingRequest request) {
        healerProtocolUnderstandingService.saveFromHealerSelection(id, request);
        return ResponseEntity.ok().build();
    }

    // D-06: CASE:*, not SESSION_PROTOCOL:* — reads the healer's protocol understanding for a
    // CASE; the per-session protocol resource is scoped to ProtocolController's submission.
    @PreAuthorize("@rbacAuthz.hasPermission('CASE','READ')")
    @GetMapping("/{id}/protocol-understanding")
    public ResponseEntity<HealerProtocolUnderstandingRequest> getProtocolUnderstanding(
            @PathVariable UUID id) {
        return ResponseEntity.ok(healerProtocolUnderstandingService.getExistingSelection(id));
    }

    // Same CASE:READ gate as the saved selection above: master-data protocol colors for the
    // case's scanned chakras, which the closure form pre-fills where nothing is saved yet.
    @PreAuthorize("@rbacAuthz.hasPermission('CASE','READ')")
    @GetMapping("/{id}/protocol-understanding/suggestions")
    public ResponseEntity<HealerProtocolUnderstandingRequest> getProtocolUnderstandingSuggestions(
            @PathVariable UUID id) {
        return ResponseEntity.ok(healerProtocolUnderstandingService.getProtocolSuggestions(id));
    }

    @PreAuthorize("@rbacAuthz.hasPermission('CASE','DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        caseService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Gated CASE:UPDATE on the healer path per the 2026-09-02 CR-01 ruling (D-19a) — every
     * sibling write of a clinical case record on this controller carries the same gate. The
     * patient caller (the health-form submit) is served by the sibling
     * {@code PATCH /api/v1/cases/self-service} mapping ({@link #updateCaseDetailsSelfService}),
     * which is caller-class-restricted to ROLE_PATIENT in SecurityConfig, not by an
     * annotation here. This handler is multipart and carries labFindings files, but those
     * files attach to the CASE, not a PDF job — do not "fix" this to PDF:*. The
     * {@code healer_id} row filter remains the record-level layer beneath this capability
     * gate.
     */
    @PreAuthorize("@rbacAuthz.hasPermission('CASE','UPDATE')")
    @PatchMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CaseResponse> updateCaseDetails(
            @RequestPart("data") CaseUpdateRequest caseUpdateRequest,
            @RequestPart(value = "labFindings", required = false) List<MultipartFile> labFindings) {
        return ResponseEntity.ok(caseService.updateCaseDetails(caseUpdateRequest, labFindings));
    }

    /**
     * Reached only by a ROLE_PATIENT principal (a SELF_REG or PATIENT_CASE token) — the
     * patient health-form submit. Deliberately carries no {@code @PreAuthorize}: a SELF_REG
     * token's {@code TenantDetails.healerId} is the OWNING healer's id, so the dynamic
     * capability check would resolve the patient's request against the wrong principal's
     * grants. The caller-class restriction lives in SecurityConfig as a path-based
     * ROLE_PATIENT request-matcher rule on {@code /api/v1/cases/self-service}, not here. The
     * case is resolved from {@code CaseUpdateRequest.healingCaseId} in the body, beneath the
     * untouched {@code healer_id} row filter — the labFindings files attach to the CASE, not
     * a PDF job. Named by plan 42-14's open-list test.
     */
    @PatchMapping(path = "/self-service", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CaseResponse> updateCaseDetailsSelfService(
            @RequestPart("data") CaseUpdateRequest caseUpdateRequest,
            @RequestPart(value = "labFindings", required = false) List<MultipartFile> labFindings) {
        return ResponseEntity.ok(caseService.updateCaseDetails(caseUpdateRequest, labFindings));
    }

    @PreAuthorize("@rbacAuthz.hasPermission('PDF','CREATE')")
    @PostMapping("/generate-pdf")
    public ResponseEntity<ApiResponse<JobAckResponse>> generatePdf(@RequestParam UUID healingCaseId) {
        JobAckResponse ackResponse = caseService.postMessageToGeneratePdf(healingCaseId);
        return ResponseEntity.accepted().body(
                new ApiResponse<>(true,
                        "Accepted the request, please wait while processing",
                        ackResponse));
    }

    @PreAuthorize("@rbacAuthz.hasPermission('PDF','READ')")
    @GetMapping("/pdfs")
    public ResponseEntity<ApiResponse<Window<PdfJobDTO>>> getAllPdfs(
            @RequestParam(required = false) ScrollPosition scrollPosition,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok().body(
            new ApiResponse<>(true, "Successfully retrieved pdfs",
                caseService.getCompletedPdfJobs(scrollPosition, limit)
            )
        );
    }

    // PDF:EXPORT — mints a presigned URL that removes the data from the application's control
    // boundary; a distinct, separately grantable verb from PDF:READ (listing != downloading).
    @PreAuthorize("@rbacAuthz.hasPermission('PDF','EXPORT')")
    @GetMapping("/download")
    public ResponseEntity<ApiResponse<PreSignedUrlResponse>> getDownloadLink(@RequestParam String fileUrl) {
        return ResponseEntity.ok().body(
                new ApiResponse<>(true, "Successfully retrieved download link",
                    caseService.getPreSignedDownloadUrl(fileUrl)
                )
        );
    }

    // CASE:READ — summarises the case, not the PDF jobs.
    @PreAuthorize("@rbacAuthz.hasPermission('CASE','READ')")
    @GetMapping("/{healingCaseId}/summary")
    public ResponseEntity<ApiResponse<CaseSummaryResponse>> getCaseSummary(
            @PathVariable UUID healingCaseId) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Case summary retrieved",
                        caseService.getCaseSummary(healingCaseId))
        );
    }

    /**
     * Gated CASE:READ on the healer path per the 2026-09-02 CR-01 ruling (D-19a). Case
     * symptoms are case data, so this takes the sibling CASE:READ verb already used by
     * {@link #getCaseSummary} and {@link #getProtocolUnderstanding} — deliberately NOT
     * FEEDBACK:READ: D-19 dropped that verb, {@code Resource.FEEDBACK} is {@code {CREATE}}
     * only, and the contract test's negative control pins
     * {@code Resource.FEEDBACK.supports(READ) == false}. The patient caller (the feedback
     * form build) is served by the sibling {@code GET /api/v1/cases/self-service/symptoms}
     * mapping ({@link #getSymptomsForFeedbackSelfService}), which is caller-class-restricted
     * to ROLE_PATIENT in SecurityConfig, not by an annotation here. The record-level layer
     * for this method is {@link
     * com.pranicdoc.cph.healing.service.impl.CaseService#getSymptomsForFeedback}'s {@code
     * findOrThrow} plus explicit healer/case ownership comparison, NOT a {@code healer_id}
     * row filter — the backing symptom query cannot carry the Hibernate tenant filter
     * ({@code CaseSymptom} extends {@code BaseEntity}, not {@code TenantEntity}) (42-15 /
     * CR-02).
     */
    @PreAuthorize("@rbacAuthz.hasPermission('CASE','READ')")
    @GetMapping("/symptoms")
    public ResponseEntity<PageResponse<SymptomFeedbackDTO>> getSymptomsForFeedback(
            @RequestParam UUID caseId,
            Pageable pageable) {

        return ResponseEntity.ok(
                caseService.getSymptomsForFeedback(caseId, pageable)
        );
    }

    /**
     * Reached only by a ROLE_PATIENT principal (a SELF_REG or PATIENT_CASE token) — the
     * patient feedback form build. Deliberately carries no {@code @PreAuthorize}: a SELF_REG
     * token's {@code TenantDetails.healerId} is the OWNING healer's id, so the dynamic
     * capability check would resolve the patient's request against the wrong principal's
     * grants. The caller-class restriction lives in SecurityConfig as a path-based
     * ROLE_PATIENT request-matcher rule on {@code /api/v1/cases/self-service}, not here.
     * Named by plan 42-14's open-list test. The record-level control is {@link
     * com.pranicdoc.cph.healing.service.impl.CaseService#getSymptomsForFeedback}'s caseId
     * claim comparison — the feedback-session token minted by {@code
     * AuthService.initSessionForFeedback} is case-bound to a single {@code
     * FeedbackLink.caseId} (42-15 / CR-02, Task 1), so a patient token for another case
     * gets 403.
     */
    @GetMapping("/self-service/symptoms")
    public ResponseEntity<PageResponse<SymptomFeedbackDTO>> getSymptomsForFeedbackSelfService(
            @RequestParam UUID caseId,
            Pageable pageable) {

        return ResponseEntity.ok(
                caseService.getSymptomsForFeedback(caseId, pageable)
        );
    }

    // FEEDBACK:CREATE (D-05) — resendFeedback is the SOLE enforcement site for the whole
    // FEEDBACK resource; it re-triggers a patient feedback request and is healer-only.
    @PreAuthorize("@rbacAuthz.hasPermission('FEEDBACK','CREATE')")
    @PostMapping("/{id}/resend-feedback")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void resendFeedback(@PathVariable UUID id, @RequestParam boolean isFinal) {
        caseService.resendFeedback(id, isFinal);
    }
}
