package com.pranicdoc.cph.review.controller;

import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.healing.dto.PreSignedUrlResponse;
import com.pranicdoc.cph.review.dto.ApprovedCountResponse;
import com.pranicdoc.cph.review.dto.CaseReviewStatusResponse;
import com.pranicdoc.cph.review.dto.SubmissionResponse;
import com.pranicdoc.cph.review.model.SubmittedVia;
import com.pranicdoc.cph.review.service.ReviewRefusedException;
import com.pranicdoc.cph.review.service.ReviewSubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Healer side of case review. Every route is gated CASE:UPDATE; the service adds the row-level
 * check that the case or submission belongs to the caller.
 */
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class HealerReviewController {

    private final ReviewSubmissionService service;
    private final SecurityContextUtil securityContextUtil;

    @PostMapping("/cases/{caseId}/submit")
    @PreAuthorize("@rbacAuthz.hasPermission('CASE','UPDATE')")
    public SubmissionResponse submit(@PathVariable UUID caseId) {
        return service.submit(caseId, SubmittedVia.MANUAL);
    }

    @GetMapping("/my-submissions")
    @PreAuthorize("@rbacAuthz.hasPermission('CASE','UPDATE')")
    public List<SubmissionResponse> mySubmissions() {
        return service.mySubmissions();
    }

    @GetMapping("/my-approved-count")
    @PreAuthorize("@rbacAuthz.hasPermission('CASE','UPDATE')")
    public ApprovedCountResponse myApprovedCount() {
        return new ApprovedCountResponse(service.approvedCount(securityContextUtil.getCurrentHealerId()));
    }

    @GetMapping("/case-statuses")
    @PreAuthorize("@rbacAuthz.hasPermission('CASE','UPDATE')")
    public List<CaseReviewStatusResponse> caseStatuses(@RequestParam List<UUID> caseIds) {
        return List.copyOf(service.statusesFor(caseIds).values());
    }

    @GetMapping("/submissions/{id}")
    @PreAuthorize("@rbacAuthz.hasPermission('CASE','UPDATE')")
    public SubmissionResponse getSubmission(@PathVariable UUID id) {
        return service.getSubmission(id);
    }

    @GetMapping("/submissions/{id}/revisions/{n}/file")
    @PreAuthorize("@rbacAuthz.hasPermission('CASE','UPDATE')")
    public PreSignedUrlResponse getRevisionFile(@PathVariable UUID id, @PathVariable("n") int revisionNo) {
        return service.getFileUrl(id, revisionNo);
    }

    /** A refused submit (no instructor linked, no PDF yet, already approved) is a 409 carrying the reason. */
    @ExceptionHandler(ReviewRefusedException.class)
    public ProblemDetail handleRefused(ReviewRefusedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }
}
