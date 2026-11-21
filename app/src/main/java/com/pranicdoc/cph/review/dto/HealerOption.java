package com.pranicdoc.cph.review.dto;

import java.util.UUID;

/** A healer offered in a picker. */
public record HealerOption(UUID id, String fullName, String email) {
}
