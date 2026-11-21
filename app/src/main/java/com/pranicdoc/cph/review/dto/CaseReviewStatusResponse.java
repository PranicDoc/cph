package com.pranicdoc.cph.review.dto;

import java.util.UUID;

/**
 * Review state of one case for the patients page. {@code status} is one of NOT_SUBMITTED,
 * NOT_SUBMITTED_NO_REVIEWER, PENDING_REVIEW, CHANGES_REQUESTED, APPROVED, DISMISSED.
 * {@code eligible} is true when the case has a completed PDF that could be submitted.
 */
public record CaseReviewStatusResponse(UUID caseId, String status, boolean eligible, Integer latestRevisionNo) {
}
