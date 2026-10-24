package com.pranicdoc.cph.payments;

import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.security.OAuth2AuthenticationSuccessHandler;
import com.pranicdoc.cph.common.security.SecurityConfig;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.tenant.TenantInterceptor;
import com.pranicdoc.cph.payments.service.impl.CreditServiceImpl;
import com.pranicdoc.cph.rbac.RbacAuthz;
import com.pranicdoc.cph.rbac.service.RolePermissionLookupService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc proxy-behaviour tests for {@link CreditProxyController}.
 *
 * <p>Purpose (unchanged by plan 42-06's repair): the controller forwards {@code GET /packages}
 * and {@code POST /purchase} through {@link CreditServiceImpl} to creditService and returns the
 * upstream status/body, without exposing the server-side {@code X-Api-Key} to the browser;
 * unauthenticated calls are rejected before the handler runs.
 *
 * <p><b>D-14 repair.</b> This class was one of the 13 red slices in the Phase 42 baseline —
 * {@code IllegalStateException: Failed to load ApplicationContext}. It is rebuilt on the PROVEN
 * {@code @WebMvcTest + @Import(SecurityConfig)} harness from {@code 40-HARNESS-DECISION.md}:
 * {@code @ActiveProfiles("test")} for the dummy OAuth2 client registration, the always-required
 * {@code HealerCacheService} / {@code OAuth2AuthenticationSuccessHandler} mocks, and — because
 * {@code CreditProxyController} is now capability-gated (Task 1) — the {@code rbacAuthz} SpEL bean
 * plus {@code SecurityContextUtil} / {@code RolePermissionLookupService}. {@code @WithMockUser}
 * (silently wiped by {@code JwtAuthFilter}, which calls {@code clearContext()} when there is no
 * {@code accessToken} cookie) is replaced by real cookie authentication; every behavioural test
 * authenticates as SUPADM so it reaches the handler through the {@link RbacAuthz} short-circuit —
 * authorization itself is covered by {@code CreditProxyControllerAuthorizationTest}.
 */
@WebMvcTest(CreditProxyController.class)
@Import({SecurityConfig.class, CreditProxyControllerTest.RestClientTestConfig.class,
        CreditProxyControllerTest.RbacAuthzTestConfig.class, CreditServiceImpl.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "app.enable.credit-service=true")
class CreditProxyControllerTest {

    /** Shared static mock — @TestConfiguration provides it as "creditServiceRestClient" bean. */
    static final RestClient CREDIT_SERVICE_REST_CLIENT = mock(RestClient.class);

    @TestConfiguration
    static class RestClientTestConfig {
        @Bean("creditServiceRestClient")
        RestClient creditServiceRestClient() {
            return CREDIT_SERVICE_REST_CLIENT;
        }
    }

    @TestConfiguration
    static class RbacAuthzTestConfig {
        @Bean   // MUST be named 'rbacAuthz' — this is the @PreAuthorize SpEL reference
        RbacAuthz rbacAuthz(SecurityContextUtil securityContextUtil,
                            RolePermissionLookupService lookupService) {
            return new RbacAuthz(securityContextUtil, lookupService);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private com.pranicdoc.cph.payments.service.CreditGateService creditGateService;

    @MockBean
    private TenantInterceptor tenantInterceptor;

    @MockBean
    private HealerCacheService healerCacheService;

    @MockBean
    private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    @MockBean
    private SecurityContextUtil securityContextUtil;

    @MockBean
    private RolePermissionLookupService lookupService;

    // CreditServiceImpl collaborators — the slice does not scan @Service / repositories,
    // so both must be @MockBean'd for the context to load (D-14 assembly fact 1).
    @MockBean
    private CreditServiceClient creditServiceClient;

    @MockBean
    private HealerRepository healerRepository;

    private static final UUID HEALER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ACCESS_TOKEN = "test-access-token";

    @BeforeEach
    void setUp() {
        // Behavioural tests are about proxy forwarding, not authorization: authenticate as SUPADM
        // so RbacAuthz short-circuits to true and every request reaches the handler.
        lenient().when(securityContextUtil.getCurrentRole()).thenReturn("SUPADM");
        lenient().when(creditServiceClient.getPackages(any(), any())).thenReturn("[{\"name\":\"FREE\",\"credits\":10}]");
        lenient().when(creditServiceClient.purchase(any(), any()))
                .thenReturn("{\"purchase_reference\":\"uuid\",\"credits_added\":50}");
    }

    private void authenticateAsSupadm() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(HEALER_ID.toString());
        when(claims.get("role", String.class)).thenReturn("SUPADM");
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

    @Test
    void getPackages_returns200WithPackageList() throws Exception {
        authenticateAsSupadm();

        mockMvc.perform(get("/api/credits/packages")
                        .accept(MediaType.APPLICATION_JSON)
                        .cookie(accessCookie()))
                .andExpect(status().isOk());
    }

    @Test
    void purchase_returns200WithPurchaseResponse() throws Exception {
        authenticateAsSupadm();
        String requestBody = "{\"package_id\":\"uuid\",\"owner_type\":\"HEALER\",\"owner_id\":\"uuid\",\"idempotency_key\":\"key\"}";

        mockMvc.perform(post("/api/credits/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .cookie(accessCookie()))
                .andExpect(status().isOk());
    }

    @Test
    void getPackages_unauthenticated_returns4xx() throws Exception {
        // Without an accessToken cookie, JwtAuthFilter clears the context and
        // .anyRequest().authenticated() rejects the request before the handler runs.
        mockMvc.perform(get("/api/credits/packages"))
                .andExpect(status().is4xxClientError());
    }
}
