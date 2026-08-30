package com.pranicdoc.cph.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One-time bootstrap request — creates the first healer on an empty system
 * (solo-healer world, no clinic — CUTOVER-05). Always seeded as SUPADM (D-02);
 * the request carries no role choice.
 */
public record BootstrapRequest(
        @NotBlank String fullName,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6) String password,
        String phone
) {}
