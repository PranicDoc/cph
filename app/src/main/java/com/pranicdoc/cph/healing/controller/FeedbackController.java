package com.pranicdoc.cph.healing.controller;

import com.pranicdoc.cph.healing.dto.QualitativeFeedbackRequest;
import com.pranicdoc.cph.healing.dto.SymptomFeedbackRequest;
import com.pranicdoc.cph.healing.service.impl.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Live patient feedback submission path. Both POSTs below are reached only via a
 * PATIENT-role token (cph/ui Feedback.tsx), where the {@code @rbacAuthz} capability gate
 * returns {@code false} for every real caller ({@code getCurrentRole() == "PATIENT"}, and
 * the owning healer holds no grants) — a gate here would 403 every patient feedback
 * submission (D-19). {@code Resource.FEEDBACK} is therefore enforced solely by
 * {@code CaseController.resendFeedback} (healer-only), and {@code FEEDBACK} allow-lists
 * {@code CREATE} only. Openness is asserted by absence plus a named allow-list test (D-07),
 * not by a marker annotation, matching the Phase 40 source-sweep style.
 */
@RestController
@RequestMapping("/api/v1/sessions/{caseId}/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    // Deliberately ungated: reached only via a PATIENT-role token — the @rbacAuthz gate
    // returns false for every real caller, so a gate would 403 every patient feedback
    // submission (D-19).
    @PostMapping("/symptoms")
    public ResponseEntity<Void> saveSymptomFeedback(
            @PathVariable UUID caseId,
            @Valid @RequestBody List<SymptomFeedbackRequest> requests) {
        feedbackService.saveSymptomFeedback(caseId, requests);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // Deliberately ungated: reached only via a PATIENT-role token — the @rbacAuthz gate
    // returns false for every real caller, so a gate would 403 every patient feedback
    // submission (D-19).
    @PostMapping("/qualitative")
    public ResponseEntity<Void> saveQualitativeFeedback(
            @PathVariable UUID caseId,
            @Valid @RequestBody QualitativeFeedbackRequest request) {
        feedbackService.saveQualitativeFeedback(caseId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
