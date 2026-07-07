package com.pranicdoc.cph.auth.dto;

import com.pranicdoc.cph.common.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Admin invite-creation payload (ADMIN-01).
 * clinicId is nullable — only honored for SUPADM callers; regular ADMIN callers
 * have their own clinicId resolved server-side (D-02), the controller ignores
 * this field for them.
 */
public record InviteCreateRequest(
        @NotBlank @Email String email,
        @NotNull Role role,
        UUID clinicId
) {}
