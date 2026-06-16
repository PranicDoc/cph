package com.pranicdoc.cph.analytics.dto;

import java.time.LocalDate;

public record DateRangeFilter(
        String period,
        LocalDate startDate,
        LocalDate endDate
) {
    // Compact canonical constructor: default period to "30d" when not provided
    public DateRangeFilter {
        if (period == null) period = "30d";
    }
}
