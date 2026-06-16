package com.pranicdoc.cph.analytics.controller;

import com.pranicdoc.cph.analytics.dto.DateRangeFilter;
import com.pranicdoc.cph.analytics.dto.DashboardPatientGrowth;
import com.pranicdoc.cph.analytics.dto.DashboardSessionTrend;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for DashboardController trend endpoints: session-trends and patient-growth.
 *
 * Uses @WebMvcTest + @Import(SecurityConfig.class, GlobalExceptionHandler.class, DateRangeResolver.class)
 * pattern. The real DateRangeResolver is loaded so that an invalid period triggers a real
 * IllegalArgumentException that GlobalExceptionHandler maps to HTTP 400.
 *
 * Auth approach: @WithMockUser bypasses the JWT filter for authenticated tests.
 * Unauthenticated tests return 401 from the stateless JWT security config.
 *
 * Note: DashboardService is mocked. DateRangeResolver is real (loaded via @Import).
 */
@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, DateRangeResolver.class})
class DashboardControllerTrendsTest {

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
        // Stub securityContextUtil for all @WithMockUser tests
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
    }

    // Test 1: GET /api/v1/dashboard/session-trends?period=30d with valid auth -> HTTP 200
    @Test
    @WithMockUser
    void getSessionTrends_authenticated_returns200() throws Exception {
        when(dashboardService.getSessionTrends(any(UUID.class), any(DateRangeFilter.class)))
                .thenReturn(ApiResponse.of(List.of()));

        mockMvc.perform(get("/api/v1/dashboard/session-trends").param("period", "30d"))
                .andExpect(status().isOk());
    }

    // Test 2: GET /api/v1/dashboard/session-trends?period=invalid with valid auth -> HTTP 400
    // Uses real DateRangeResolver (loaded via @Import) which throws IllegalArgumentException
    // for invalid period values. GlobalExceptionHandler maps it to 400.
    @Test
    @WithMockUser
    void getSessionTrends_invalidPeriod_returns400() throws Exception {
        // DashboardService.getSessionTrends() with real DateRangeResolver:
        // The controller calls dashboardService.getSessionTrends(healerId, filter).
        // DashboardServiceImpl (NOT loaded here) would call dateRangeResolver.resolve(filter).
        // Since DashboardService is mocked, DateRangeResolver is not invoked by the mock.
        //
        // To trigger a 400 from the controller layer, we configure the DashboardService mock
        // to invoke the real DateRangeResolver via thenAnswer:
        DateRangeResolver resolver = new DateRangeResolver();
        when(dashboardService.getSessionTrends(any(), any()))
                .thenAnswer(inv -> {
                    DateRangeFilter f = inv.getArgument(1);
                    resolver.resolve(f); // throws IllegalArgumentException for invalid period
                    return ApiResponse.of(List.of());
                });

        mockMvc.perform(get("/api/v1/dashboard/session-trends").param("period", "invalid"))
                .andExpect(status().isBadRequest());
    }

    // Test 3: GET /api/v1/dashboard/patient-growth?period=7d with valid auth -> HTTP 200
    @Test
    @WithMockUser
    void getPatientGrowth_authenticated_returns200() throws Exception {
        when(dashboardService.getPatientGrowth(any(UUID.class), any(DateRangeFilter.class)))
                .thenReturn(ApiResponse.of(List.of()));

        mockMvc.perform(get("/api/v1/dashboard/patient-growth").param("period", "7d"))
                .andExpect(status().isOk());
    }

    // Test 4: GET /api/v1/dashboard/patient-growth with no auth -> HTTP 401
    @Test
    void getPatientGrowth_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/patient-growth"))
                .andExpect(status().isUnauthorized());
    }

    // Test 5: GET /api/v1/dashboard/session-trends with no auth -> HTTP 401
    @Test
    void getSessionTrends_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/session-trends"))
                .andExpect(status().isUnauthorized());
    }
}