package com.pranicdoc.cph.clinic.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClinicCreateRequest(
        @NotBlank @Size(max = 20) String code,
        @NotBlank String name,
        String phone,
        @Email String email,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        @Size(max = 10) String zip
) {}
