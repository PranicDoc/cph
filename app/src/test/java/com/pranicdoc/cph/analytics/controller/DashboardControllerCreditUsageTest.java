package com.pranicdoc.cph.analytics.controller;

import com.pranicdoc.cph.analytics.dto.DashboardCreditUsage;
import com.pranicdoc.cph.analytics.dto.DateRangeFilter;
import com.pranicdoc.cph.analytics.service.DashboardService;
import com.pranicdoc.cph.analytics.service.DateRangeResolver;
import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.security.OAuth2AuthenticationSuccessHandler;
import com.pranicdoc.cph.common.security.SecurityConfig;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.tenant.TenantInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * MockMvc tests for DashboardController GET /api/v1/dashboard/credit-usage.
 * Tests: 401 unauthenticated, 200 authenticated, 200 with ?period=30d param.
 */
@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, DateRangeResolver.class})
@ActiveProfiles("test")
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

    @MockBean
    private HealerCacheService healerCacheService;

    @MockBean
    private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    private static final UUID HEALER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ACCESS_TOKEN = "test-access-token";

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
    }

    private static Cookie accessCookie() {
        return new Cookie("accessToken", ACCESS_TOKEN);
    }

    @Test
    void getCreditUsage_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/credit-usage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCreditUsage_authenticated_returns200WithData() throws Exception {
        authenticateAs("HEALER");
        mockMvc.perform(get("/api/v1/dashboard/credit-usage").param("period", "30d").cookie(accessCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getCreditUsage_withPeriodParam_returns200() throws Exception {
        authenticateAs("HEALER");
        mockMvc.perform(get("/api/v1/dashboard/credit-usage").param("period", "7d").cookie(accessCookie()))
                .andExpect(status().isOk());
    }
}
