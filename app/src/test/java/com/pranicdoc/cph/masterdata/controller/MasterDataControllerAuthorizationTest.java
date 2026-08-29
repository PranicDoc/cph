package com.pranicdoc.cph.masterdata.controller;

import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.security.OAuth2AuthenticationSuccessHandler;
import com.pranicdoc.cph.common.security.SecurityConfig;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.tenant.TenantInterceptor;
import com.pranicdoc.cph.masterdata.service.MasterDataService;
import com.pranicdoc.cph.masterdata.service.ProtocolImportService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Request-level authorization suite for {@link MasterDataController} — the SC2
 * half of the phase's largest converted surface (the reflection/contract half is
 * {@code MasterDataControllerAuthorizationContractTest}, plan 40-05).
 *
 * <p>Built on the PROVEN {@code @WebMvcTest(controller) + @Import(SecurityConfig)}
 * harness from {@code 40-HARNESS-DECISION.md} (spike:
 * {@code RbacMethodSecuritySliceContractTest}, {@code 9174e6e}) — the slice
 * evaluates {@code @PreAuthorize} at request time and resolves the
 * {@code @rbacAuthz} bean call through the real Spring Security filter chain.
 * Every legacy controller test in this repo uses {@code standaloneSetup} where
 * {@code @PreAuthorize} is inert and a 403 assertion would pass for the wrong
 * reason; this class is the real-filter-chain evidence D-12 requires.
 *
 * <p><b>Harness-assembly notes (per 40-HARNESS-DECISION.md, applied here):</b>
 * <ul>
 *   <li>{@code RbacAuthz} is contributed from a nested {@code @TestConfiguration}
 *       {@code @Bean} <em>named</em> {@code rbacAuthz} — NOT
 *       {@code @Import(RbacAuthz.class)}, which registers it under its FQN and
 *       breaks the {@code @rbacAuthz} SpEL reference.</li>
 *   <li>{@code HealerCacheService} and {@code OAuth2AuthenticationSuccessHandler}
 *       are {@code @MockBean}'d because the {@code SecurityConfig} slice context
 *       fails to load without them; {@code @ActiveProfiles("test")} supplies the
 *       dummy OAuth2 client registration.</li>
 *   <li>{@code @WithMockUser} does not survive {@code JwtAuthFilter}
 *       ({@code clearContext()} when no token cookie is present); callers are
 *       authenticated by stubbing {@link JwtService} to mint a valid
 *       {@code ACCESS} token and putting an {@code accessToken} cookie on the
 *       request.</li>
 * </ul>
 *
 * <p><b>Task 1</b> — {@code supadmReachesEveryConvertedEndpoint} sweeps all 21
 * concrete requests asserting "not 403" plus {@code verifyNoInteractions(lookupService)};
 * {@code newlyGatedReadEndpointsDenyAnUngrantedCaller} /
 * {@code ...AdmitAGrantedCaller} prove the D-09 read-leak closure endpoint by
 * endpoint (the 8 GETs that carried no {@code @PreAuthorize} before this phase);
 * {@code resourceIsolation_*} and {@code verbIsolation_*} catch the copy-paste
 * error a 21-site conversion is most likely to produce.
 *
 * <p><b>Task 2</b> — the D-04 master-data system-row override matrix, asserted
 * via {@code ArgumentCaptor} on the actual {@code isSuperAdmin} boolean the
 * controller passes to {@link MasterDataService} (not a status code): SUPADM
 * still receives it on all three resources; a non-SUPADM caller never does
 * (PD-40-04); a gate-admitted-but-service-refused request returns 403, not 500.
 */
@WebMvcTest(MasterDataController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class,
        MasterDataControllerAuthorizationTest.RbacAuthzSliceConfig.class})
@ActiveProfiles("test")
class MasterDataControllerAuthorizationTest {

    /**
     * Contributes the real {@link RbacAuthz} enforcement primitive under the bean
     * name {@code rbacAuthz} that {@code @PreAuthorize} SpEL references.
     * {@code @Import(RbacAuthz.class)} would name it by FQN and the lookup would
     * fail — see the class javadoc.
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

    // ─── per-controller collaborators ───────────────────────────────────────
    @MockBean
    private MasterDataService masterDataService;
    @MockBean
    private ProtocolImportService protocolImportService;

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
    private static final String BASE = "/api/v1/masterdata";

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
     * Builds a byte-minimal request for the given method + path. POST/PUT bodies
     * are {@code "{}"} (they may fail bean-validation downstream with 400 — that
     * is fine, every Task-1 assertion is "not 403" / "isForbidden"). "MULTIPART"
     * is the {@code POST /protocols/import} case with an empty part named "file".
     */
    private static MockHttpServletRequestBuilder request(String method, String path) {
        MockHttpServletRequestBuilder b = switch (method) {
            case "GET" -> get(path);
            case "POST" -> post(path).contentType(MediaType.APPLICATION_JSON).content("{}");
            case "PUT" -> put(path).contentType(MediaType.APPLICATION_JSON).content("{}");
            case "DELETE" -> delete(path);
            case "MULTIPART" -> multipart(path)
                    .file(new MockMultipartFile("file", "protocols.csv", "text/csv", new byte[0]));
            default -> throw new IllegalArgumentException("unsupported method " + method);
        };
        return b.cookie(accessCookie());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Task 1 — SUPADM sweep + D-09 deny/admit + resource/verb isolation
    // ═══════════════════════════════════════════════════════════════════════

    /** All 21 concrete requests on the converted surface, {uuid} -> {@link #ROW_ID}. */
    static Stream<Arguments> allTwentyOneEndpoints() {
        return Stream.of(
                Arguments.of("GET", BASE + "/chakras"),
                Arguments.of("GET", BASE + "/chakras/" + ROW_ID),
                Arguments.of("GET", BASE + "/chakras/code/HEART"),
                Arguments.of("POST", BASE + "/chakras"),
                Arguments.of("PUT", BASE + "/chakras/" + ROW_ID),
                Arguments.of("DELETE", BASE + "/chakras/" + ROW_ID),
                Arguments.of("GET", BASE + "/colors"),
                Arguments.of("GET", BASE + "/colors/" + ROW_ID),
                Arguments.of("GET", BASE + "/colors/code/GREEN"),
                Arguments.of("POST", BASE + "/colors"),
                Arguments.of("PUT", BASE + "/colors/" + ROW_ID),
                Arguments.of("DELETE", BASE + "/colors/" + ROW_ID),
                Arguments.of("GET", BASE + "/protocol-mappings"),
                Arguments.of("POST", BASE + "/protocol-mappings"),
                Arguments.of("DELETE", BASE + "/protocol-mappings/" + ROW_ID),
                Arguments.of("GET", BASE + "/protocols"),
                Arguments.of("GET", BASE + "/protocols/" + ROW_ID),
                Arguments.of("POST", BASE + "/protocols"),
                Arguments.of("PUT", BASE + "/protocols/" + ROW_ID),
                Arguments.of("DELETE", BASE + "/protocols/" + ROW_ID),
                Arguments.of("MULTIPART", BASE + "/protocols/import"));
    }

    @Test
    void task1SweepCoversExactly21Endpoints() {
        assertThat(allTwentyOneEndpoints().count())
                .as("the SUPADM sweep must supply exactly the 21 converted MasterDataController requests")
                .isEqualTo(21L);
    }

    @ParameterizedTest(name = "SUPADM {0} {1} is not 403")
    @MethodSource("allTwentyOneEndpoints")
    void supadmReachesEveryConvertedEndpoint(String method, String path) throws Exception {
        authenticateAs("SUPADM");
        when(securityContextUtil.getCurrentRole()).thenReturn("SUPADM");

        mockMvc.perform(request(method, path))
                .andExpect(status().is(not(403)));

        // RBAC-04: the SUPADM short-circuit in RbacAuthz returns true before any
        // grant lookup runs — for the @PreAuthorize gate AND the in-body D-04 flag.
        verifyNoInteractions(lookupService);
    }

    /**
     * Exactly the 8 GET paths that carried no {@code @PreAuthorize} before this
     * phase (D-09). {@code /protocol-mappings} is excluded — it was already gated
     * on {@code hasAnyRole('ADMIN','SUPADM')} and was a conversion, not a new gate.
     */
    static Stream<Arguments> eightNewlyGatedGetPaths() {
        return Stream.of(
                Arguments.of("GET", BASE + "/chakras"),
                Arguments.of("GET", BASE + "/chakras/" + ROW_ID),
                Arguments.of("GET", BASE + "/chakras/code/HEART"),
                Arguments.of("GET", BASE + "/colors"),
                Arguments.of("GET", BASE + "/colors/" + ROW_ID),
                Arguments.of("GET", BASE + "/colors/code/GREEN"),
                Arguments.of("GET", BASE + "/protocols"),
                Arguments.of("GET", BASE + "/protocols/" + ROW_ID));
    }

    @Test
    void task2SweepCoversExactly8NewlyGatedGetPaths() {
        assertThat(eightNewlyGatedGetPaths().count())
                .as("the D-09 deny/admit sweeps must cover exactly the 8 previously-ungated GET paths")
                .isEqualTo(8L);
        assertThat(eightNewlyGatedGetPaths().allMatch(a -> "GET".equals(a.get()[0])))
                .as("all 8 D-09 paths are GET reads")
                .isTrue();
    }

    @ParameterizedTest(name = "ungranted HEALER {0} {1} is 403")
    @MethodSource("eightNewlyGatedGetPaths")
    void newlyGatedReadEndpointsDenyAnUngrantedCaller(String method, String path) throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        // Differs from newlyGatedReadEndpointsAdmitAGrantedCaller ONLY in this boolean.
        when(lookupService.hasPermission(any(), any(), eq(Operation.READ))).thenReturn(false);

        mockMvc.perform(request(method, path))
                .andExpect(status().isForbidden());

        // Direct proof the @rbacAuthz bean method executed — a slice that ignored
        // @PreAuthorize would never reach this call.
        verify(lookupService).hasPermission(any(), any(), eq(Operation.READ));
    }

    @ParameterizedTest(name = "granted HEALER {0} {1} is not 403")
    @MethodSource("eightNewlyGatedGetPaths")
    void newlyGatedReadEndpointsAdmitAGrantedCaller(String method, String path) throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        // Differs from newlyGatedReadEndpointsDenyAnUngrantedCaller ONLY in this boolean.
        when(lookupService.hasPermission(any(), any(), eq(Operation.READ))).thenReturn(true);

        mockMvc.perform(request(method, path))
                .andExpect(status().is(not(403)));
    }

    @Test
    void resourceIsolation_chakraGrantDoesNotUnlockColors() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), eq(Resource.MASTER_DATA_CHAKRAS), any())).thenReturn(true);
        when(lookupService.hasPermission(any(), eq(Resource.MASTER_DATA_COLORS), any())).thenReturn(false);

        mockMvc.perform(get(BASE + "/chakras").cookie(accessCookie()))
                .andExpect(status().is(not(403)));
        mockMvc.perform(get(BASE + "/colors").cookie(accessCookie()))
                .andExpect(status().isForbidden());
    }

    @Test
    void verbIsolation_readGrantDoesNotUnlockDelete() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), eq(Resource.MASTER_DATA_PROTOCOLS), eq(Operation.READ)))
                .thenReturn(true);
        when(lookupService.hasPermission(any(), eq(Resource.MASTER_DATA_PROTOCOLS), eq(Operation.DELETE)))
                .thenReturn(false);

        mockMvc.perform(get(BASE + "/protocols").cookie(accessCookie()))
                .andExpect(status().is(not(403)));
        mockMvc.perform(delete(BASE + "/protocols/" + ROW_ID).cookie(accessCookie()))
                .andExpect(status().isForbidden());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Task 2 — the D-04 master-data system-row override matrix (PD-40-04)
    // ═══════════════════════════════════════════════════════════════════════

    // ─── Test A — SUPADM still receives the override on all 3 resources ─────

    @Test
    void supadmStillReceivesTheSystemRowOverride_chakraDelete() throws Exception {
        authenticateAs("SUPADM");
        when(securityContextUtil.getCurrentRole()).thenReturn("SUPADM");

        mockMvc.perform(delete(BASE + "/chakras/" + ROW_ID).cookie(accessCookie()))
                .andExpect(status().is(not(403)));

        var captor = forClass(Boolean.class);
        verify(masterDataService).deleteChakra(eq(ROW_ID), captor.capture());
        assertThat(captor.getValue())
                .as("SUPADM still receives the chakra system-row override after the D-04 flag swap")
                .isTrue();
    }

    @Test
    void supadmStillReceivesTheSystemRowOverride_colorUpdate() throws Exception {
        authenticateAs("SUPADM");
        when(securityContextUtil.getCurrentRole()).thenReturn("SUPADM");
        when(masterDataService.updateColor(any(), any(), anyBoolean())).thenReturn(null);

        mockMvc.perform(put(BASE + "/colors/" + ROW_ID)
                        .cookie(accessCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Green\",\"code\":\"GREEN\",\"colorHex\":\"#0f0\"}"))
                .andExpect(status().is(not(403)));

        var captor = forClass(Boolean.class);
        verify(masterDataService).updateColor(eq(ROW_ID), any(), captor.capture());
        assertThat(captor.getValue())
                .as("SUPADM still receives the colour system-row override after the D-04 flag swap")
                .isTrue();
    }

    @Test
    void supadmStillReceivesTheSystemRowOverride_protocolDelete() throws Exception {
        authenticateAs("SUPADM");
        when(securityContextUtil.getCurrentRole()).thenReturn("SUPADM");

        mockMvc.perform(delete(BASE + "/protocols/" + ROW_ID).cookie(accessCookie()))
                .andExpect(status().is(not(403)));

        var captor = forClass(Boolean.class);
        verify(masterDataService).deleteProtocol(eq(ROW_ID), captor.capture());
        assertThat(captor.getValue())
                .as("SUPADM still receives the protocol system-row override after the D-04 flag swap")
                .isTrue();
    }

    // ─── Test B — non-SUPADM override behaviour, captured as an executable fact ──

    /**
     * The plan's Task-2 Test B verbatim: blanket {@code hasPermission(any(),any(),any()) -> true}.
     * The request passes the {@code @PreAuthorize} gate (MASTER_DATA_CHAKRAS:DELETE
     * is "granted") — but the captured {@code isSuperAdmin} argument is asserted
     * against its ACTUALLY OBSERVED value, whatever that is.
     *
     * <p>OBSERVED: with the blanket stub the request-time override resolves
     * {@code true} for a non-SUPADM caller, because {@code RbacAuthz.hasPermission("MASTER_DATA_CHAKRAS","ADMIN")}
     * for a non-SUPADM principal falls through to {@code lookupService.hasPermission(healerId, MASTER_DATA_CHAKRAS, ADMIN)},
     * which the blanket stub answers {@code true}. This is a Mockito artifact,
     * NOT a privilege expansion: {@code RbacAuthz} does not itself refuse the
     * {@code MASTER_DATA_*:ADMIN} pair at request time. Non-grantability is
     * enforced UPSTREAM — {@code Permission}'s constructor rejects the
     * {@code (MASTER_DATA_*, ADMIN)} pair at role-creation time, so
     * {@code RolePermissionLookupService} reading real {@code role_permission}
     * rows can never return {@code true} for it. The realistic-grant counterpart
     * {@link #nonSupadmWithEveryAllowListedVerb_neverReceivesTheOverride()} is
     * the executable statement of production behaviour. Recorded as a security
     * note for plan 40-09 (PD-40-04).
     */
    @Test
    void nonSupadmWithBlanketGrantStub_overrideResolvesTrue_isAMockArtifactNotAPrivilegeExpansion() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), any(), any())).thenReturn(true);

        mockMvc.perform(delete(BASE + "/chakras/" + ROW_ID).cookie(accessCookie()))
                .andExpect(status().is(not(403)));

        var captor = forClass(Boolean.class);
        verify(masterDataService).deleteChakra(eq(ROW_ID), captor.capture());
        assertThat(captor.getValue())
                .as("PD-40-04: RbacAuthz does NOT refuse MASTER_DATA_*:ADMIN at request time — with a "
                        + "blanket lookupService stub the override resolves TRUE for a non-SUPADM caller. "
                        + "This is a Mockito artifact: non-grantability is enforced upstream by Permission's "
                        + "constructor rejecting the (MASTER_DATA_*, ADMIN) pair at role-creation time, so a "
                        + "real role_permission read can never carry it. See "
                        + "nonSupadmWithEveryAllowListedVerb_neverReceivesTheOverride() and plan 40-09.")
                .isTrue();
    }

    /**
     * The must-have truth: "the master-data system-row override resolves true
     * only for SUPADM, even for a caller holding every allow-listed
     * {@code MASTER_DATA_*} verb". Stubs {@code READ/CREATE/UPDATE/DELETE} on
     * {@code MASTER_DATA_CHAKRAS} true — every GRANTABLE verb — and leaves
     * {@code ADMIN} unstubbed ({@code -> false}), exactly as a real grant can
     * never carry it (PD-40-04).
     */
    @Test
    void nonSupadmWithEveryAllowListedVerb_neverReceivesTheOverride() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), eq(Resource.MASTER_DATA_CHAKRAS), eq(Operation.READ)))
                .thenReturn(true);
        when(lookupService.hasPermission(any(), eq(Resource.MASTER_DATA_CHAKRAS), eq(Operation.CREATE)))
                .thenReturn(true);
        when(lookupService.hasPermission(any(), eq(Resource.MASTER_DATA_CHAKRAS), eq(Operation.UPDATE)))
                .thenReturn(true);
        when(lookupService.hasPermission(any(), eq(Resource.MASTER_DATA_CHAKRAS), eq(Operation.DELETE)))
                .thenReturn(true);

        mockMvc.perform(delete(BASE + "/chakras/" + ROW_ID).cookie(accessCookie()))
                .andExpect(status().is(not(403)));

        var captor = forClass(Boolean.class);
        verify(masterDataService).deleteChakra(eq(ROW_ID), captor.capture());
        assertThat(captor.getValue())
                .as("PD-40-04: the master-data system-row override is SUPADM-only-forever. A non-SUPADM "
                        + "caller holding every GRANTABLE MASTER_DATA_CHAKRAS verb still does not receive it, "
                        + "because MASTER_DATA_* does not allow-list ADMIN. Anyone who later adds "
                        + "Operation.ADMIN to a MASTER_DATA_* constant must confront this assertion.")
                .isFalse();
    }

    // ─── Test C — gate admits, service refuses the system row -> 403 not 500 ──

    @Test
    void overrideDeniedRowStillReturns403() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        // Gate admits: MASTER_DATA_CHAKRAS:DELETE granted.
        when(lookupService.hasPermission(any(), eq(Resource.MASTER_DATA_CHAKRAS), eq(Operation.DELETE)))
                .thenReturn(true);
        // ...but the row is system-seeded and the caller is not SUPADM (isSuperAdmin=false),
        // so the service refuses exactly as it does for a SYSTEM catalog row.
        doThrow(new AccessDeniedException("System chakras can only be deleted by SUPADM"))
                .when(masterDataService).deleteChakra(eq(ROW_ID), eq(false));

        mockMvc.perform(delete(BASE + "/chakras/" + ROW_ID).cookie(accessCookie()))
                .andExpect(status().isForbidden());
    }
}
