package com.pranicdoc.cph.analytics.dto;

public record DashboardSummary(
        long activePatients,
        long sessionsThisMonth,
        Long creditsRemaining,
        long pdfsGeneratedThisMonth,
        boolean creditServiceUnavailable
) {
}
