package com.pranicdoc.cph.analytics.service;

import com.pranicdoc.cph.analytics.dto.DateRangeFilter;
import com.pranicdoc.cph.analytics.dto.DashboardPatientGrowth;
import com.pranicdoc.cph.analytics.dto.DashboardSessionTrend;
import com.pranicdoc.cph.analytics.dto.DashboardSummary;
import com.pranicdoc.cph.common.dto.ApiResponse;

import java.util.List;
import java.util.UUID;

public interface DashboardService {
    ApiResponse<DashboardSummary> getSummary(UUID healerId);
    ApiResponse<List<DashboardSessionTrend>> getSessionTrends(UUID healerId, DateRangeFilter filter);
    ApiResponse<List<DashboardPatientGrowth>> getPatientGrowth(UUID healerId, DateRangeFilter filter);
}