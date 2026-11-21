package com.pranicdoc.cph.review.dto;

import java.util.UUID;

/** A healer's linked instructor, with how many of the healer's cases that instructor has approved. */
public record ReviewerAssignmentResponse(
        UUID healerId,
        String healerName,
        UUID reviewerId,
        String reviewerName,
        long approvedCount) {
}
