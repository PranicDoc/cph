package com.pranicdoc.cph.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sets a new password using the token from the forgot-password email. Same length rule as change-password. */
public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 128) String newPassword,
        @NotBlank String confirmPassword
) {}
