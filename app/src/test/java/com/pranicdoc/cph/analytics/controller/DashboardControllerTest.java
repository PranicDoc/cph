package com.pranicdoc.cph.analytics.controller;

import com.pranicdoc.cph.analytics.service.DashboardService;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.security.SecurityConfig;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.tenant.TenantInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for DashboardController.
 *
 * Uses @WebMvcTest + @Import(SecurityConfig.class) pattern, mirroring CreditProxyControllerTest.
 * The JWT filter chain is active via SecurityConfig; unauthenticated requests get 401.
 *
 * Note: @WebMvcTest loads the web layer only (no Spring Boot app context). SecurityConfig
 * is imported explicitly to enable JWT filter chain without full @SpringBootTest.
 * @Cacheable is bypassed in this test slice (no Spring AOP proxy for the service mock).
 */
@WebMvcTest(DashboardController.class)
@Import(SecurityConfig.class)
class DashboardControllerTest {

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

    // Test 5: Unauthenticated request to GET /api/v1/dashboard/summary is blocked (401 or 403)
    // Note: Spring Security returns 403 for unauthenticated requests in this JWT stateless config
    // (no custom AuthenticationEntryPoint). The invariant is that unauthenticated access is
    // blocked — matching the pattern established in CreditProxyControllerTest.
    @Test
    void getDashboardSummary_unauthenticated_returns4xxClientError() throws Exception {
        // No Authorization header — JWT filter chain blocks the request
        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().is4xxClientError());
    }
}
