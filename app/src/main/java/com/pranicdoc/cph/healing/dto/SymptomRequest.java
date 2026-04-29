package com.pranicdoc.cph.healing.dto;

import jakarta.validation.constraints.NotBlank;

public record SymptomRequest(
        @NotBlank String symptomName,
        String description,
        Integer severity
) {}
