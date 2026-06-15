package com.pranicdoc.cph.analytics.service;

import com.pranicdoc.cph.analytics.dto.DashboardSessionTrends;
import com.pranicdoc.cph.analytics.dto.DashboardSummary;
import com.pranicdoc.cph.common.dto.ApiResponse;

public interface DashboardService {
    ApiResponse<DashboardSummary> getSummary();
    ApiResponse<DashboardSessionTrends> getSessionTrends();
}
