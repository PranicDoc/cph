package com.pranicdoc.cph.consent.dto;

import com.pranicdoc.cph.consent.model.ConsentType;
import jakarta.validation.constraints.NotNull;

/**
 * Accept-consent request body (CONSENT-05 / T-30-01 mitigation).
 *
 * Deliberately carries ONLY consentType + policyVersion. All server-captured
 * acceptance metadata (who/how/where the acceptance came from) is
 * structurally absent from this record — the caller is physically unable to
 * supply it. ConsentService derives every bit of that metadata itself, from
 * the authenticated SecurityContext and the inbound servlet request.
 */
public record ConsentAcceptRequest(
        @NotNull ConsentType consentType,
        @NotNull Integer policyVersion
) {}
