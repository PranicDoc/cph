package com.pranicdoc.cph.review.dto;

import java.util.UUID;

/**
 * An account offered in the reviewer picker. {@code hasReviewPermission} is a real CASE:REVIEW role grant
 * (false for a SUPADM, who holds no role rows); neither flag limits who can be assigned.
 */
public record HealerOption(UUID id, String fullName, String email,
                           boolean hasReviewPermission, boolean superAdmin) {
}
