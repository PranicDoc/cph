package com.pranicdoc.cph.healing.dto.kafka;

@Deprecated(since = "0.0.1-SNAPSHOT", forRemoval = false)
public record MedicationSnapshot(
        String medicationName,
        String dosage,
        Boolean morningDose,
        Boolean afternoonDose,
        Boolean nightDose,
        String notes
) {}
