package com.pranicdoc.cph.analytics.dto;

import java.time.LocalDate;

public record DashboardPatientGrowth(
        LocalDate date,
        long count
) {
}
