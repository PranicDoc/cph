package com.pranicdoc.cph.healing.dto;

import com.pranicdoc.cph.common.enums.FeedbackType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Patient feedback the healer enters by hand. INTERIM feedback is for one session ({@code sessionId}
 * is required) and carries symptom ratings and/or a comment. FINAL feedback is one per case: a
 * comment only, saved against the case's latest session ({@code sessionId} is ignored). The healing
 * date is never sent; it is the session's own date.
 */
public record ManualFeedbackRequest(
        @NotNull FeedbackType feedbackType,
        UUID sessionId,
        @Valid List<SymptomFeedbackRequest> symptoms,
        String feedbackText
) {}
