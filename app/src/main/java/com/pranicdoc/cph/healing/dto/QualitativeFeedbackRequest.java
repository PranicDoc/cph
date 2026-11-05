package com.pranicdoc.cph.healing.dto;

import com.pranicdoc.cph.common.enums.FeedbackType;
import com.pranicdoc.cph.common.validation.TextLimits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The patient's own comment. The tighter interim limit is checked by the service, which knows the type. */
public record QualitativeFeedbackRequest(
        @NotNull FeedbackType feedbackType,
        @Size(max = TextLimits.FINAL_FEEDBACK, message = "Feedback can be at most {max} characters")
        String feedbackText,
        @Size(max = TextLimits.INTERIM_FEEDBACK, message = "Remarks can be at most {max} characters")
        String patientRemarks
) {}
