package com.pranicdoc.cph.healing.dto;

import com.pranicdoc.cph.common.enums.FeedbackType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Patient feedback the healer enters by hand: the same symptom ratings and comments as the
 * WhatsApp form, plus the date of the healing the feedback is about. {@code sessionId} optionally
 * names the session the feedback is for; without it the session is derived from the healing date.
 */
public record ManualFeedbackRequest(
        @NotNull FeedbackType feedbackType,
        @NotNull @PastOrPresent LocalDate healingDate,
        @Valid List<SymptomFeedbackRequest> symptoms,
        String feedbackText,
        UUID sessionId
) {}
