package com.pranicdoc.cph.review.dto;

import com.pranicdoc.cph.review.model.ReviewStage;
import com.pranicdoc.cph.review.model.ReviewStatus;
import com.pranicdoc.cph.review.model.SubmittedVia;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SubmissionResponse(
        UUID id,
        UUID caseId,
        UUID healerId,
        UUID reviewerId,
        ReviewStage stage,
        ReviewStatus status,
        boolean dismissedFromApproved,
        String dismissReason,
        SubmittedVia submittedVia,
        int latestRevisionNo,
        Instant createdAt,
        Instant updatedAt,
        List<RevisionResponse> revisions) {
}
