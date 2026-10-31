package com.pranicdoc.cph.payments;

import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.security.OAuth2AuthenticationSuccessHandler;
import com.pranicdoc.cph.common.security.SecurityConfig;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.tenant.TenantInterceptor;
import com.pranicdoc.cph.payments.dto.AccountResponse;
import com.pranicdoc.cph.payments.service.CreditService;
import com.pranicdoc.cph.rbac.RbacAuthz;
import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;
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
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Request-level authorization suite for {@link CreditProxyController} — the SC3 half of plan
 * 42-06 (the reflection/contract half is {@code CreditProxyControllerAuthorizationContractTest}).
 *
 * <p>Built on the PROVEN {@code @WebMvcTest(controller) + @Import(SecurityConfig)} harness from
 * {@code 40-HARNESS-DECISION.md} (spike {@code RbacMethodSecuritySliceContractTest}, {@code 9174e6e})
 * — the slice evaluates {@code @PreAuthorize} at request time and resolves the {@code @rbacAuthz}
 * bean call through the real Spring Security filter chain.
 *
 * <p><b>Harness-assembly notes (per 40-HARNESS-DECISION.md, applied here):</b>
 * <ul>
 *   <li>{@code RbacAuthz} is contributed from a nested {@code @TestConfiguration} {@code @Bean}
 *       <em>named</em> {@code rbacAuthz} — NOT {@code @Import(RbacAuthz.class)}, which registers it
 *       under its FQN and breaks the {@code @rbacAuthz} SpEL reference (HTTP 400).</li>
 *   <li>{@code HealerCacheService} and {@code OAuth2AuthenticationSuccessHandler} are
 *       {@code @MockBean}'d because the {@code SecurityConfig} slice context fails to load without
 *       them; {@code @ActiveProfiles("test")} supplies the dummy OAuth2 client registration.</li>
 *   <li>{@code @WithMockUser} does not survive {@code JwtAuthFilter}; callers are authenticated by
 *       stubbing {@link JwtService} to mint a valid {@code ACCESS} token and putting an
 *       {@code accessToken} cookie on the request.</li>
 * </ul>
 *
 * <p><b>Depth (D-10):</b> the full SUPADM / granted / ungranted trio per gated endpoint
 * (3 endpoints x 3 = 9 requests) — cheap to cover fully here because the 3 methods use 3
 * <em>different</em> {@code (resource, verb)} pairs — plus a verb-isolation test
 * ({@code CREDIT_ACCOUNTS:READ} does not unlock {@code createAccount}) and a resource-isolation
 * test (a {@code CREDIT_ACCOUNTS} grant does not unlock {@code purchase}).
 *
 * <p>Assertions are <b>behaviour only</b> (HTTP status + mock interaction); the annotation
 * literals are pinned by the contract test, so this class stays green even if the literal
 * strings are later reformatted.
 */
@WebMvcTest(CreditProxyController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class,
        CreditProxyControllerAuthorizationTest.RbacAuthzSliceConfig.class})
@ActiveProfiles("test")
class CreditProxyControllerAuthorizationTest {

    /**
     * Contributes the real {@link RbacAuthz} enforcement primitive under the bean name
     * {@code rbacAuthz} that {@code @PreAuthorize} SpEL references.
     */
    @TestConfiguration
    static class RbacAuthzSliceConfig {
        @Bean
        RbacAuthz rbacAuthz(SecurityContextUtil securityContextUtil,
                            RolePermissionLookupService lookupService) {
            return new RbacAuthz(securityContextUtil, lookupService);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    // ─── controller collaborator ────────────────────────────────────────
    @MockBean
    private CreditService creditService;

    // ─── consumed by RbacAuthz ──────────────────────────────────────────
    @MockBean
    private SecurityContextUtil securityContextUtil;
    @MockBean
    private RolePermissionLookupService lookupService;

    // ─── always required by the SecurityConfig slice context ────────────
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

    private static final String PACKAGES_PATH = "/api/credits/packages";
    private static final String PURCHASE_PATH = "/api/credits/purchase";
    private static final String ACCOUNT_PATH = "/api/credits/account";

    private static final String PURCHASE_BODY = "{\"package_id\":\"p1\"}";

    @BeforeEach
    void stubCreditServiceForCleanTwoXx() {
        // Granted / SUPADM paths must reach a clean 2xx rather than a 500 that would mask a 403.
        lenient().when(creditService.getPackages()).thenReturn(ApiResponse.of("[]"));
        lenient().when(creditService.purchase(any(), any())).thenReturn("{}");
        lenient().when(creditService.createAccount())
                .thenReturn(new AccountResponse(UUID.randomUUID(), "HEALER", HEALER_ID, 0, 0, "FREE", null));
    }

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

    // ═══════════════════════════════════════════════════════════════════════
    //  GET /api/credits/packages — gated CREDIT_ACCOUNTS:READ
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void packages_supadm_isNotForbiddenAndSkipsGrantLookup() throws Exception {
        authenticateAs("SUPADM");
        when(securityContextUtil.getCurrentRole()).thenReturn("SUPADM");

        mockMvc.perform(get(PACKAGES_PATH).cookie(accessCookie()))
                .andExpect(status().is(not(403)));

        verifyNoInteractions(lookupService);
    }

    @Test
    void packages_grantedHealer_isNotForbidden() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        // Differs from packages_ungrantedHealer_isForbidden ONLY in this boolean.
        when(lookupService.hasPermission(any(), eq(Resource.CREDIT_ACCOUNTS), eq(Operation.READ)))
                .thenReturn(true);

        mockMvc.perform(get(PACKAGES_PATH).cookie(accessCookie()))
                .andExpect(status().is(not(403)));
    }

    @Test
    void packages_ungrantedHealer_isForbidden() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        // Byte-identical to packages_grantedHealer_isNotForbidden except this boolean.
        when(lookupService.hasPermission(any(), eq(Resource.CREDIT_ACCOUNTS), eq(Operation.READ)))
                .thenReturn(false);

        mockMvc.perform(get(PACKAGES_PATH).cookie(accessCookie()))
                .andExpect(status().isForbidden());

        verify(lookupService).hasPermission(any(), eq(Resource.CREDIT_ACCOUNTS), eq(Operation.READ));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  POST /api/credits/purchase — gated CREDIT_ACCOUNT_TRANSACTIONS:WRITE
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void purchase_supadm_isNotForbiddenAndSkipsGrantLookup() throws Exception {
        authenticateAs("SUPADM");
        when(securityContextUtil.getCurrentRole()).thenReturn("SUPADM");

        mockMvc.perform(post(PURCHASE_PATH)
                        .header("X-Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON).content(PURCHASE_BODY)
                        .cookie(accessCookie()))
                .andExpect(status().is(not(403)));

        verifyNoInteractions(lookupService);
    }

    @Test
    void purchase_grantedHealer_isNotForbidden() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        // Differs from purchase_ungrantedHealer_isForbidden ONLY in this boolean.
        when(lookupService.hasPermission(any(), eq(Resource.CREDIT_ACCOUNT_TRANSACTIONS), eq(Operation.WRITE)))
                .thenReturn(true);

        mockMvc.perform(post(PURCHASE_PATH)
                        .header("X-Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON).content(PURCHASE_BODY)
                        .cookie(accessCookie()))
                .andExpect(status().is(not(403)));
    }

    @Test
    void purchase_ungrantedHealer_isForbidden() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        // Byte-identical to purchase_grantedHealer_isNotForbidden except this boolean.
        when(lookupService.hasPermission(any(), eq(Resource.CREDIT_ACCOUNT_TRANSACTIONS), eq(Operation.WRITE)))
                .thenReturn(false);

        mockMvc.perform(post(PURCHASE_PATH)
                        .header("X-Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON).content(PURCHASE_BODY)
                        .cookie(accessCookie()))
                .andExpect(status().isForbidden());

        verify(lookupService).hasPermission(any(), eq(Resource.CREDIT_ACCOUNT_TRANSACTIONS), eq(Operation.WRITE));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  POST /api/credits/purchase/orders[/{id}/confirm] — gated CREDIT_ACCOUNT_TRANSACTIONS:WRITE
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void createOrderAndConfirm_ungrantedHealer_areForbidden() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), eq(Resource.CREDIT_ACCOUNT_TRANSACTIONS), eq(Operation.WRITE)))
                .thenReturn(false);

        mockMvc.perform(post(PURCHASE_PATH + "/orders")
                        .header("X-Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON).content(PURCHASE_BODY)
                        .cookie(accessCookie()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(PURCHASE_PATH + "/orders/order_1/confirm")
                        .contentType(MediaType.APPLICATION_JSON).content(PURCHASE_BODY)
                        .cookie(accessCookie()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(creditService);
    }

    @Test
    void createOrderAndConfirm_grantedHealer_areNotForbidden() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), eq(Resource.CREDIT_ACCOUNT_TRANSACTIONS), eq(Operation.WRITE)))
                .thenReturn(true);

        mockMvc.perform(post(PURCHASE_PATH + "/orders")
                        .header("X-Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON).content(PURCHASE_BODY)
                        .cookie(accessCookie()))
                .andExpect(status().is(not(403)));

        mockMvc.perform(post(PURCHASE_PATH + "/orders/order_1/confirm")
                        .contentType(MediaType.APPLICATION_JSON).content(PURCHASE_BODY)
                        .cookie(accessCookie()))
                .andExpect(status().is(not(403)));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  POST /api/credits/account — gated CREDIT_ACCOUNTS:CREATE (D-04)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void createAccount_supadm_isNotForbiddenAndSkipsGrantLookup() throws Exception {
        authenticateAs("SUPADM");
        when(securityContextUtil.getCurrentRole()).thenReturn("SUPADM");

        mockMvc.perform(post(ACCOUNT_PATH).cookie(accessCookie()))
                .andExpect(status().is(not(403)));

        verifyNoInteractions(lookupService);
    }

    @Test
    void createAccount_grantedHealer_isNotForbidden() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        // Differs from createAccount_ungrantedHealer_isForbidden ONLY in this boolean.
        when(lookupService.hasPermission(any(), eq(Resource.CREDIT_ACCOUNTS), eq(Operation.CREATE)))
                .thenReturn(true);

        mockMvc.perform(post(ACCOUNT_PATH).cookie(accessCookie()))
                .andExpect(status().is(not(403)));
    }

    @Test
    void createAccount_ungrantedHealer_isForbidden() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        // Byte-identical to createAccount_grantedHealer_isNotForbidden except this boolean.
        when(lookupService.hasPermission(any(), eq(Resource.CREDIT_ACCOUNTS), eq(Operation.CREATE)))
                .thenReturn(false);

        mockMvc.perform(post(ACCOUNT_PATH).cookie(accessCookie()))
                .andExpect(status().isForbidden());

        verify(lookupService).hasPermission(any(), eq(Resource.CREDIT_ACCOUNTS), eq(Operation.CREATE));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  verb isolation — CREDIT_ACCOUNTS:READ does not confer createAccount
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void verbIsolation_creditAccountsReadGrantDoesNotUnlockCreateAccount() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        // Granted CREDIT_ACCOUNTS:READ and nothing else.
        when(lookupService.hasPermission(any(), eq(Resource.CREDIT_ACCOUNTS), eq(Operation.READ)))
                .thenReturn(true);
        when(lookupService.hasPermission(any(), eq(Resource.CREDIT_ACCOUNTS), eq(Operation.CREATE)))
                .thenReturn(false);

        mockMvc.perform(get(PACKAGES_PATH).cookie(accessCookie()))
                .andExpect(status().is(not(403)));

        mockMvc.perform(post(ACCOUNT_PATH).cookie(accessCookie()))
                .andExpect(status().isForbidden());

        verify(lookupService).hasPermission(any(), eq(Resource.CREDIT_ACCOUNTS), eq(Operation.CREATE));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  resource isolation — a CREDIT_ACCOUNTS grant does not unlock purchase
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void resourceIsolation_creditAccountsGrantDoesNotUnlockPurchase() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        // Granted CREDIT_ACCOUNTS:{READ,CREATE} but NOT CREDIT_ACCOUNT_TRANSACTIONS:WRITE.
        when(lookupService.hasPermission(any(), eq(Resource.CREDIT_ACCOUNTS), eq(Operation.READ)))
                .thenReturn(true);
        when(lookupService.hasPermission(any(), eq(Resource.CREDIT_ACCOUNTS), eq(Operation.CREATE)))
                .thenReturn(true);
        when(lookupService.hasPermission(any(), eq(Resource.CREDIT_ACCOUNT_TRANSACTIONS), eq(Operation.WRITE)))
                .thenReturn(false);

        mockMvc.perform(post(ACCOUNT_PATH).cookie(accessCookie()))
                .andExpect(status().is(not(403)));

        mockMvc.perform(post(PURCHASE_PATH)
                        .header("X-Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON).content(PURCHASE_BODY)
                        .cookie(accessCookie()))
                .andExpect(status().isForbidden());

        verify(lookupService).hasPermission(any(), eq(Resource.CREDIT_ACCOUNT_TRANSACTIONS), eq(Operation.WRITE));
    }
}
