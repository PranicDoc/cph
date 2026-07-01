package com.pranicdoc.cph.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record HealerRequest(
        @NotBlank String fullName,
        @NotBlank @Email String email,
        String phone
) {}
