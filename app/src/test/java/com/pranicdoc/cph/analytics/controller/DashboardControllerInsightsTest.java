package com.pranicdoc.cph.analytics.controller;

import com.pranicdoc.cph.analytics.dto.DateRangeFilter;
import com.pranicdoc.cph.analytics.dto.DashboardTopProtocol;
import com.pranicdoc.cph.analytics.dto.DashboardTopSymptom;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for DashboardController insight endpoints: top-symptoms and top-protocols.
 *
 * Uses @WebMvcTest + @Import(SecurityConfig.class, GlobalExceptionHandler.class, DateRangeResolver.class)
 * pattern -- identical to DashboardControllerTrendsTest.
 *
 * Auth approach: @WithMockUser bypasses the JWT filter for authenticated tests.
 * Unauthenticated tests return 401 from the stateless JWT security config.
 */
@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, DateRangeResolver.class})
class DashboardControllerInsightsTest {

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

    // Test 1: GET /api/v1/dashboard/top-symptoms with valid auth -> HTTP 200
    @Test
    @WithMockUser
    void getTopSymptoms_authenticated_returns200() throws Exception {
        when(dashboardService.getTopSymptoms(any(UUID.class), any(DateRangeFilter.class)))
                .thenReturn(ApiResponse.of(List.of(new DashboardTopSymptom("Anxiety", 5L))));

        mockMvc.perform(get("/api/v1/dashboard/top-symptoms").param("period", "30d"))
                .andExpect(status().isOk());
    }

    // Test 2: GET /api/v1/dashboard/top-symptoms with valid auth, empty result -> HTTP 200
    @Test
    @WithMockUser
    void getTopSymptoms_authenticated_emptyResult_returns200() throws Exception {
        when(dashboardService.getTopSymptoms(any(UUID.class), any(DateRangeFilter.class)))
                .thenReturn(ApiResponse.of(List.of()));

        mockMvc.perform(get("/api/v1/dashboard/top-symptoms").param("period", "7d"))
                .andExpect(status().isOk());
    }

    // Test 3: GET /api/v1/dashboard/top-symptoms with no auth -> HTTP 401
    @Test
    void getTopSymptoms_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/top-symptoms"))
                .andExpect(status().isUnauthorized());
    }

    // Test 4: GET /api/v1/dashboard/top-protocols with valid auth -> HTTP 200
    @Test
    @WithMockUser
    void getTopProtocols_authenticated_returns200() throws Exception {
        when(dashboardService.getTopProtocols(any(UUID.class), any(DateRangeFilter.class)))
                .thenReturn(ApiResponse.of(List.of(new DashboardTopProtocol("Basic Pranic Healing", 8L))));

        mockMvc.perform(get("/api/v1/dashboard/top-protocols").param("period", "30d"))
                .andExpect(status().isOk());
    }

    // Test 5: GET /api/v1/dashboard/top-protocols with valid auth, empty result -> HTTP 200
    @Test
    @WithMockUser
    void getTopProtocols_authenticated_emptyResult_returns200() throws Exception {
        when(dashboardService.getTopProtocols(any(UUID.class), any(DateRangeFilter.class)))
                .thenReturn(ApiResponse.of(List.of()));

        mockMvc.perform(get("/api/v1/dashboard/top-protocols").param("period", "7d"))
                .andExpect(status().isOk());
    }

    // Test 6: GET /api/v1/dashboard/top-protocols with no auth -> HTTP 401
    @Test
    void getTopProtocols_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/top-protocols"))
                .andExpect(status().isUnauthorized());
    }
}
