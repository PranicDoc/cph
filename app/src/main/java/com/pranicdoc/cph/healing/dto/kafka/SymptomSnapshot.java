package com.pranicdoc.cph.healing.dto.kafka;

import java.time.LocalDate;

@Deprecated(since = "0.0.1-SNAPSHOT", forRemoval = false)
public record SymptomSnapshot(
        String symptomName,
        String description,
        Integer severity,
        LocalDate addedDate,
        LocalDate resolvedDate,
        boolean resolved
) {}