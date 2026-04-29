package com.pranicdoc.cph.healing.controller;

import com.pranicdoc.cph.healing.dto.QualitativeFeedbackRequest;
import com.pranicdoc.cph.healing.dto.SymptomFeedbackRequest;
import com.pranicdoc.cph.healing.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping("/symptoms")
    public ResponseEntity<Void> saveSymptomFeedback(
            @PathVariable UUID sessionId,
            @Valid @RequestBody List<SymptomFeedbackRequest> requests) {
        feedbackService.saveSymptomFeedback(sessionId, requests);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/qualitative")
    public ResponseEntity<Void> saveQualitativeFeedback(
            @PathVariable UUID sessionId,
            @Valid @RequestBody QualitativeFeedbackRequest request) {
        feedbackService.saveQualitativeFeedback(sessionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
