package com.pranicdoc.cph.analytics.dto;

import java.time.LocalDate;
import java.util.UUID;

public record DashboardPendingReport(
        UUID sessionId,
        String patientFirstName,
        String patientLastName,
        LocalDate sessionDate
) {}
