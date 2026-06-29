package com.pranicdoc.cph.analytics.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record DashboardUpcomingSession(
        UUID id,
        String patientFirstName,
        String patientLastName,
        LocalDate date,
        LocalTime startTime,
        String type
) {}
