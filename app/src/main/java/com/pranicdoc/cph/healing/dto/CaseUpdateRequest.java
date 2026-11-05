package com.pranicdoc.cph.healing.dto;

import com.pranicdoc.cph.common.validation.TextLimits;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CaseUpdateRequest(
        UUID healingCaseId,
        @Size(max = TextLimits.MEDIUM_BOX, message = "Medical condition can be at most {max} characters") String medicalCondition,
        @jakarta.validation.Valid List<SymptomRequest> symptoms,
        @jakarta.validation.Valid HealingDetailsRequest healingDetails,
        @Size(max = TextLimits.NOTE, message = "Protocol name can be at most {max} characters") String protocolName,
        @Size(max = TextLimits.NOTE, message = "Reference book can be at most {max} characters") String referenceBook,
        @Size(max = TextLimits.MEDIUM_BOX, message = "Healing details can be at most {max} characters") String detailedProtocol,
        String basicBookPg,
        String advancedBookPg,
        String psychotherapyPg,
        boolean complementaryTreatments,
        @Size(max = TextLimits.EXPLAIN, message = "Details can be at most {max} characters") String complementaryDetails,
        boolean infectiousIllness,
        @Size(max = TextLimits.EXPLAIN, message = "Details can be at most {max} characters") String infectiousDetails,
        @jakarta.validation.Valid List<MedicationRequest> medications,
        List<String> labFindings
) {
}
