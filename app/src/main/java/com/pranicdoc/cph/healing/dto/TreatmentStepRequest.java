package com.pranicdoc.cph.healing.dto;

import java.util.List;

public record TreatmentStepRequest(
    int step,
    String tag,
    List<String> targets,
    String sweepType,
    int cycles,
    String notes
) {
}
