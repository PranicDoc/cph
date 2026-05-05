package com.pranicdoc.cph.healing.dto.kafka;

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