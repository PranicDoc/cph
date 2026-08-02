package com.pranicdoc.cph.consent.dto;

import com.pranicdoc.cph.consent.model.ConsentType;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata-only view of a PolicyVersion row. Deliberately omits the
 * document body field — Phase 31 owns delivery of the drafted text; the
 * reading-flow UI (Phase 33) fetches it separately.
 */
public record PolicyVersionResponse(
        UUID id,
        ConsentType consentType,
        int policyVersion,
        boolean mandatory,
        Instant effectiveDate
) {}
