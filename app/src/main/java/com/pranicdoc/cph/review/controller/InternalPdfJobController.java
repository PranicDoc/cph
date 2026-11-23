package com.pranicdoc.cph.review.controller;

import com.pranicdoc.cph.common.feature.FeatureFlags;
import com.pranicdoc.cph.review.event.PdfReadyEvent;
import com.pranicdoc.cph.review.event.ReviewEventPublisher;
import com.pranicdoc.cph.review.service.PdfJobCaseResolver;
import com.pranicdoc.cph.review.service.ReviewAutoSubmitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Machine-to-machine webhook: pdfWorker tells cph a PDF job finished. No JWT; authentication is done
 * before this controller by InternalApiKeyFilter (InternalApiSecurityConfig's filter chain, header
 * {@code X-Internal-Key}). It pushes a "PDF ready" event to the case's healer, then runs auto-submit.
 * Whatever happens once authenticated answers 204, so pdfWorker never retries a business skip.
 */
@RestController
@RequestMapping("/api/v1/internal/pdf-jobs")
@Slf4j
@RequiredArgsConstructor
public class InternalPdfJobController {

    private final ReviewAutoSubmitService autoSubmit;
    private final PdfJobCaseResolver jobCases;
    private final ReviewEventPublisher events;
    private final FeatureFlags flags;

    @PostMapping("/{jobId}/completed")
    public ResponseEntity<Void> completed(@PathVariable String jobId) {
        Optional<PdfJobCaseResolver.JobCase> jobCase = resolve(jobId);
        if (jobCase.isEmpty() || !flags.caseReviewEnabledFor(jobCase.get().healerId())) {
            return ResponseEntity.noContent().build();
        }
        publishPdfReady(jobId, jobCase.get());
        try {
            autoSubmit.onPdfCompleted(jobId);
        } catch (Exception e) {
            log.warn("Auto-submit failed for PDF job {}", jobId, e);
        }
        return ResponseEntity.noContent().build();
    }

    /** The job's case, or empty when unknown or the lookup fails: the webhook never errors on a business skip. */
    private Optional<PdfJobCaseResolver.JobCase> resolve(String jobId) {
        try {
            return jobCases.resolve(jobId);
        } catch (Exception e) {
            log.warn("Case for PDF job {} not resolved", jobId, e);
            return Optional.empty();
        }
    }

    /** Tells the case's healer their PDF is ready, whatever their auto-submit setting. Never throws. */
    private void publishPdfReady(String jobId, PdfJobCaseResolver.JobCase jobCase) {
        try {
            events.publishPdfReady(new PdfReadyEvent(jobCase.healerId(), jobCase.caseId(), jobId));
        } catch (Exception e) {
            log.warn("PDF-ready event not published for job {}", jobId, e);
        }
    }
}
