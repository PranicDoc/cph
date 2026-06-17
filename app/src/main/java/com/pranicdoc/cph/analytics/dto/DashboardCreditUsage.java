package com.pranicdoc.cph.analytics.dto;

import java.time.LocalDate;

public record DashboardCreditUsage(LocalDate date, long creditsConsumed) {}
