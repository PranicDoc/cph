package com.pranicdoc.cph.healing.controller;

import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.security.OAuth2AuthenticationSuccessHandler;
import com.pranicdoc.cph.common.security.SecurityConfig;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.tenant.TenantInterceptor;
import com.pranicdoc.cph.healing.service.impl.ProtocolService;
import com.pranicdoc.cph.healing.service.impl.ScanService;
import com.pranicdoc.cph.rbac.RbacAuthz;
import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Request-level authorization suite for the two one-method healing controllers
 * {@link ScanController} and {@link ProtocolController} — the SC3 half of plan 42-05 (the
 * reflection/contract half is {@code ScanControllerAuthorizationContractTest} /
 * {@code ProtocolControllerAuthorizationContractTest}).
 *
 * <p>Built on the PROVEN {@code @WebMvcTest(controllers) + @Import(SecurityConfig)} harness
 * from {@code 40-HARNESS-DECISION.md} (spike {@code RbacMethodSecuritySliceContractTest},
 * {@code 9174e6e}) — the slice evaluates {@code @PreAuthorize} at request time and resolves
 * the {@code @rbacAuthz} bean call through the real Spring Security filter chain.
 * {@code @WebMvcTest} accepts an array of controllers, so one slice hosts both.
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
 *   <li>{@code @WithMockUser} does not survive {@code JwtAuthFilter}; callers are
 *       authenticated by stubbing {@link JwtService} to mint a valid {@code ACCESS} token
 *       and putting an {@code accessToken} cookie on the request.</li>
 * </ul>
 *
 * <p><b>Depth (D-10 — small single-resource controllers):</b> the full SUPADM / granted /
 * ungranted trio per gated endpoint (2 endpoints x 3 = 6 requests), plus one
 * resource-isolation test proving a {@code SCAN:CREATE} grant does not open the protocol
 * endpoint — the two new single-verb resources are genuinely separate grants.
 *
 * <p>Assertions are <b>behaviour only</b> (HTTP status + mock interaction); the annotation
 * literals are pinned by the Task 2 contract tests, so this class stays green even if the
 * literal strings are later reformatted.
 */
@WebMvcTest({ScanController.class, ProtocolController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class,
        ScanAndProtocolControllerAuthorizationTest.RbacAuthzSliceConfig.class})
@ActiveProfiles("test")
class ScanAndProtocolControllerAuthorizationTest {

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

    // ─── per-controller collaborators ─────────────────────────────────────
    @MockBean
    private ScanService scanService;
    @MockBean
    private ProtocolService protocolService;

    // ─── consumed by RbacAuthz ───────────────────────────────────────────
    @MockBean
    private SecurityContextUtil securityContextUtil;
    @MockBean
    private RolePermissionLookupService lookupService;

    // ─── always required by the SecurityConfig slice context ─────────────
    @MockBean
    private JwtService jwtService;
    @MockBean
    private TenantInterceptor tenantInterceptor;
    @MockBean
    private HealerCacheService healerCacheService;
    @MockBean
    private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    private static final UUID HEALER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final String ACCESS_TOKEN = "test-access-token";

    private static final String SCANS_PATH = "/api/v1/sessions/" + SESSION_ID + "/scans";
    private static final String PROTOCOL_PATH = "/api/v1/sessions/" + SESSION_ID + "/protocol";

    /** Minimal body that passes {@code @Valid} on ScanRequest, so the granted/SUPADM paths hit a clean 201. */
    private static final String SCAN_BODY = """
            {"scanType":"PRE","readings":[{"chakraId":"00000000-0000-0000-0000-0000000000aa","chakraName":"HEART"}]}
            """;

    /** ProtocolController takes a List; no field is @NotNull, so a single empty element is valid. */
    private static final String PROTOCOL_BODY = "[{}]";

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
    //  POST /api/v1/sessions/{sessionId}/scans — gated SCAN:CREATE
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void scan_supadm_isNotForbiddenAndSkipsGrantLookup() throws Exception {
        authenticateAs("SUPADM");
        when(securityContextUtil.getCurrentRole()).thenReturn("SUPADM");

        mockMvc.perform(post(SCANS_PATH)
                        .contentType(MediaType.APPLICATION_JSON).content(SCAN_BODY)
                        .cookie(accessCookie()))
                .andExpect(status().is(not(403)));

        // RBAC-04: the SUPADM short-circuit in RbacAuthz returns true before any grant lookup.
        verifyNoInteractions(lookupService);
    }

    @Test
    void scan_grantedHealer_isNotForbidden() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        // Differs from scan_ungrantedHealer_isForbidden ONLY in this boolean.
        when(lookupService.hasPermission(any(), eq(Resource.SCAN), eq(Operation.CREATE))).thenReturn(true);

        mockMvc.perform(post(SCANS_PATH)
                        .contentType(MediaType.APPLICATION_JSON).content(SCAN_BODY)
                        .cookie(accessCookie()))
                .andExpect(status().is(not(403)));
    }

    @Test
    void scan_ungrantedHealer_isForbidden() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        // Byte-identical to scan_grantedHealer_isNotForbidden except this boolean.
        when(lookupService.hasPermission(any(), eq(Resource.SCAN), eq(Operation.CREATE))).thenReturn(false);

        mockMvc.perform(post(SCANS_PATH)
                        .contentType(MediaType.APPLICATION_JSON).content(SCAN_BODY)
                        .cookie(accessCookie()))
                .andExpect(status().isForbidden());

        // Direct proof the @rbacAuthz bean method executed — a slice that ignored
        // @PreAuthorize would never reach this call.
        verify(lookupService).hasPermission(any(), eq(Resource.SCAN), eq(Operation.CREATE));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  POST /api/v1/sessions/{sessionId}/protocol — gated SESSION_PROTOCOL:CREATE
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void protocol_supadm_isNotForbiddenAndSkipsGrantLookup() throws Exception {
        authenticateAs("SUPADM");
        when(securityContextUtil.getCurrentRole()).thenReturn("SUPADM");

        mockMvc.perform(post(PROTOCOL_PATH)
                        .contentType(MediaType.APPLICATION_JSON).content(PROTOCOL_BODY)
                        .cookie(accessCookie()))
                .andExpect(status().is(not(403)));

        verifyNoInteractions(lookupService);
    }

    @Test
    void protocol_grantedHealer_isNotForbidden() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        // Differs from protocol_ungrantedHealer_isForbidden ONLY in this boolean.
        when(lookupService.hasPermission(any(), eq(Resource.SESSION_PROTOCOL), eq(Operation.CREATE)))
                .thenReturn(true);

        mockMvc.perform(post(PROTOCOL_PATH)
                        .contentType(MediaType.APPLICATION_JSON).content(PROTOCOL_BODY)
                        .cookie(accessCookie()))
                .andExpect(status().is(not(403)));
    }

    @Test
    void protocol_ungrantedHealer_isForbidden() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        // Byte-identical to protocol_grantedHealer_isNotForbidden except this boolean.
        when(lookupService.hasPermission(any(), eq(Resource.SESSION_PROTOCOL), eq(Operation.CREATE)))
                .thenReturn(false);

        mockMvc.perform(post(PROTOCOL_PATH)
                        .contentType(MediaType.APPLICATION_JSON).content(PROTOCOL_BODY)
                        .cookie(accessCookie()))
                .andExpect(status().isForbidden());

        verify(lookupService).hasPermission(any(), eq(Resource.SESSION_PROTOCOL), eq(Operation.CREATE));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  resource isolation — a SCAN:CREATE grant does not open the protocol endpoint
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void resourceIsolation_scanCreateGrantDoesNotUnlockProtocol() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        // Granted SCAN:CREATE and nothing else — every other pair resolves false.
        when(lookupService.hasPermission(any(), eq(Resource.SCAN), eq(Operation.CREATE))).thenReturn(true);
        when(lookupService.hasPermission(any(), eq(Resource.SESSION_PROTOCOL), eq(Operation.CREATE)))
                .thenReturn(false);

        mockMvc.perform(post(SCANS_PATH)
                        .contentType(MediaType.APPLICATION_JSON).content(SCAN_BODY)
                        .cookie(accessCookie()))
                .andExpect(status().is(not(403)));

        mockMvc.perform(post(PROTOCOL_PATH)
                        .contentType(MediaType.APPLICATION_JSON).content(PROTOCOL_BODY)
                        .cookie(accessCookie()))
                .andExpect(status().isForbidden());

        verify(lookupService).hasPermission(any(), eq(Resource.SESSION_PROTOCOL), eq(Operation.CREATE));
    }
}
