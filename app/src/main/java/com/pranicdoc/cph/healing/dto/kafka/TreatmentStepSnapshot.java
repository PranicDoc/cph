package com.pranicdoc.cph.healing.dto.kafka;

import java.util.List;

public record TreatmentStepSnapshot(
        int step,
        String tag,
        String sweepType,
        String notes,
        int cycles,
        List<String> targets
) {}