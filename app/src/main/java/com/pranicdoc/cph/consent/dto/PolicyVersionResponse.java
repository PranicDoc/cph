package com.pranicdoc.cph.consent.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * View of a PolicyVersion row, including the published document body.
 * {@code content} is the Phase 31 published Markdown text (nullable until a
 * version is actually published); {@code publisherId} is the denormalized
 * D-04/D-05 attribution of who published it (nullable, no scoping meaning —
 * D-06). The reading-flow UI (Phase 33) renders {@code content} directly.
 */
public record PolicyVersionResponse(
        UUID id,
        String consentType,
        int policyVersion,
        boolean mandatory,
        Instant effectiveDate,
        String content,
        UUID publisherId
) {}
