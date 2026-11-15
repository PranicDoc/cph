package com.pranicdoc.cph.review.controller;

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

    @PostMapping("/{jobId}/completed")
    public ResponseEntity<Void> completed(@PathVariable String jobId) {
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
}
