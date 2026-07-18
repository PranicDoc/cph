package com.pranicdoc.cph.auth.dto;

import com.pranicdoc.cph.common.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Admin invite-creation payload (ADMIN-01). Solo-healer world (CUTOVER-05):
 * invites are no longer clinic-scoped — every ADMIN/SUPADM caller creates a
 * plain, clinic-free invite.
 */
public record InviteCreateRequest(
        @NotBlank @Email String email,
        @NotNull Role role
) {}
