package com.pranicdoc.cph.review.dto;

import com.pranicdoc.cph.review.model.ReviewStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the super admin's "All reviews" list. The reviewer is the one stored on the submission;
 * both reviewer fields are null when it has none. {@code submittedAt} is when the latest revision arrived.
 */
public record AdminReviewItemResponse(
        UUID submissionId,
        UUID healerId,
        String healerName,
        UUID reviewerId,
        String reviewerName,
        String caseTitle,
        int latestRevisionNo,
        ReviewStatus status,
        Instant submittedAt) {
}
