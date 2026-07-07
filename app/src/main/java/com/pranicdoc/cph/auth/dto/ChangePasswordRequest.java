package com.pranicdoc.cph.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        String currentPassword,                    // Optional — null for Google-only healers
        @NotBlank @Size(min = 8) String newPassword,
        @NotBlank String confirmPassword
) {}
