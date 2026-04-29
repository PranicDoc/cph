package com.pranicdoc.cph.healing.dto;

import com.pranicdoc.cph.common.enums.FeedbackType;
import jakarta.validation.constraints.NotNull;

public record QualitativeFeedbackRequest(
        @NotNull FeedbackType feedbackType,
        String feedbackText,
        String patientRemarks
) {}
