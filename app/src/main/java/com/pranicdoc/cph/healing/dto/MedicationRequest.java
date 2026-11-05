package com.pranicdoc.cph.healing.dto;

import com.pranicdoc.cph.common.validation.TextLimits;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

public record MedicationRequest(
        @NotBlank @Size(max = TextLimits.SHORT_TEXT, message = "Medication name can be at most {max} characters") String medicationName,
        @Size(max = TextLimits.SHORT_TEXT, message = "Dosage can be at most {max} characters") String dosage,
        Boolean morningDose,
        Boolean afternoonDose,
        Boolean nightDose,
        @Size(max = TextLimits.NOTE, message = "Notes can be at most {max} characters") String notes
) {}
