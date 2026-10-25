package com.pranicdoc.cph.healing.dto;

import com.pranicdoc.cph.common.enums.FeedbackType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;
import java.util.List;

/**
 * Patient feedback the healer enters by hand: the same symptom ratings and comments as the
 * WhatsApp form, plus the date of the healing the feedback is about.
 */
public record ManualFeedbackRequest(
        @NotNull FeedbackType feedbackType,
        @NotNull @PastOrPresent LocalDate healingDate,
        @Valid List<SymptomFeedbackRequest> symptoms,
        String feedbackText
) {}
