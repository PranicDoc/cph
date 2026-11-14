package com.pranicdoc.cph.review.controller;

import com.pranicdoc.cph.healing.dto.PreSignedUrlResponse;
import com.pranicdoc.cph.review.dto.InboxItemResponse;
import com.pranicdoc.cph.review.dto.SubmissionResponse;
import com.pranicdoc.cph.review.model.ReviewStatus;
import com.pranicdoc.cph.review.service.ReviewInboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Instructor side of case review. Every route is gated CASE:REVIEW; the service adds the row-level
 * check that the submission is assigned to the caller and is not their own case.
 */
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewerController {

    private final ReviewInboxService service;

    @GetMapping("/inbox")
    @PreAuthorize("@rbacAuthz.hasPermission('CASE','REVIEW')")
    public List<InboxItemResponse> inbox(@RequestParam(required = false) ReviewStatus status) {
        return service.inbox(status);
    }

    @GetMapping("/inbox/{id}")
    @PreAuthorize("@rbacAuthz.hasPermission('CASE','REVIEW')")
    public SubmissionResponse getItem(@PathVariable UUID id) {
        return service.getItem(id);
    }

    @GetMapping("/inbox/{id}/revisions/{n}/file")
    @PreAuthorize("@rbacAuthz.hasPermission('CASE','REVIEW')")
    public PreSignedUrlResponse getRevisionFile(@PathVariable UUID id, @PathVariable("n") int revisionNo) {
        return service.getFileUrl(id, revisionNo);
    }
}
