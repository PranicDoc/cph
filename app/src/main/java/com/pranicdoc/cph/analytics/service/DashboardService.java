package com.pranicdoc.cph.analytics.service;

import com.pranicdoc.cph.analytics.dto.DashboardCase;
import com.pranicdoc.cph.analytics.dto.DateRangeFilter;
import com.pranicdoc.cph.analytics.dto.DashboardCreditUsage;
import com.pranicdoc.cph.analytics.dto.DashboardFollowup;
import com.pranicdoc.cph.analytics.dto.DashboardPendingReport;
import com.pranicdoc.cph.analytics.dto.DashboardUpcomingSession;
import com.pranicdoc.cph.analytics.dto.DashboardPatientGrowth;
import com.pranicdoc.cph.analytics.dto.DashboardTopProtocol;
import com.pranicdoc.cph.analytics.dto.DashboardTopSymptom;
import com.pranicdoc.cph.analytics.dto.DashboardSessionTrend;
import com.pranicdoc.cph.analytics.dto.DashboardSummary;
import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface DashboardService {
    ApiResponse<DashboardSummary> getSummary(UUID healerId);
    ApiResponse<List<DashboardSessionTrend>> getSessionTrends(UUID healerId, DateRangeFilter filter);
    ApiResponse<List<DashboardPatientGrowth>> getPatientGrowth(UUID healerId, DateRangeFilter filter);
    ApiResponse<List<DashboardTopSymptom>> getTopSymptoms(UUID healerId, DateRangeFilter filter);
    ApiResponse<List<DashboardTopProtocol>> getTopProtocols(UUID healerId, DateRangeFilter filter);
    ApiResponse<List<DashboardUpcomingSession>> getUpcomingSessions(UUID healerId);
    ApiResponse<List<DashboardPendingReport>> getPendingReports(UUID healerId);
    PageResponse<DashboardCase> getCases(UUID healerId, String q, Pageable pageable);
    ApiResponse<List<DashboardFollowup>> getFollowups(UUID healerId);
    ApiResponse<List<DashboardCreditUsage>> getCreditUsage(UUID healerId, DateRangeFilter filter);
}