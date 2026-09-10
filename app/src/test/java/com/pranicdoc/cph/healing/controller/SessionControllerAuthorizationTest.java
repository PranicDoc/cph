package com.pranicdoc.cph.healing.controller;

import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.security.OAuth2AuthenticationSuccessHandler;
import com.pranicdoc.cph.common.security.SecurityConfig;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.tenant.TenantInterceptor;
import com.pranicdoc.cph.healing.service.impl.SessionService;
import com.pranicdoc.cph.rbac.RbacAuthz;
import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;
import com.pranicdoc.cph.rbac.service.RolePermissionLookupService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Request-level authorization suite for {@link SessionController} — the SC3 half of the
 * plan (the reflection/contract half is {@code SessionControllerAuthorizationContractTest}).
 *
 * <p>Built on the PROVEN {@code @WebMvcTest(controller) + @Import(SecurityConfig)} harness
 * from {@code 40-HARNESS-DECISION.md} (spike: {@code RbacMethodSecuritySliceContractTest},
 * {@code 9174e6e}) — the slice evaluates {@code @PreAuthorize} at request time and resolves
 * the {@code @rbacAuthz} bean call through the real Spring Security filter chain. Every
 * legacy controller test in this repo uses {@code standaloneSetup} where {@code @PreAuthorize}
 * is inert and a 403 assertion would pass for the wrong reason; this class is the
 * real-filter-chain evidence.
 *
 * <p><b>Harness-assembly notes (per 40-HARNESS-DECISION.md, applied here):</b>
 * <ul>
 *   <li>{@code RbacAuthz} is contributed from a nested {@code @TestConfiguration}
 *       {@code @Bean} <em>named</em> {@code rbacAuthz} — NOT {@code @Import(RbacAuthz.class)},
 *       which registers it under its FQN and breaks the {@code @rbacAuthz} SpEL reference
 *       (HTTP 400).</li>
 *   <li>{@code HealerCacheService} and {@code OAuth2AuthenticationSuccessHandler} are
 *       {@code @MockBean}'d because the {@code SecurityConfig} slice context fails to load
 *       without them; {@code @ActiveProfiles("test")} supplies the dummy OAuth2 client
 *       registration.</li>
 *   <li>{@code @WithMockUser} does not survive {@code JwtAuthFilter}
 *       ({@code clearContext()} when no token cookie is present); callers are authenticated
 *       by stubbing {@link JwtService} to mint a valid {@code ACCESS} token and putting an
 *       {@code accessToken} cookie on the request.</li>
 * </ul>
 *
 * <p><b>Depth (D-10 — small single-resource controller):</b> a parameterized SUPADM sweep
 * over all 5 endpoints asserting not-403 + {@code verifyNoInteractions(lookupService)}
 * (RBAC-04 short-circuit), plus a representative granted/ungranted pair on one READ endpoint
 * ({@code GET /{id}/chakras} — the D-03 mapping) and one write endpoint
 * ({@code PATCH /{id}/complete}), each pair differing ONLY in the {@code lookupService}
 * boolean, plus a verb-isolation test.
 */
@WebMvcTest(SessionController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class,
        SessionControllerAuthorizationTest.RbacAuthzSliceConfig.class})
@ActiveProfiles("test")
class SessionControllerAuthorizationTest {

    /**
     * Contributes the real {@link RbacAuthz} enforcement primitive under the bean name
     * {@code rbacAuthz} that {@code @PreAuthorize} SpEL references. {@code @Import(RbacAuthz.class)}
     * would name it by FQN and the lookup would fail — see the class javadoc.
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

    // ─── per-controller collaborator ───────────────────────────────────────
    @MockBean
    private SessionService sessionService;

    // ─── consumed by RbacAuthz ─────────────────────────────────────────────
    @MockBean
    private SecurityContextUtil securityContextUtil;
    @MockBean
    private RolePermissionLookupService lookupService;

    // ─── always required by the SecurityConfig slice context ───────────────
    @MockBean
    private JwtService jwtService;
    @MockBean
    private TenantInterceptor tenantInterceptor;
    @MockBean
    private HealerCacheService healerCacheService;
    @MockBean
    private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    private static final UUID HEALER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ROW_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String BASE = "/api/v1/sessions";

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

    /**
     * Builds a byte-minimal request for the given method + path. POST/PATCH bodies are
     * {@code "{}"} (they may fail bean-validation downstream with 400 — that is fine, every
     * assertion here is "not 403" / "isForbidden").
     */
    private static MockHttpServletRequestBuilder request(String method, String path) {
        MockHttpServletRequestBuilder b = switch (method) {
            case "GET" -> get(path);
            case "POST" -> post(path).contentType(MediaType.APPLICATION_JSON).content("{}");
            case "PATCH" -> patch(path).contentType(MediaType.APPLICATION_JSON).content("{}");
            default -> throw new IllegalArgumentException("unsupported method " + method);
        };
        return b.cookie(accessCookie());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  (a) SUPADM sweep — all 5 endpoints, zero grant lookups
    // ═══════════════════════════════════════════════════════════════════════

    /** All 5 concrete SessionController requests, {uuid} -> {@link #ROW_ID}. */
    static Stream<Arguments> fiveSessionEndpoints() {
        return Stream.of(
                Arguments.of("POST", BASE + "/case"),
                Arguments.of("GET", BASE + "/" + ROW_ID),
                Arguments.of("GET", BASE + "/case/" + ROW_ID),
                Arguments.of("PATCH", BASE + "/" + ROW_ID + "/complete"),
                Arguments.of("GET", BASE + "/" + ROW_ID + "/chakras"));
    }

    @Test
    void supadmSweepCoversExactly5Endpoints() {
        assertThat(fiveSessionEndpoints().count())
                .as("the SUPADM sweep must supply exactly the 5 SessionController requests — "
                        + "the sweep can never silently shrink")
                .isEqualTo(5L);
    }

    @ParameterizedTest(name = "SUPADM {0} {1} is not 403 and never reads a grant")
    @MethodSource("fiveSessionEndpoints")
    void supadmReachesEverySessionEndpointWithoutAGrantLookup(String method, String path) throws Exception {
        authenticateAs("SUPADM");
        when(securityContextUtil.getCurrentRole()).thenReturn("SUPADM");
        when(sessionService.getAllScannedChakra(any())).thenReturn(Set.of("HEART"));

        mockMvc.perform(request(method, path))
                .andExpect(status().is(not(403)));

        // RBAC-04: the SUPADM short-circuit in RbacAuthz returns true before any grant
        // lookup runs.
        verifyNoInteractions(lookupService);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  (b)/(c) representative granted/ungranted pair — one READ + one write
    // ═══════════════════════════════════════════════════════════════════════

    // ── GET /{id}/chakras — the D-03 mapping, gated SESSION:READ ───────────

    @Test
    void chakras_grantedHealer_isNotForbidden() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(sessionService.getAllScannedChakra(any())).thenReturn(Set.of("HEART"));
        // Differs from chakras_ungrantedHealer_isForbidden ONLY in this boolean.
        when(lookupService.hasPermission(any(), eq(Resource.SESSION),
                eq(Operation.READ))).thenReturn(true);

        mockMvc.perform(get(BASE + "/" + ROW_ID + "/chakras").cookie(accessCookie()))
                .andExpect(status().is(not(403)));
    }

    @Test
    void chakras_ungrantedHealer_isForbidden() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(sessionService.getAllScannedChakra(any())).thenReturn(Set.of("HEART"));
        // Byte-identical to chakras_grantedHealer_isNotForbidden except this boolean.
        when(lookupService.hasPermission(any(), eq(Resource.SESSION),
                eq(Operation.READ))).thenReturn(false);

        mockMvc.perform(get(BASE + "/" + ROW_ID + "/chakras").cookie(accessCookie()))
                .andExpect(status().isForbidden());

        // Direct proof the @rbacAuthz bean method executed — a slice that ignored
        // @PreAuthorize would never reach this call.
        verify(lookupService).hasPermission(any(), eq(Resource.SESSION),
                eq(Operation.READ));
    }

    // ── PATCH /{id}/complete — gated SESSION:UPDATE ───────────────────────

    @Test
    void complete_grantedHealer_isNotForbidden() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(sessionService.complete(any())).thenReturn(null);
        // Differs from complete_ungrantedHealer_isForbidden ONLY in this boolean.
        when(lookupService.hasPermission(any(), eq(Resource.SESSION),
                eq(Operation.UPDATE))).thenReturn(true);

        mockMvc.perform(patch(BASE + "/" + ROW_ID + "/complete").cookie(accessCookie()))
                .andExpect(status().is(not(403)));
    }

    @Test
    void complete_ungrantedHealer_isForbidden() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(sessionService.complete(any())).thenReturn(null);
        // Byte-identical to complete_grantedHealer_isNotForbidden except this boolean.
        when(lookupService.hasPermission(any(), eq(Resource.SESSION),
                eq(Operation.UPDATE))).thenReturn(false);

        mockMvc.perform(patch(BASE + "/" + ROW_ID + "/complete").cookie(accessCookie()))
                .andExpect(status().isForbidden());

        verify(lookupService).hasPermission(any(), eq(Resource.SESSION),
                eq(Operation.UPDATE));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  verb isolation — a READ grant does not unlock the write endpoint
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void verbIsolation_sessionReadGrantDoesNotUnlockComplete() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(sessionService.getAllScannedChakra(any())).thenReturn(Set.of("HEART"));
        when(sessionService.complete(any())).thenReturn(null);
        when(lookupService.hasPermission(any(), eq(Resource.SESSION),
                eq(Operation.READ))).thenReturn(true);
        when(lookupService.hasPermission(any(), eq(Resource.SESSION),
                eq(Operation.UPDATE))).thenReturn(false);

        mockMvc.perform(get(BASE + "/" + ROW_ID + "/chakras").cookie(accessCookie()))
                .andExpect(status().is(not(403)));
        mockMvc.perform(patch(BASE + "/" + ROW_ID + "/complete").cookie(accessCookie()))
                .andExpect(status().isForbidden());
    }
}
