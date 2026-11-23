package com.pranicdoc.cph.patient.dto;

import com.pranicdoc.cph.common.validation.TextLimits;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

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
        @Size(max = TextLimits.SHORT_TEXT, message = "Location can be at most {max} characters") String cancerLocation,
        @Size(max = TextLimits.HISTORY_BOX, message = "Details can be at most {max} characters") String cancerDetails,
        @Size(max = TextLimits.HISTORY_BOX, message = "Notes can be at most {max} characters") String additionalNotes,
        @DecimalMin(value = "50", message = "Height must be at least {value} cm")
        @DecimalMax(value = "250", message = "Height can be at most {value} cm") BigDecimal heightCm,
        @DecimalMin(value = "2", message = "Weight must be at least {value} kg")
        @DecimalMax(value = "500", message = "Weight can be at most {value} kg") BigDecimal weightKg,
        @Size(max = TextLimits.HISTORY_LINE, message = "Past history can be at most {max} characters") String pastHistory,
        @Size(max = TextLimits.HISTORY_LINE, message = "Surgical history can be at most {max} characters") String surgicalHistory,
        @Size(max = TextLimits.HISTORY_LINE, message = "Family history can be at most {max} characters") String familyHistory,
        @Size(max = TextLimits.HISTORY_LINE, message = "Psychosocial history can be at most {max} characters") String psychosocialHistory
) {
    /** The shape before measurements and the four histories existed. */
    public MedicalHistoryRequest(String generalHealth, String diet, String exercise, String exerciseFrequency,
                                 Boolean hypertension, Boolean giIssues, Boolean stressDepressionAnxiety,
                                 Boolean pregnant, Boolean cancer, String cancerLocation, String cancerDetails,
                                 String additionalNotes) {
        this(generalHealth, diet, exercise, exerciseFrequency, hypertension, giIssues, stressDepressionAnxiety,
                pregnant, cancer, cancerLocation, cancerDetails, additionalNotes, null, null, null, null, null, null);
    }
}
