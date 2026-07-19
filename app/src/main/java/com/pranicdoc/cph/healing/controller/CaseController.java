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

    @PostMapping
    public ResponseEntity<CaseResponse> create(@Valid @RequestBody CaseCreateRequest request,
                                               HttpServletRequest httpRequest,
                                               HttpServletResponse httpResponse) {
        return ResponseEntity.status(HttpStatus.CREATED).body(caseService.create(request, httpRequest, httpResponse));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CaseResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(caseService.getById(id));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<PageResponse<CaseResponse>> getByPatientId(
            @PathVariable UUID patientId,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(caseService.getByPatientId(patientId, pageable));
    }

    @PostMapping("/{id}/symptoms")
    public ResponseEntity<Void> addSymptom(@PathVariable UUID id,
                                            @Valid @RequestBody SymptomRequest request) {
        caseService.addSymptom(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PatchMapping("/{id}/symptoms/{symptomId}/resolve")
    public ResponseEntity<Void> resolveSymptom(@PathVariable UUID id,
                                                @PathVariable UUID symptomId) {
        caseService.resolveSymptom(id, symptomId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/medications")
    public ResponseEntity<Void> addMedication(@PathVariable UUID id,
                                               @Valid @RequestBody MedicationRequest request) {
        caseService.addMedication(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/{id}/declaration")
    public ResponseEntity<Void> signDeclaration(@PathVariable UUID id,
                                                 @Valid @RequestBody DeclarationRequest request) {
        caseService.signDeclaration(id, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<Void> closeCase(@PathVariable UUID id,
                                           @Valid @RequestBody CaseClosureRequest request) {
        caseService.closeCase(id, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/protocol-understanding")
    public ResponseEntity<Void> saveProtocolUnderstanding(
            @PathVariable UUID id,
            @Valid @RequestBody HealerProtocolUnderstandingRequest request) {
        healerProtocolUnderstandingService.saveFromHealerSelection(id, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/protocol-understanding")
    public ResponseEntity<HealerProtocolUnderstandingRequest> getProtocolUnderstanding(
            @PathVariable UUID id) {
        return ResponseEntity.ok(healerProtocolUnderstandingService.getExistingSelection(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        caseService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CaseResponse> updateCaseDetails(
            @RequestPart("data") CaseUpdateRequest caseUpdateRequest,
            @RequestPart(value = "labFindings", required = false) List<MultipartFile> labFindings) {
        return ResponseEntity.ok(caseService.updateCaseDetails(caseUpdateRequest, labFindings));
    }

    @PostMapping("/generate-pdf")
    public ResponseEntity<ApiResponse<JobAckResponse>> generatePdf(@RequestParam UUID healingCaseId) {
        JobAckResponse ackResponse = caseService.postMessageToGeneratePdf(healingCaseId);
        return ResponseEntity.accepted().body(
                new ApiResponse<>(true,
                        "Accepted the request, please wait while processing",
                        ackResponse));
    }

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

    @GetMapping("/download")
    public ResponseEntity<ApiResponse<PreSignedUrlResponse>> getDownloadLink(@RequestParam String fileUrl) {
        return ResponseEntity.ok().body(
                new ApiResponse<>(true, "Successfully retrieved download link",
                    caseService.getPreSignedDownloadUrl(fileUrl)
                )
        );
    }

    @GetMapping("/{healingCaseId}/summary")
    public ResponseEntity<ApiResponse<CaseSummaryResponse>> getCaseSummary(
            @PathVariable UUID healingCaseId) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Case summary retrieved",
                        caseService.getCaseSummary(healingCaseId))
        );
    }

    @GetMapping("/symptoms")
    public ResponseEntity<PageResponse<SymptomFeedbackDTO>> getSymptomsForFeedback(
            @RequestParam UUID caseId,
            Pageable pageable) {

        return ResponseEntity.ok(
                caseService.getSymptomsForFeedback(caseId, pageable)
        );
    }

    @PostMapping("/{id}/resend-feedback")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void resendFeedback(@PathVariable UUID id, @RequestParam boolean isFinal) {
        caseService.resendFeedback(id, isFinal);
    }
}
