package com.pranicdoc.cph.healing.dto.kafka;

import java.util.UUID;

public record SymptomFeedbackSnapshot(
        UUID caseSymptomId,
        Integer rating,
        String notes
) {}
