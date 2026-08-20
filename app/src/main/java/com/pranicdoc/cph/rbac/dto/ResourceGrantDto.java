package com.pranicdoc.cph.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * D-02's resource-grouped grant shape: one entry per resource, carrying the
 * list of operations granted on it.
 * <p>
 * {@code resource} and {@code operations} are deliberately plain
 * {@code String}/{@code List<String>}, NEVER typed {@code Resource}/
 * {@code Operation} enums (PD-36-04 / 36-RESEARCH.md Pitfall 1).
 * {@code GlobalExceptionHandler} has no {@code HttpMessageNotReadableException}
 * handler, so a bad literal on a typed-enum field would turn Jackson's
 * automatic enum binding failure into an unhandled 500 instead of D-03's
 * required clean 400. Parsing (and the {@code Resource.supports(Operation)}
 * allow-list check) happens explicitly in the service layer (plan 36-03),
 * not here.
 */
public record ResourceGrantDto(
        @NotBlank String resource,
        @NotEmpty List<@NotBlank String> operations
) {}
