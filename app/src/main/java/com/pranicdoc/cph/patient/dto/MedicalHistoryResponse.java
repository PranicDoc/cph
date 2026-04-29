package com.pranicdoc.cph.patient.dto;

import java.util.UUID;

public record MedicalHistoryResponse(
        UUID id,
        UUID patientId,
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
