package com.pranicdoc.cph.healing.dto;

import com.pranicdoc.cph.common.validation.TextLimits;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

public record SymptomRequest(
        @NotBlank @Size(max = TextLimits.SYMPTOM_NAME, message = "Symptom name can be at most {max} characters") String symptomName,
        @Size(max = TextLimits.NOTE, message = "Description can be at most {max} characters") String description,
        Integer severity
) {}
