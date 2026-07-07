package com.pranicdoc.cph.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Public self-signup request payload (SELFREG-01, D-01). Email-only --
 * fullName/password/phone are collected on the shared accept-invite page
 * after the verification token is issued and clicked.
 */
public record SignupRequest(
        @NotBlank @Email String email
) {}
