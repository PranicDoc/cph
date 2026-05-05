package com.pranicdoc.cph.healing.dto.kafka;

public record QualitativeFeedbackSnapshot(
        String feedbackType,
        String feedbackText,
        String patientRemarks
) {}
