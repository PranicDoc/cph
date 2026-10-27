package com.pranicdoc.cph.analytics.dto;

import java.time.LocalDate;
import java.util.UUID;

/** One row of the dashboard's active/draft case list. */
public record DashboardCase(
        UUID caseId,
        String patientFirstName,
        String patientLastName,
        String status,
        LocalDate lastSessionDate
) {}
