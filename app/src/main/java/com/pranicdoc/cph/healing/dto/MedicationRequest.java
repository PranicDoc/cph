package com.pranicdoc.cph.healing.dto;

import jakarta.validation.constraints.NotBlank;

public record MedicationRequest(
        @NotBlank String medicationName,
        String dosage,
        Boolean morningDose,
        Boolean afternoonDose,
        Boolean nightDose,
        String notes
) {}
