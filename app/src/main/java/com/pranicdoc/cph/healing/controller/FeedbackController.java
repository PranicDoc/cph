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

@RestController
@RequestMapping("/api/v1/sessions/{caseId}/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping("/symptoms")
    public ResponseEntity<Void> saveSymptomFeedback(
            @PathVariable UUID caseId,
            @Valid @RequestBody List<SymptomFeedbackRequest> requests) {
        feedbackService.saveSymptomFeedback(caseId, requests);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/qualitative")
    public ResponseEntity<Void> saveQualitativeFeedback(
            @PathVariable UUID caseId,
            @Valid @RequestBody QualitativeFeedbackRequest request) {
        feedbackService.saveQualitativeFeedback(caseId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
