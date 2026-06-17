package com.pranicdoc.cph.analytics.controller;

import com.pranicdoc.cph.analytics.dto.DashboardCreditUsage;
import com.pranicdoc.cph.analytics.dto.DateRangeFilter;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * MockMvc tests for DashboardController GET /api/v1/dashboard/credit-usage.
 * Tests: 401 unauthenticated, 200 authenticated, 200 with ?period=30d param.
 */
@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, DateRangeResolver.class})
class DashboardControllerCreditUsageTest {

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

    private final List<DashboardCreditUsage> sampleData = List.of(
            new DashboardCreditUsage(LocalDate.now().minusDays(1), 15L),
            new DashboardCreditUsage(LocalDate.now(), 0L)
    );

    @BeforeEach
    void setUp() throws Exception {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(tenantInterceptor.preHandle(any(HttpServletRequest.class), any(HttpServletResponse.class), any()))
                .thenReturn(true);
        when(dashboardService.getCreditUsage(any(UUID.class), any(DateRangeFilter.class)))
                .thenReturn(ApiResponse.of(sampleData));
    }

    @Test
    void getCreditUsage_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/credit-usage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getCreditUsage_authenticated_returns200WithData() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/credit-usage").param("period", "30d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser
    void getCreditUsage_withPeriodParam_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/credit-usage").param("period", "7d"))
                .andExpect(status().isOk());
    }
}
