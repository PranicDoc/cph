package com.pranicdoc.cph.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Invite-creation payload for the admin invite flow. Solo-healer world
 * (CUTOVER-05): invites are no longer clinic-scoped. The request carries no role
 * choice at all — every invite mints a plain HEALER, and elevated capability is a
 * separate, subsequent role-assignment action (Phase 41 / MIGRATE-04, D-06).
 */
public record InviteCreateRequest(
        @NotBlank @Email String email
) {}
