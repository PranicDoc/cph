package com.pranicdoc.cph.healing.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SymptomFeedbackRequest(
        @NotNull UUID caseSymptomId,
        @NotNull @Min(0) @Max(10) Integer rating,
        String notes
) {}
