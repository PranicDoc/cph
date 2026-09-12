package com.pranicdoc.cph.masterdata.controller;

import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.enums.MasterdataType;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.security.OAuth2AuthenticationSuccessHandler;
import com.pranicdoc.cph.common.security.SecurityConfig;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.tenant.TenantInterceptor;
import com.pranicdoc.cph.masterdata.dto.ColumnConfigResponse;
import com.pranicdoc.cph.masterdata.model.ColumnDataType;
import com.pranicdoc.cph.masterdata.service.MasterdataColumnConfigService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization contract for {@link MasterdataColumnConfigController}
 * (Phase 01, masterdata-import-redesign, MDIMPORT-01/02/03/06).
 *
 * <p><b>D-10 (critical):</b> all 4 verbs on {@code MASTER_DATA_COLUMN_CONFIG}
 * are normal grantable RBAC permissions, each handler gated on its own
 * matching verb — no shared/blanket {@code ADMIN}-gated handler exists on
 * this controller. This is the opposite shape from {@code
 * MasterDataController.updateChakra}'s non-grantable-override idiom.
 *
 * <p><b>Section A</b> pins each of the 4 endpoint {@code @PreAuthorize}
 * literals.
 *
 * <p><b>Section B</b> is the anti-vacuous sweep: exactly 4 request-mapped
 * methods, each a bare {@code @rbacAuthz.hasPermission('R','V')} gate, no
 * residual {@code hasRole}/{@code hasAnyRole}.
 *
 * <p><b>Section C</b> resolves every named (resource, verb) pair through
 * {@link Resource#valueOf} + {@link Operation#valueOf} + {@link
 * Resource#supports}, and asserts {@code
 * MASTER_DATA_COLUMN_CONFIG.supports(ADMIN)} is {@code false} — the D-10
 * regression guard: this controller must never gain a 5th
 * {@code ADMIN}-gated handler.
 *
 * <p>The sibling {@link FilterChainMasterdataColumnConfigControllerAuthorizationTest}
 * proves the per-verb filter-chain matrix through the real Spring Security
 * filter chain.
 */
class MasterdataColumnConfigControllerAuthorizationTest {

    // ─── Section A — the 4 pinned literals ────────────────────────────────

    static final String READ_LITERAL   = "@rbacAuthz.hasPermission('MASTER_DATA_COLUMN_CONFIG','READ')";
    static final String CREATE_LITERAL = "@rbacAuthz.hasPermission('MASTER_DATA_COLUMN_CONFIG','CREATE')";
    static final String UPDATE_LITERAL = "@rbacAuthz.hasPermission('MASTER_DATA_COLUMN_CONFIG','UPDATE')";
    static final String DELETE_LITERAL = "@rbacAuthz.hasPermission('MASTER_DATA_COLUMN_CONFIG','DELETE')";

    /** methodName -> expected @PreAuthorize literal. */
    private static final List<String[]> EXPECTED_GATES = List.of(
            new String[]{"list", READ_LITERAL},
            new String[]{"create", CREATE_LITERAL},
            new String[]{"update", UPDATE_LITERAL},
            new String[]{"remove", DELETE_LITERAL});

    private static String preAuthorizeValueOf(Class<?> controller, String methodName) {
        Method match = null;
        for (Method m : controller.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers()) || !m.getName().equals(methodName)) {
                continue;
            }
            if (match != null) {
                return fail("method %s on %s is ambiguous (overloaded) — cannot pin its @PreAuthorize",
                        methodName, controller.getSimpleName());
            }
            match = m;
        }
        if (match == null) {
            return fail("no public method named %s on %s", methodName, controller.getSimpleName());
        }
        PreAuthorize preAuthorize = match.getAnnotation(PreAuthorize.class);
        if (preAuthorize == null) {
            return fail("method %s on %s carries no @PreAuthorize", methodName, controller.getSimpleName());
        }
        return preAuthorize.value();
    }

    @Test
    void list_isGatedByRead() {
        assertThat(preAuthorizeValueOf(MasterdataColumnConfigController.class, "list"))
                .isEqualTo(READ_LITERAL);
    }

    @Test
    void create_isGatedByCreate() {
        assertThat(preAuthorizeValueOf(MasterdataColumnConfigController.class, "create"))
                .isEqualTo(CREATE_LITERAL);
    }

    @Test
    void update_isGatedByUpdate() {
        assertThat(preAuthorizeValueOf(MasterdataColumnConfigController.class, "update"))
                .isEqualTo(UPDATE_LITERAL);
    }

    @Test
    void remove_isGatedByDelete() {
        assertThat(preAuthorizeValueOf(MasterdataColumnConfigController.class, "remove"))
                .isEqualTo(DELETE_LITERAL);
    }

    @Test
    void noHandlerIsNamedOrGatedOnAdmin() {
        for (String[] entry : EXPECTED_GATES) {
            assertThat(entry[1]).doesNotContain("'ADMIN'");
        }
    }

    // ─── Section B — anti-vacuous sweep ───────────────────────────────────

    private static final List<Class<? extends Annotation>> REQUEST_MAPPING_ANNOTATIONS = List.of(
            GetMapping.class, PostMapping.class, PutMapping.class, PatchMapping.class,
            DeleteMapping.class, RequestMapping.class);

    private static final Pattern BARE_GATE =
            Pattern.compile("^@rbacAuthz\\.hasPermission\\('([A-Z_]+)','([A-Z]+)'\\)$");

    @Test
    void everyRequestMappedMethodCarriesABareDynamicGate() {
        int inspected = 0;

        for (Method method : MasterdataColumnConfigController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            boolean isRequestMapped = REQUEST_MAPPING_ANNOTATIONS.stream()
                    .anyMatch(annotationType -> method.getAnnotation(annotationType) != null);
            if (!isRequestMapped) {
                continue;
            }

            inspected++;

            PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
            assertThat(preAuthorize)
                    .as("method %s must carry @PreAuthorize", method.getName())
                    .isNotNull();

            String value = preAuthorize.value();
            assertThat(value)
                    .as("method %s's @PreAuthorize must be a bare @rbacAuthz.hasPermission('R','V') expression",
                            method.getName())
                    .matches(BARE_GATE);
            assertThat(value)
                    .as("method %s's @PreAuthorize must not retain a hasRole gate", method.getName())
                    .doesNotContain("hasRole");
            assertThat(value)
                    .as("method %s's @PreAuthorize must not retain a hasAnyRole gate", method.getName())
                    .doesNotContain("hasAnyRole");
        }

        assertThat(inspected)
                .as("the sweep must inspect exactly the 4 declared MasterdataColumnConfigController endpoints")
                .isEqualTo(4);
    }

    // ─── Section C — allow-list validity ─────────────────────────────────

    @Test
    void everyGateNamesAnAllowListedResourceVerbPair() {
        for (String[] entry : EXPECTED_GATES) {
            String methodName = entry[0];
            String literal = preAuthorizeValueOf(MasterdataColumnConfigController.class, methodName);

            Matcher matcher = BARE_GATE.matcher(literal);
            assertThat(matcher.matches())
                    .as("gate for %s (%s) must be a bare @rbacAuthz.hasPermission('R','V') expression",
                            methodName, literal)
                    .isTrue();

            Resource resource = Resource.valueOf(matcher.group(1));
            Operation operation = Operation.valueOf(matcher.group(2));

            assertThat(resource.supports(operation))
                    .as("%s: %s:%s is not an allow-listed (resource, verb) pair (D-10)",
                            methodName, resource, operation)
                    .isTrue();
        }
    }

    @Test
    void masterDataColumnConfigDoesNotSupportAdminVerb_regressionGuardForD10() {
        assertThat(Resource.MASTER_DATA_COLUMN_CONFIG.supports(Operation.ADMIN))
                .as("D-10: MASTER_DATA_COLUMN_CONFIG must never allow-list ADMIN — this controller must never "
                        + "gain a 5th ADMIN-gated handler; all 4 verbs (READ/CREATE/UPDATE/DELETE) are ordinary "
                        + "grantable permissions")
                .isFalse();
    }
}

/**
 * SC2 contribution — the per-verb filter-chain matrix, run through the real
 * Spring Security filter chain on the PROVEN {@code
 * @WebMvcTest(controller) + @Import(SecurityConfig)} harness (see {@code
 * MasterDataControllerAuthorizationTest} / {@code
 * FilterChainConsentTypeControllerAuthorizationTest}).
 *
 * <p>Sibling top-level class (not {@code @Nested}) so the annotation-contract
 * sections stay context-free. Both class names end with {@code
 * MasterdataColumnConfigControllerAuthorizationTest}, so {@code --tests
 * "*MasterdataColumnConfigControllerAuthorizationTest*"} runs both.
 *
 * <ol>
 *   <li>SUPADM passes all 4 endpoints without any {@code lookupService}
 *       interaction (the {@code RbacAuthz} short-circuit fires first).</li>
 *   <li>A caller granted exactly the matching verb passes that endpoint.</li>
 *   <li>The same caller with only a DIFFERENT one of the 4 verbs granted is
 *       403 on this endpoint (D-10: no permanently-non-grantable verb
 *       exists on this resource — each verb is independent).</li>
 *   <li>A fully-ungranted, non-SUPADM caller is 403 on all 4 endpoints with
 *       zero {@code masterdataColumnConfigService} interaction.</li>
 * </ol>
 */
@WebMvcTest(MasterdataColumnConfigController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class,
        FilterChainMasterdataColumnConfigControllerAuthorizationTest.RbacAuthzSliceConfig.class})
@ActiveProfiles("test")
class FilterChainMasterdataColumnConfigControllerAuthorizationTest {

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

    // ─── per-controller collaborator ─────────────────────────────────────
    @MockBean
    private MasterdataColumnConfigService masterdataColumnConfigService;

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
    private static final UUID ROW_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String BASE = "/api/v1/masterdata/column-config";

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

    private static ColumnConfigResponse sampleResponse() {
        return new ColumnConfigResponse(
                UUID.randomUUID(), MasterdataType.PROTOCOL, "Note", "note", false,
                ColumnDataType.STRING, false, Instant.now());
    }

    private static final String CREATE_BODY =
            "{\"masterdataType\":\"PROTOCOL\",\"columnName\":\"Note\",\"targetField\":\"note\","
                    + "\"required\":false,\"dataType\":\"STRING\"}";
    private static final String UPDATE_BODY =
            "{\"columnName\":\"Note\",\"targetField\":\"note\",\"required\":false,\"dataType\":\"STRING\"}";

    /** The 4 (method, path, body, gate-verb) tuples under test. */
    static Stream<Arguments> allFourEndpoints() {
        return Stream.of(
                Arguments.of("GET", BASE + "?type=PROTOCOL", null, Operation.READ),
                Arguments.of("POST", BASE, CREATE_BODY, Operation.CREATE),
                Arguments.of("PUT", BASE + "/" + ROW_ID, UPDATE_BODY, Operation.UPDATE),
                Arguments.of("DELETE", BASE + "/" + ROW_ID, null, Operation.DELETE));
    }

    private static MockHttpServletRequestBuilder request(String method, String path, String body) {
        MockHttpServletRequestBuilder b = switch (method) {
            case "GET" -> get(path);
            case "POST" -> post(path);
            case "PUT" -> put(path);
            case "DELETE" -> delete(path);
            default -> throw new IllegalArgumentException("unsupported method " + method);
        };
        b.cookie(accessCookie());
        if (body != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return b;
    }

    private void stubServiceHappyPath() {
        when(masterdataColumnConfigService.list(any())).thenReturn(List.of(sampleResponse()));
        when(masterdataColumnConfigService.create(any())).thenReturn(sampleResponse());
        when(masterdataColumnConfigService.update(any(), any())).thenReturn(sampleResponse());
    }

    // ─── (1) SUPADM sweep — all 4 pass without any grant lookup ──────────

    @Test
    void task1SweepCoversExactlyFourEndpoints() {
        assertThat(allFourEndpoints().count())
                .as("the sweep must supply exactly the 4 column-config endpoints")
                .isEqualTo(4L);
    }

    @ParameterizedTest(name = "SUPADM {0} {1} is not 403")
    @MethodSource("allFourEndpoints")
    void supadm_reachesEveryEndpoint_withoutAnyGrantLookup(String method, String path, String body, Operation verb) throws Exception {
        authenticateAs("SUPADM");
        when(securityContextUtil.getCurrentRole()).thenReturn("SUPADM");
        stubServiceHappyPath();

        mockMvc.perform(request(method, path, body))
                .andExpect(status().is(not(403)));

        verifyNoInteractions(lookupService);
    }

    // ─── (2) caller granted exactly the matching verb passes that endpoint ──

    @ParameterizedTest(name = "granted-{3} caller on its own endpoint {0} {1} is not 403")
    @MethodSource("allFourEndpoints")
    void grantedMatchingVerb_passesItsOwnEndpoint(String method, String path, String body, Operation verb) throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), eq(Resource.MASTER_DATA_COLUMN_CONFIG), eq(verb)))
                .thenReturn(true);
        stubServiceHappyPath();

        mockMvc.perform(request(method, path, body))
                .andExpect(status().is(not(403)));
    }

    // ─── (3) caller granted only a DIFFERENT one of the 4 verbs is 403 here ──

    @ParameterizedTest(name = "granted-only-{0} caller on {2} {3} (needs {4}) is 403")
    @MethodSource("crossVerbDenialMatrix")
    void grantedOnlyADifferentVerb_is403OnThisEndpoint(
            Operation grantedVerb, Operation neededVerb, String method, String path, String body) throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), eq(Resource.MASTER_DATA_COLUMN_CONFIG), eq(grantedVerb)))
                .thenReturn(true);
        // neededVerb intentionally left unstubbed -> defaults to false.

        mockMvc.perform(request(method, path, body))
                .andExpect(status().isForbidden());
    }

    static Stream<Arguments> crossVerbDenialMatrix() {
        List<Object[]> endpoints = List.of(
                new Object[]{Operation.READ, "GET", BASE + "?type=PROTOCOL", null},
                new Object[]{Operation.CREATE, "POST", BASE, CREATE_BODY},
                new Object[]{Operation.UPDATE, "PUT", BASE + "/" + ROW_ID, UPDATE_BODY},
                new Object[]{Operation.DELETE, "DELETE", BASE + "/" + ROW_ID, null});

        return endpoints.stream().flatMap(endpoint -> {
            Operation neededVerb = (Operation) endpoint[0];
            String method = (String) endpoint[1];
            String path = (String) endpoint[2];
            String body = (String) endpoint[3];
            return List.of(Operation.READ, Operation.CREATE, Operation.UPDATE, Operation.DELETE).stream()
                    .filter(grantedVerb -> grantedVerb != neededVerb)
                    .map(grantedVerb -> Arguments.of(grantedVerb, neededVerb, method, path, body));
        });
    }

    // ─── (4) fully-ungranted caller — 403 on all 4, zero service interaction ──

    @ParameterizedTest(name = "ungranted HEALER {0} {1} is 403")
    @MethodSource("allFourEndpoints")
    void ungrantedCaller_is403OnEveryEndpoint_andServiceNeverInvoked(String method, String path, String body, Operation verb) throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), eq(Resource.MASTER_DATA_COLUMN_CONFIG), any()))
                .thenReturn(false);

        mockMvc.perform(request(method, path, body))
                .andExpect(status().isForbidden());

        verifyNoInteractions(masterdataColumnConfigService);
    }
}
