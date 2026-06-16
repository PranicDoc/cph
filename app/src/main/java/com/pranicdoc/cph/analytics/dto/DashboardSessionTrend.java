package com.pranicdoc.cph.analytics.dto;

import java.time.LocalDate;

public record DashboardSessionTrend(
        LocalDate date,
        long count
) {
}
