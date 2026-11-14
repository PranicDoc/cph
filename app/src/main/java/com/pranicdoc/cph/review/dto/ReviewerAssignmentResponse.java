package com.pranicdoc.cph.review.dto;

import java.util.UUID;

public record ReviewerAssignmentResponse(
        UUID healerId,
        String healerName,
        UUID reviewerId,
        String reviewerName) {
}
