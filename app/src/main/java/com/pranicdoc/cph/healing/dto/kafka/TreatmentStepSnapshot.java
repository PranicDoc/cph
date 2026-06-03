package com.pranicdoc.cph.healing.dto.kafka;

import java.util.List;

@Deprecated(since = "0.0.1-SNAPSHOT", forRemoval = false)
public record TreatmentStepSnapshot(
        int step,
        String tag,
        String sweepType,
        String notes,
        int cycles,
        List<String> targets
) {}