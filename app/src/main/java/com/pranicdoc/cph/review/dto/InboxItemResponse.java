package com.pranicdoc.cph.review.dto;

import com.pranicdoc.cph.review.model.ReviewStatus;

import java.time.Instant;
import java.util.UUID;

/** One row of the reviewer's queue. {@code submittedAt} is when the latest revision arrived. */
public record InboxItemResponse(
        UUID submissionId,
        String healerName,
        String caseTitle,
        int latestRevisionNo,
        ReviewStatus status,
        Instant submittedAt) {
}
