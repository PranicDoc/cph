package com.pranicdoc.cph.healing.dto.kafka;

@Deprecated(since = "0.0.1-SNAPSHOT", forRemoval = false)
public record MedicalSnapshot(
        String generalHealth,
        String diet,
        String exercise,
        String exerciseFrequency,
        Boolean hypertension,
        Boolean giIssues,
        Boolean stressDepressionAnxiety,
        Boolean pregnant,
        Boolean cancer,
        String cancerLocation,
        String cancerDetails,
        String additionalNotes
) {}