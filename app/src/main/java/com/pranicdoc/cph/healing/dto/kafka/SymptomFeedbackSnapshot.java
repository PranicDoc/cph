package com.pranicdoc.cph.healing.dto.kafka;

import java.util.UUID;

@Deprecated(since = "0.0.1-SNAPSHOT", forRemoval = false)
public record SymptomFeedbackSnapshot(
        UUID caseSymptomId,
        Integer rating,
        String notes
) {}
