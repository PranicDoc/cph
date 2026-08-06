package com.pranicdoc.cph.consent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create-consent-type request body (CONSENT-09 / T-31.1-04 mitigation).
 *
 * Deliberately carries ONLY label. {@code key}, {@code mandatory},
 * {@code active}, and {@code systemDefined} are structurally absent from
 * this record — the caller is physically unable to supply them, even by
 * crafting extra JSON fields. {@code ConsentTypeService.create()} derives
 * {@code key} server-side by slugifying {@code label} (D-05), and hardcodes
 * {@code mandatory = false} on every create (D-01/CONSENT-10) — a new type
 * is always opt-in until an admin explicitly promotes it.
 */
public record ConsentTypeCreateRequest(
        @NotBlank @Size(max = 120) String label
) {}
