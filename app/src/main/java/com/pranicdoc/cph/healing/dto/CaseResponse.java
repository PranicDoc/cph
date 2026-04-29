package com.pranicdoc.cph.healing.dto;

import com.pranicdoc.cph.common.enums.CaseStatus;
import com.pranicdoc.cph.healing.model.CaseMedicalSnapshot;
import com.pranicdoc.cph.healing.model.HealingSession;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CaseResponse(
        UUID id,
        UUID patientId,
        UUID healerId,
        String medicalCondition,
        String healingDetails,
        String labFindings,
        Boolean complementaryTreatments,
        String complementaryDetails,
        Boolean infectiousIllness,
        String infectiousDetails,
        String basicBookPg,
        String advancedBookPg,
        String psychotherapyPg,
        CaseStatus status,
        Instant createdAt,
        Instant updatedAt,
        CaseMedicalSnapshot medicalSnapshot,
        List<HealingSession> healingSession
) {}
