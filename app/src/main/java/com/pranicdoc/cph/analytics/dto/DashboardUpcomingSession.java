package com.pranicdoc.cph.analytics.dto;

import com.pranicdoc.cph.common.enums.SessionStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record DashboardUpcomingSession(
        UUID sessionId,
        String patientFirstName,
        String patientLastName,
        LocalDate sessionDate,
        LocalTime startTime,
        SessionStatus status
) {}
