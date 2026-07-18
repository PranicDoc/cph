package com.pranicdoc.cph.auth.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Invite list/detail view. Deliberately excludes the raw token — the secret
 * only ever travels in the emailed activation URL (T-22-10).
 */
public record InviteResponse(
        UUID id,
        String email,
        String role,
        String status,
        Instant createdAt,
        Instant expiresAt
) {}
