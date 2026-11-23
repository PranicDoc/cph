package com.pranicdoc.cph.healing.dto;

import com.pranicdoc.cph.common.enums.Onset;
import com.pranicdoc.cph.common.enums.Timing;
import com.pranicdoc.cph.common.validation.TextLimits;
import jakarta.validation.constraints.Size;

/** The history of presenting illness for one chief-complaint problem; every answer is optional. */
public record ProblemHistoryRequest(
        Onset onset,
        @Size(max = TextLimits.SHORT_TEXT, message = "Location can be at most {max} characters") String location,
        @Size(max = TextLimits.PRESENTING_ILLNESS, message = "Character can be at most {max} characters") String characterText,
        @Size(max = TextLimits.PRESENTING_ILLNESS, message = "Aggravating factor can be at most {max} characters") String aggravatingFactor,
        @Size(max = TextLimits.PRESENTING_ILLNESS, message = "Relieving factor can be at most {max} characters") String relievingFactor,
        Timing timing,
        @Size(max = TextLimits.PRESENTING_ILLNESS, message = "Timing pattern can be at most {max} characters") String timingPattern
) {}
