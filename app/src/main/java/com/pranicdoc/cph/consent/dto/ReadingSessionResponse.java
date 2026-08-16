package com.pranicdoc.cph.consent.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Caller-facing view of a ReadingSession row. closedAt and durationSeconds
 * are null for a still-open session.
 */
public record ReadingSessionResponse(
        UUID id,
        UUID subjectId,
        String consentType,
        int policyVersion,
        Instant openedAt,
        Instant closedAt,
        Long durationSeconds,
        boolean scrollCompleted
) {}
