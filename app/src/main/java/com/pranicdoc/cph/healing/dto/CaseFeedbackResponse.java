package com.pranicdoc.cph.healing.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** What is recorded for a case, so the healer's feedback form can show it for editing. */
public record CaseFeedbackResponse(List<SessionFeedback> interims, String finalFeedbackText, LocalDate finalFeedbackDate) {

    /** One session's interim feedback: its comment (null when there is none) and symptom ratings. */
    public record SessionFeedback(UUID sessionId, String feedbackText, List<Rating> ratings) {}

    /** A symptom's stored rating: 0 to 10, higher is better. */
    public record Rating(UUID caseSymptomId, Integer rating) {}
}
