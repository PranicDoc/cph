package com.pranicdoc.cph.healing.dto;

import com.pranicdoc.cph.common.validation.TextLimits;
import jakarta.validation.constraints.Size;
import java.util.List;

public record TreatmentStepRequest(
    int step,
    String tag,
    List<String> targets,
    String sweepType,
    int cycles,
    @Size(max = TextLimits.NOTE, message = "Notes can be at most {max} characters") String notes
) {
}
