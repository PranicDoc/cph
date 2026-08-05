package com.pranicdoc.cph.consent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Accept-consent request body (CONSENT-05 / T-30-01 mitigation).
 *
 * Deliberately carries ONLY consentType + policyVersion. All server-captured
 * acceptance metadata (who/how/where the acceptance came from) is
 * structurally absent from this record — the caller is physically unable to
 * supply it. ConsentService derives every bit of that metadata itself, from
 * the authenticated SecurityContext and the inbound servlet request.
 * consentType is now an open key string, validated against the registry
 * server-side (CONSENT-09).
 */
public record ConsentAcceptRequest(
        @NotBlank @Size(max = 60) String consentType,
        @NotNull Integer policyVersion
) {}
