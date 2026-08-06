package com.pranicdoc.cph.consent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Rename-consent-type request body.
 *
 * Renaming is label-only. {@code key} is immutable once a consent type is
 * created — {@code consent.consent_type} / {@code policy_version.consent_type}
 * reference it as a denormalized non-FK string (V26/V27 precedent), so
 * changing it after the fact would orphan every historical consent and
 * policy-version row already keyed on the old value.
 */
public record ConsentTypeRenameRequest(
        @NotBlank @Size(max = 120) String label
) {}
