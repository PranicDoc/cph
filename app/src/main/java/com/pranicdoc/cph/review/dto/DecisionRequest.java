package com.pranicdoc.cph.review.dto;

import jakarta.validation.constraints.NotNull;

/** The revision the instructor is deciding on; it must be the submission's latest. */
public record DecisionRequest(@NotNull Integer revisionNo) {
}
