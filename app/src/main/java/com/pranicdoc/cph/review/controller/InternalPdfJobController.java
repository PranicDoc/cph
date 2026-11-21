package com.pranicdoc.cph.review.controller;

import com.pranicdoc.cph.review.event.PdfReadyEvent;
import com.pranicdoc.cph.review.event.ReviewEventPublisher;
import com.pranicdoc.cph.review.service.PdfJobCaseResolver;
import com.pranicdoc.cph.review.service.ReviewAutoSubmitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Machine-to-machine webhook: pdfWorker tells cph a PDF job finished. No JWT; the shared
 * {@code X-Internal-Token} is checked here, and an unset token rejects every call (fail closed).
 * It also pushes a "PDF ready" event to the case's healer. Whatever happens after the token check
 * answers 204, so pdfWorker never retries a business skip.
 */
@RestController
@RequestMapping("/internal/pdf-jobs")
@Slf4j
public class InternalPdfJobController {

    private final ReviewAutoSubmitService autoSubmit;
    private final PdfJobCaseResolver jobCases;
    private final ReviewEventPublisher events;
    private final String expectedToken;

    public InternalPdfJobController(ReviewAutoSubmitService autoSubmit, PdfJobCaseResolver jobCases,
                                    ReviewEventPublisher events,
                                    @Value("${cph.internal.token:}") String expectedToken) {
        this.autoSubmit = autoSubmit;
        this.jobCases = jobCases;
        this.events = events;
        this.expectedToken = expectedToken;
    }

    @PostMapping("/{jobId}/completed")
    public ResponseEntity<Void> completed(@PathVariable String jobId,
                                          @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        if (!tokenMatches(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        publishPdfReady(jobId);
        try {
            autoSubmit.onPdfCompleted(jobId);
        } catch (Exception e) {
            log.warn("Auto-submit failed for PDF job {}", jobId, e);
        }
        return ResponseEntity.noContent().build();
    }

    /** Tells the case's healer their PDF is ready, whatever their auto-submit setting. Never throws. */
    private void publishPdfReady(String jobId) {
        try {
            jobCases.resolve(jobId).ifPresent(jc ->
                    events.publishPdfReady(new PdfReadyEvent(jc.healerId(), jc.caseId(), jobId)));
        } catch (Exception e) {
            log.warn("PDF-ready event not published for job {}", jobId, e);
        }
    }

    private boolean tokenMatches(String token) {
        if (expectedToken == null || expectedToken.isBlank() || token == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }
}
