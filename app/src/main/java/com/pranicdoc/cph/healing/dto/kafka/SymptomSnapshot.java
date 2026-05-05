package com.pranicdoc.cph.healing.dto.kafka;

import java.time.LocalDate;

public record SymptomSnapshot(
        String symptomName,
        String description,
        Integer severity,
        LocalDate addedDate,
        LocalDate resolvedDate,
        boolean resolved
) {}