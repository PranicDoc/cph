package com.pranicdoc.cph.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Forgot-password request. Public, so it carries nothing but the address to send the link to. */
public record ForgotPasswordRequest(
        @NotBlank @Email String email
) {}
