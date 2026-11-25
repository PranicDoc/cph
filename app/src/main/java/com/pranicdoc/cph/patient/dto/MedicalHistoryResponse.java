package com.pranicdoc.cph.patient.dto;

import java.math.BigDecimal;
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
        String additionalNotes,
        BigDecimal heightCm,
        BigDecimal weightKg,
        String pastHistory,
        String surgicalHistory,
        String familyHistory,
        String psychosocialHistory
) {
    /** The shape before measurements and the four histories existed. */
    public MedicalHistoryResponse(UUID id, UUID patientId, String generalHealth, String diet, String exercise,
                                  String exerciseFrequency, Boolean hypertension, Boolean giIssues,
                                  Boolean stressDepressionAnxiety, Boolean pregnant, Boolean cancer,
                                  String cancerLocation, String cancerDetails, String additionalNotes) {
        this(id, patientId, generalHealth, diet, exercise, exerciseFrequency, hypertension, giIssues,
                stressDepressionAnxiety, pregnant, cancer, cancerLocation, cancerDetails, additionalNotes,
                null, null, null, null, null, null);
    }
}
