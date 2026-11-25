package com.pranicdoc.cph.review.controller;

import com.pranicdoc.cph.common.feature.RequiresCaseReview;
import com.pranicdoc.cph.healing.dto.PreSignedUrlResponse;
import com.pranicdoc.cph.review.dto.CommentRequest;
import com.pranicdoc.cph.review.dto.CommentResponse;
import com.pranicdoc.cph.review.dto.DecisionRequest;
import com.pranicdoc.cph.review.dto.InboxItemResponse;
import com.pranicdoc.cph.review.dto.SubmissionResponse;
import com.pranicdoc.cph.review.model.AuthorSide;
import com.pranicdoc.cph.review.model.ReviewStatus;
import com.pranicdoc.cph.review.service.ReviewCommentService;
import com.pranicdoc.cph.review.service.ReviewInboxService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Instructor side of case review. Every route is open to a caller with CASE:REVIEW or with a healer
 * assigned to them ({@code ReviewAuthz}); the service adds the row-level check that the submission is
 * assigned to the caller and is not their own case, which only a super admin bypasses.
 */
@RestController
@RequiresCaseReview
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewerController {

    private final ReviewInboxService service;
    private final ReviewCommentService commentService;

    @GetMapping("/inbox")
    @PreAuthorize("@reviewAuthz.canReview()")
    public List<InboxItemResponse> inbox(@RequestParam(required = false) ReviewStatus status) {
        return service.inbox(status);
    }

    @GetMapping("/inbox/{id}")
    @PreAuthorize("@reviewAuthz.canReview()")
    public SubmissionResponse getItem(@PathVariable UUID id) {
        return service.getItem(id);
    }

    @GetMapping("/inbox/{id}/revisions/{n}/file")
    @PreAuthorize("@reviewAuthz.canReview()")
    public PreSignedUrlResponse getRevisionFile(@PathVariable UUID id, @PathVariable("n") int revisionNo) {
        return service.getFileUrl(id, revisionNo);
    }

    @GetMapping("/inbox/{id}/comments")
    @PreAuthorize("@reviewAuthz.canReview()")
    public List<CommentResponse> listComments(@PathVariable UUID id) {
        return commentService.list(id, AuthorSide.REVIEWER);
    }

    @PostMapping("/inbox/{id}/comments")
    @PreAuthorize("@reviewAuthz.canReview()")
    public CommentResponse addComment(@PathVariable UUID id, @Valid @RequestBody CommentRequest request) {
        return commentService.add(id, request, AuthorSide.REVIEWER);
    }

    @PostMapping("/inbox/comments/{commentId}/resolve")
    @PreAuthorize("@reviewAuthz.canReview()")
    public void resolveComment(@PathVariable UUID commentId) {
        commentService.resolve(commentId);
    }

    /** Approves the latest revision; a stale {@code revisionNo} is a 409. */
    @PostMapping("/inbox/{id}/approve")
    @PreAuthorize("@reviewAuthz.canReview()")
    public SubmissionResponse approve(@PathVariable UUID id, @Valid @RequestBody DecisionRequest request) {
        return service.approve(id, request.revisionNo());
    }

    /** Asks the healer for changes on the latest revision; a stale {@code revisionNo} is a 409. */
    @PostMapping("/inbox/{id}/request-changes")
    @PreAuthorize("@reviewAuthz.canReview()")
    public SubmissionResponse requestChanges(@PathVariable UUID id, @Valid @RequestBody DecisionRequest request) {
        return service.requestChanges(id, request.revisionNo());
    }
}
