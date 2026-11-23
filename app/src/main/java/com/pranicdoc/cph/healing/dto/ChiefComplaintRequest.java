package com.pranicdoc.cph.healing.dto;

import com.pranicdoc.cph.common.enums.DurationUnit;
import com.pranicdoc.cph.common.validation.TextLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * One problem of the chief complaint. A blank problem is a skipped row; a row with a problem must carry both
 * durationValue and durationUnit (the service enforces that so the message can name the problem).
 */
public record ChiefComplaintRequest(
        UUID id,
        @Size(max = TextLimits.SHORT_TEXT, message = "Problem can be at most {max} characters") String problem,
        @Min(value = 1, message = "Duration must be at least 1")
        @Max(value = 999, message = "Duration can be at most 999") Integer durationValue,
        DurationUnit durationUnit,
        @Valid ProblemHistoryRequest problemHistory
) {}
