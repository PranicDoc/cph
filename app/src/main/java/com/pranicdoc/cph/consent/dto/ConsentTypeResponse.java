package com.pranicdoc.cph.consent.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * View of a ConsentType registry row.
 */
public record ConsentTypeResponse(
        UUID id,
        String key,
        String label,
        boolean mandatory,
        boolean active,
        boolean systemDefined,
        Instant createdAt
) {}
