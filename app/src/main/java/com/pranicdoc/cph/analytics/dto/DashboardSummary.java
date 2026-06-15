package com.pranicdoc.cph.clinic.dto;

public record DashboardSummary(
        long activePatients,
        long sessionsThisMonth,
        long creditsRemaining,
        long pdfsGeneratedThisMonth
) {
}
