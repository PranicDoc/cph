package com.pranicdoc.cph.analytics.controller;

import com.pranicdoc.cph.analytics.dto.DashboardFollowup;
import com.pranicdoc.cph.analytics.dto.DashboardPendingReport;
import com.pranicdoc.cph.analytics.dto.DashboardUpcomingSession;
import com.pranicdoc.cph.analytics.service.DashboardService;
import com.pranicdoc.cph.analytics.service.DateRangeResolver;
import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.security.SecurityConfig;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.tenant.TenantInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, DateRangeResolver.class})
class DashboardControllerWorkQueueTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private SecurityContextUtil securityContextUtil;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private TenantInterceptor tenantInterceptor;

    private static final UUID HEALER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
    }

    // --- upcoming-sessions ---

    @Test
    void getUpcomingSessions_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/upcoming-sessions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getUpcomingSessions_authenticated_returns200() throws Exception {
        when(dashboardService.getUpcomingSessions(any(UUID.class)))
                .thenReturn(ApiResponse.of(List.of(
                        new DashboardUpcomingSession(UUID.randomUUID(), "Jane", "Doe",
                                LocalDate.now(), null, "DRAFT_SESSION"))));

        mockMvc.perform(get("/api/v1/dashboard/upcoming-sessions"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void getUpcomingSessions_emptyList_returns200() throws Exception {
        when(dashboardService.getUpcomingSessions(any(UUID.class)))
                .thenReturn(ApiResponse.of(List.of()));

        mockMvc.perform(get("/api/v1/dashboard/upcoming-sessions"))
                .andExpect(status().isOk());
    }

    // --- pending-reports ---

    @Test
    void getPendingReports_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/pending-reports"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getPendingReports_authenticated_returns200() throws Exception {
        when(dashboardService.getPendingReports(any(UUID.class)))
                .thenReturn(ApiResponse.of(List.of(
                        new DashboardPendingReport(UUID.randomUUID(), "Alice", "Smith",
                                LocalDate.now().minusDays(5)))));

        mockMvc.perform(get("/api/v1/dashboard/pending-reports"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void getPendingReports_emptyList_returns200() throws Exception {
        when(dashboardService.getPendingReports(any(UUID.class)))
                .thenReturn(ApiResponse.of(List.of()));

        mockMvc.perform(get("/api/v1/dashboard/pending-reports"))
                .andExpect(status().isOk());
    }

    // --- followups ---

    @Test
    void getFollowups_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/followups"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getFollowups_authenticated_returns200() throws Exception {
        when(dashboardService.getFollowups(any(UUID.class)))
                .thenReturn(ApiResponse.of(List.of(
                        new DashboardFollowup(UUID.randomUUID(), "Bob", "Jones",
                                "test-token-abc", Instant.now().minusSeconds(90_000), true))));

        mockMvc.perform(get("/api/v1/dashboard/followups"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void getFollowups_emptyList_returns200() throws Exception {
        when(dashboardService.getFollowups(any(UUID.class)))
                .thenReturn(ApiResponse.of(List.of()));

        mockMvc.perform(get("/api/v1/dashboard/followups"))
                .andExpect(status().isOk());
    }
}
