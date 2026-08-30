package com.pranicdoc.cph.rbac.controller;

import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.dto.PageResponse;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.security.OAuth2AuthenticationSuccessHandler;
import com.pranicdoc.cph.common.security.SecurityConfig;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.tenant.TenantInterceptor;
import com.pranicdoc.cph.rbac.RbacAuthz;
import com.pranicdoc.cph.rbac.dto.RoleSummaryResponse;
import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;
import com.pranicdoc.cph.rbac.service.RbacRoleService;
import com.pranicdoc.cph.rbac.service.RolePermissionLookupService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RbacMethodSecuritySliceContractTest — the standing proof that a
 * {@code @WebMvcTest(controller) + @Import(SecurityConfig)} slice in this
 * repository evaluates {@code @PreAuthorize} method security at request time,
 * resolving the {@code @rbacAuthz} bean call through the real Spring Security
 * filter chain.
 *
 * <p>Phase 40's D-12 demands an automated real-filter-chain authorization gate.
 * Every legacy controller test in this repo uses
 * {@code MockMvcBuilders.standaloneSetup(...)} (no filter chain, so
 * {@code @PreAuthorize} is inert) and {@code @SpringBootTest} full-context tests
 * are broken repo-wide, so before any SC2 allow/deny suite is authored this
 * spike establishes whether the slice harness actually works. Plans
 * 40-02..40-07 read {@code 40-HARNESS-DECISION.md} — written from this test's
 * observed result — to know whether to author filter-chain SC2 tests or the
 * documented {@code checkpoint:human-verify} fallback.
 *
 * <p>VERDICT: PROVEN. All three cases below pass: a 403 when the grant lookup
 * returns {@code false}, a 200 when it returns {@code true} on the identical
 * request, and a 200 with zero {@code lookupService} interaction for a SUPADM
 * caller. The 403 case additionally {@code verify(lookupService)}s that the
 * {@code @rbacAuthz} bean method actually executed — a slice that ignored
 * {@code @PreAuthorize} would never call it and would fail the verification
 * rather than pass silently.
 *
 * <p>Two harness-assembly facts downstream plans MUST copy:
 * <ul>
 *   <li>The slice needs {@code @MockBean}s for {@code JwtService},
 *       {@code TenantInterceptor}, {@code HealerCacheService} and
 *       {@code OAuth2AuthenticationSuccessHandler} or the {@code SecurityConfig}
 *       context fails to load (these are {@code @Service}/{@code @Component}
 *       beans the slice excludes; it is exactly why the older
 *       {@code DashboardController*Test} slices are currently red on this
 *       branch — they never added them).</li>
 *   <li>{@code RbacAuthz} must be contributed as a {@code @Bean} <em>named</em>
 *       {@code rbacAuthz} (see {@link RbacAuthzSliceConfig}). Listing
 *       {@code RbacAuthz.class} in {@code @Import} registers it under its
 *       fully-qualified class name, and the SpEL reference {@code @rbacAuthz}
 *       then fails with {@code NoSuchBeanDefinitionException: No bean named
 *       'rbacAuthz'} — surfaced here as an HTTP 400
 *       "Failed to evaluate expression".</li>
 * </ul>
 *
 * <p>Authentication is established by driving the repo's real
 * {@code JwtAuthFilter}: a stubbed {@link JwtService} mints a valid {@code ACCESS}
 * token carrying the {@code role} claim and an {@code accessToken} cookie is put
 * on the request. {@code @WithMockUser} cannot be used here because
 * {@code JwtAuthFilter} calls {@code SecurityContextHolder.clearContext()}
 * whenever no token cookie is present, which wipes any test-injected principal
 * before authorization runs (observed: 401 on all three cases).
 *
 * <p>The target endpoint carries the bare expression
 * {@code @rbacAuthz.hasPermission('ROLES','READ')} (plan 40-06 harmonization).
 * No coarse role half remains, so the deny/allow pair authenticates as a
 * non-SUPADM {@code HEALER}: the {@code @rbacAuthz} bean call is the only thing
 * that can decide the outcome, and a 403-vs-200 flip on the identical request
 * isolates method security reaching the bean. Phase 41 retired the former
 * non-SUPADM sentinel these tests used; {@code "HEALER"} now expresses "some
 * authenticated non-SUPADM caller" exactly.
 *
 * <p>This class asserts BEHAVIOR only (HTTP status + mock interaction). It never
 * pins a {@code @PreAuthorize} annotation literal, so plan 40-06's harmonization
 * of {@code RbacRoleController} to the bare
 * {@code @PreAuthorize("@rbacAuthz.hasPermission('ROLES','READ')")} form leaves
 * it green.
 */
@WebMvcTest(RbacRoleController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class,
        RbacMethodSecuritySliceContractTest.RbacAuthzSliceConfig.class})
@ActiveProfiles("test")
class RbacMethodSecuritySliceContractTest {

    /**
     * Contributes the real {@link RbacAuthz} enforcement primitive under the
     * bean name {@code rbacAuthz} that {@code @PreAuthorize} SpEL references.
     * {@code @Import(RbacAuthz.class)} would name it by FQN and the SpEL lookup
     * would fail — see the class javadoc.
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

    @MockBean
    private RbacRoleService rbacRoleService;

    @MockBean
    private SecurityContextUtil securityContextUtil;

    @MockBean
    private RolePermissionLookupService lookupService;

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

    /**
     * {@link RbacRoleService#list} returns {@link PageResponse}, not a Spring
     * Data {@code Page} — the plan's stub sketch said {@code Page.empty()}, but
     * the real handler contract is {@code PageResponse}. An empty one lets the
     * handler complete so the allow / SUPADM paths can assert 200.
     */
    private static PageResponse<RoleSummaryResponse> emptyPage() {
        return new PageResponse<>(List.of(), 0, 20, 0L, 0, true);
    }

    /**
     * Makes {@code JwtAuthFilter} authenticate the request as {@code HEALER_ID}
     * with authority {@code ROLE_<role>} and a {@code TenantDetails} carrying the
     * healer id, exactly as a live cookie-auth request would.
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

    @Test
    void sliceEvaluatesPreAuthorize_grantAbsent_returns403() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), eq(Resource.ROLES), eq(Operation.READ)))
                .thenReturn(false);

        mockMvc.perform(get("/api/admin/roles").cookie(accessCookie()))
                .andExpect(status().isForbidden());

        // Direct proof the @rbacAuthz bean method executed: a slice that ignores
        // @PreAuthorize never reaches this call.
        verify(lookupService).hasPermission(any(), eq(Resource.ROLES), eq(Operation.READ));
    }

    @Test
    void sliceEvaluatesPreAuthorize_grantPresent_isNotForbidden() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), eq(Resource.ROLES), eq(Operation.READ)))
                .thenReturn(true);
        when(rbacRoleService.list(any(), any())).thenReturn(emptyPage());

        // Anti-vacuous half: identical request to the deny test, only the grant
        // boolean changed. A 200 here proves the deny test's 403 came from the
        // grant decision, not a slice misconfiguration (missing bean, 401, 404).
        mockMvc.perform(get("/api/admin/roles").cookie(accessCookie()))
                .andExpect(status().isOk());
    }

    @Test
    void sliceEvaluatesPreAuthorize_supadmShortCircuits_isNotForbidden() throws Exception {
        authenticateAs("SUPADM");
        when(securityContextUtil.getCurrentRole()).thenReturn("SUPADM");
        when(rbacRoleService.list(any(), any())).thenReturn(emptyPage());

        mockMvc.perform(get("/api/admin/roles").cookie(accessCookie()))
                .andExpect(status().isOk());

        // RBAC-04 invariant every converted endpoint inherits: the SUPADM
        // short-circuit in RbacAuthz returns true before any DB lookup runs.
        verifyNoInteractions(lookupService);
    }
}
