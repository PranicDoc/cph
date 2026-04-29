package com.pranicdoc.cph.patient.dto;

public record MedicalHistoryRequest(
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
