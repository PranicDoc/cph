package com.pranicdoc.cph.healing.dto.kafka;

@Deprecated(since = "0.0.1-SNAPSHOT", forRemoval = false)
public record QualitativeFeedbackSnapshot(
        String feedbackType,
        String feedbackText,
        String patientRemarks
) {}
