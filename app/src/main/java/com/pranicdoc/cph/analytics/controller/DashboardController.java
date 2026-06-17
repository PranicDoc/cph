package com.pranicdoc.cph.analytics.controller;

import com.pranicdoc.cph.analytics.dto.DateRangeFilter;
import com.pranicdoc.cph.analytics.dto.DashboardFollowup;
import com.pranicdoc.cph.analytics.dto.DashboardPendingReport;
import com.pranicdoc.cph.analytics.dto.DashboardUpcomingSession;
import com.pranicdoc.cph.analytics.dto.DashboardPatientGrowth;
import com.pranicdoc.cph.analytics.dto.DashboardTopProtocol;
import com.pranicdoc.cph.analytics.dto.DashboardTopSymptom;
import com.pranicdoc.cph.analytics.dto.DashboardSessionTrend;
import com.pranicdoc.cph.analytics.dto.DashboardSummary;
import com.pranicdoc.cph.analytics.service.DashboardService;
import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dashboard/")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final SecurityContextUtil securityContextUtil;

    @GetMapping("summary")
    public ResponseEntity<ApiResponse<DashboardSummary>> getDashboardSummary() {
        UUID healerId = securityContextUtil.getCurrentHealerId();
        return ResponseEntity.ok(dashboardService.getSummary(healerId));
    }

    @GetMapping("session-trends")
    public ResponseEntity<ApiResponse<List<DashboardSessionTrend>>> getSessionTrends(
            @ModelAttribute DateRangeFilter filter) {
        UUID healerId = securityContextUtil.getCurrentHealerId();
        return ResponseEntity.ok(dashboardService.getSessionTrends(healerId, filter));
    }

    @GetMapping("patient-growth")
    public ResponseEntity<ApiResponse<List<DashboardPatientGrowth>>> getPatientGrowth(
            @ModelAttribute DateRangeFilter filter) {
        UUID healerId = securityContextUtil.getCurrentHealerId();
        return ResponseEntity.ok(dashboardService.getPatientGrowth(healerId, filter));
    }

    @GetMapping("top-symptoms")
    public ResponseEntity<ApiResponse<List<DashboardTopSymptom>>> getTopSymptoms(
            @ModelAttribute DateRangeFilter filter) {
        UUID healerId = securityContextUtil.getCurrentHealerId();
        return ResponseEntity.ok(dashboardService.getTopSymptoms(healerId, filter));
    }

    @GetMapping("top-protocols")
    public ResponseEntity<ApiResponse<List<DashboardTopProtocol>>> getTopProtocols(
            @ModelAttribute DateRangeFilter filter) {
        UUID healerId = securityContextUtil.getCurrentHealerId();
        return ResponseEntity.ok(dashboardService.getTopProtocols(healerId, filter));
    }

    @GetMapping("upcoming-sessions")
    public ResponseEntity<ApiResponse<List<DashboardUpcomingSession>>> getUpcomingSessions() {
        UUID healerId = securityContextUtil.getCurrentHealerId();
        return ResponseEntity.ok(dashboardService.getUpcomingSessions(healerId));
    }

    @GetMapping("pending-reports")
    public ResponseEntity<ApiResponse<List<DashboardPendingReport>>> getPendingReports() {
        UUID healerId = securityContextUtil.getCurrentHealerId();
        return ResponseEntity.ok(dashboardService.getPendingReports(healerId));
    }

    @GetMapping("followups")
    public ResponseEntity<ApiResponse<List<DashboardFollowup>>> getFollowups() {
        UUID healerId = securityContextUtil.getCurrentHealerId();
        return ResponseEntity.ok(dashboardService.getFollowups(healerId));
    }
}