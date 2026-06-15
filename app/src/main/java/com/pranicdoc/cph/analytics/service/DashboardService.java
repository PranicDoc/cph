package com.pranicdoc.cph.analytics.service;

import com.pranicdoc.cph.analytics.dto.DashboardSessionTrend;
import com.pranicdoc.cph.analytics.dto.DashboardSummary;
import com.pranicdoc.cph.common.dto.ApiResponse;

import java.util.List;

public interface DashboardService {
    ApiResponse<DashboardSummary> getSummary();
    ApiResponse<List<DashboardSessionTrend>> getSessionTrends();
}
