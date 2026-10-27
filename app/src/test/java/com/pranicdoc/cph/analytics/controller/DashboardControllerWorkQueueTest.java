package com.pranicdoc.cph.analytics.controller;

import com.pranicdoc.cph.analytics.dto.DashboardFollowup;
import com.pranicdoc.cph.analytics.dto.DashboardPendingReport;
import com.pranicdoc.cph.analytics.dto.DashboardUpcomingSession;
import com.pranicdoc.cph.analytics.service.DashboardService;
import com.pranicdoc.cph.analytics.service.DateRangeResolver;
import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.common.dto.PageResponse;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.security.OAuth2AuthenticationSuccessHandler;
import com.pranicdoc.cph.common.security.SecurityConfig;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.tenant.TenantInterceptor;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, DateRangeResolver.class})
@ActiveProfiles("test")
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

    @MockBean
    private HealerCacheService healerCacheService;

    @MockBean
    private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    private static final UUID HEALER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ACCESS_TOKEN = "test-access-token";

    @BeforeEach
    void setUp() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
    }

    /**
     * Drives the real {@code JwtAuthFilter} to an authenticated principal.
     * {@code @WithMockUser} does not survive the filter chain — it calls
     * {@code SecurityContextHolder.clearContext()} when no {@code accessToken}
     * cookie is present (40-HARNESS-DECISION.md assembly fact 3).
     */
    private void authenticateAs(String role) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(HEALER_ID.toString());
        when(claims.get("role", String.class)).thenReturn(role);
        when(jwtService.isTokenValid(ACCESS_TOKEN)).thenReturn(true);
        when(jwtService.extractTokenType(ACCESS_TOKEN)).thenReturn("ACCESS");
        when(jwtService.parseToken(ACCESS_TOKEN)).thenReturn(claims);
        when(healerCacheService.isActive(any())).thenReturn(true);
        try {
            when(tenantInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Cookie accessCookie() {
        return new Cookie("accessToken", ACCESS_TOKEN);
    }

    // --- upcoming-sessions ---

    @Test
    void getUpcomingSessions_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/upcoming-sessions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUpcomingSessions_authenticated_returns200() throws Exception {
        authenticateAs("HEALER");
        when(dashboardService.getUpcomingSessions(any(UUID.class)))
                .thenReturn(ApiResponse.of(List.of(
                        new DashboardUpcomingSession(UUID.randomUUID(), "Jane", "Doe",
                                LocalDate.now(), null, "DRAFT_SESSION"))));

        mockMvc.perform(get("/api/v1/dashboard/upcoming-sessions").cookie(accessCookie()))
                .andExpect(status().isOk());
    }

    @Test
    void getUpcomingSessions_emptyList_returns200() throws Exception {
        authenticateAs("HEALER");
        when(dashboardService.getUpcomingSessions(any(UUID.class)))
                .thenReturn(ApiResponse.of(List.of()));

        mockMvc.perform(get("/api/v1/dashboard/upcoming-sessions").cookie(accessCookie()))
                .andExpect(status().isOk());
    }

    // --- pending-reports ---

    @Test
    void getPendingReports_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/pending-reports"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPendingReports_authenticated_returns200() throws Exception {
        authenticateAs("HEALER");
        when(dashboardService.getPendingReports(any(UUID.class)))
                .thenReturn(ApiResponse.of(List.of(
                        new DashboardPendingReport(UUID.randomUUID(), "Alice", "Smith",
                                LocalDate.now().minusDays(5)))));

        mockMvc.perform(get("/api/v1/dashboard/pending-reports").cookie(accessCookie()))
                .andExpect(status().isOk());
    }

    @Test
    void getPendingReports_emptyList_returns200() throws Exception {
        authenticateAs("HEALER");
        when(dashboardService.getPendingReports(any(UUID.class)))
                .thenReturn(ApiResponse.of(List.of()));

        mockMvc.perform(get("/api/v1/dashboard/pending-reports").cookie(accessCookie()))
                .andExpect(status().isOk());
    }

    @Test
    void getPendingReportsPage_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/pending-reports/page"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPendingReportsPage_passesSearchAndTenRowsPerPage() throws Exception {
        authenticateAs("HEALER");
        when(dashboardService.getPendingReportsPage(any(UUID.class), eq("ali"), any(Pageable.class)))
                .thenReturn(new PageResponse<>(
                        List.of(new DashboardPendingReport(UUID.randomUUID(), "Alice", "Smith", LocalDate.now())),
                        0, 10, 1, 1, true));

        mockMvc.perform(get("/api/v1/dashboard/pending-reports/page").param("q", "ali").cookie(accessCookie()))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(dashboardService).getPendingReportsPage(any(UUID.class), eq("ali"), pageable.capture());
        assertEquals(10, pageable.getValue().getPageSize());
    }

    // --- followups ---

    @Test
    void getFollowups_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/followups"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getFollowups_authenticated_returns200() throws Exception {
        authenticateAs("HEALER");
        when(dashboardService.getFollowups(any(UUID.class)))
                .thenReturn(ApiResponse.of(List.of(
                        new DashboardFollowup(UUID.randomUUID(), "Bob", "Jones",
                                "test-token-abc", Instant.now().minusSeconds(90_000), true))));

        mockMvc.perform(get("/api/v1/dashboard/followups").cookie(accessCookie()))
                .andExpect(status().isOk());
    }

    @Test
    void getFollowups_emptyList_returns200() throws Exception {
        authenticateAs("HEALER");
        when(dashboardService.getFollowups(any(UUID.class)))
                .thenReturn(ApiResponse.of(List.of()));

        mockMvc.perform(get("/api/v1/dashboard/followups").cookie(accessCookie()))
                .andExpect(status().isOk());
    }
}
