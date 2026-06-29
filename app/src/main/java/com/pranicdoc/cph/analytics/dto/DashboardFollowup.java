package com.pranicdoc.cph.analytics.dto;

import java.time.Instant;
import java.util.UUID;

public record DashboardFollowup(
        UUID caseId,
        String patientFirstName,
        String patientLastName,
        String feedbackToken,
        Instant sentAt,
        boolean isFinal
) {}
