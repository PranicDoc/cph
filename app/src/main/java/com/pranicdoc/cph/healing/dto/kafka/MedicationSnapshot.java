package com.pranicdoc.cph.healing.dto.kafka;

@Deprecated(since = "0.0.1-SNAPSHOT", forRemoval = false)
public record MedicationSnapshot(
        String medicationName,
        String dosage,
        String frequency,
        String notes
) {}