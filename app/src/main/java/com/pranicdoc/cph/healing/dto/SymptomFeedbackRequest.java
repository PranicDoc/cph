package com.pranicdoc.cph.healing.dto;

import com.pranicdoc.cph.common.validation.TextLimits;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SymptomFeedbackRequest(
        @NotNull UUID caseSymptomId,
        @NotNull @Min(0) @Max(10) Integer rating,
        @Size(max = TextLimits.NOTE, message = "Notes can be at most {max} characters") String notes
) {}
