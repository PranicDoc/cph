package com.pranicdoc.cph.review.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignReviewerRequest(@NotNull UUID reviewerId) {
}
