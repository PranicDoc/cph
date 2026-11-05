package com.pranicdoc.cph.patient.dto;

import com.pranicdoc.cph.common.validation.TextLimits;
import jakarta.validation.constraints.Size;
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
        @Size(max = TextLimits.HISTORY_BOX, message = "Notes can be at most {max} characters") String additionalNotes
) {}
