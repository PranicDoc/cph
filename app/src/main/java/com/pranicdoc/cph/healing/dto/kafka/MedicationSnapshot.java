package com.pranicdoc.cph.healing.dto.kafka;

public record MedicationSnapshot(
        String medicationName,
        String dosage,
        String frequency,
        String notes
) {}