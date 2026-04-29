package com.pranicdoc.cph.auth.dto;

import com.pranicdoc.cph.common.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One-time bootstrap request — creates the first clinic + admin healer.
 */
public record BootstrapRequest(
        // --- Healer ---
        @NotBlank String fullName,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6) String password,
        @NotNull Role role,

        // --- Clinic ---
        @NotBlank String clinicCode,
        String clinicName,   // optional — defaults to clinicCode if not provided
        String phone,
        String city
) {}
