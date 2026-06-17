package com.pranicdoc.cph.analytics.dto;

import java.time.LocalDate;
import java.util.UUID;

public record DashboardFollowup(
        UUID patientId,
        String patientFirstName,
        String patientLastName,
        LocalDate lastSessionDate,
        long daysSinceLastSession
) {}
