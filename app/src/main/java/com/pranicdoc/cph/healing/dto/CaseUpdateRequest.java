package com.pranicdoc.cph.healing.dto;

import java.util.List;
import java.util.UUID;

public record CaseUpdateRequest(
        UUID healingCaseId,
        String medicalCondition,
        List<SymptomRequest> symptoms,
        HealingDetailsRequest healingDetails,
        String basicBookPg,
        String advancedBookPg,
        String psychotherapyPg,
        String labFindings,
        boolean complementaryTreatments,
        String complementaryDetails,
        boolean infectiousIllness,
        String infectiousDetails,
        List<MedicationRequest> medications
) {
}
