package com.pranicdoc.cph.consent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Open-reading-session request body (READ-01/READ-02 / T-33-01, T-33-02
 * mitigation).
 *
 * Deliberately carries ONLY consentType + policyVersion. openedAt and
 * subjectId are structurally absent because both are resolved server-side
 * (T-33-01/T-33-02) — the caller is physically unable to supply them.
 * ReadingSessionService derives openedAt from Instant.now() and subjectId
 * from the authenticated SecurityContext.
 */
public record ReadingSessionOpenRequest(
        @NotBlank @Size(max = 60) String consentType,
        @NotNull Integer policyVersion
) {}
