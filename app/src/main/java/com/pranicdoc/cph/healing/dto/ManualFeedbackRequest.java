package com.pranicdoc.cph.healing.dto;

import com.pranicdoc.cph.common.enums.FeedbackType;
import com.pranicdoc.cph.common.validation.TextLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Patient feedback the healer enters or edits by hand. INTERIM feedback is for one session
 * ({@code sessionId} is required) and carries symptom ratings and/or a comment. FINAL feedback is one
 * per case: a comment only, saved against the case's latest session ({@code sessionId} is ignored).
 * Saving again updates what is there. The healing date of an interim is the session's own date;
 * a final's is {@code finalFeedbackDate} (optional: the latest session's date for a new one, unchanged on edit).
 * The tighter interim limit is checked by the service, which knows the type.
 */
public record ManualFeedbackRequest(
        @NotNull FeedbackType feedbackType,
        UUID sessionId,
        @Valid List<SymptomFeedbackRequest> symptoms,
        @Size(max = TextLimits.FINAL_FEEDBACK, message = "Feedback can be at most {max} characters")
        String feedbackText,
        @PastOrPresent LocalDate finalFeedbackDate
) {}
