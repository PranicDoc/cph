package com.pranicdoc.cph.healing.dto.kafka;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CaseSnapshot(
        UUID id,
        UUID patientId,
        UUID healerId,
        String medicalCondition,
        String labFindings,
        Boolean complementaryTreatments,
        String complementaryDetails,
        Boolean infectiousIllness,
        String infectiousDetails,
        String basicBookPg,
        String advancedBookPg,
        String psychotherapyPg,
        String status,
        Instant createdAt,
        Instant updatedAt,

        HealingDetailsSnapshot healingDetails,
        MedicalSnapshot medicalSnapshot,

        List<SessionSnapshot> sessions,
        List<SymptomSnapshot> symptoms,
        List<MedicationSnapshot> medications
) {}
