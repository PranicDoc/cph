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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
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
 * <p><b>D-10 superseded (2026-09-13):</b> this registry is SUPADM-only.
 * Every handler carries the exact same {@code hasRole('SUPADM')} literal
 * used by {@code BootstrapController.assignRole} — no {@code
 * Resource.MASTER_DATA_COLUMN_CONFIG} exists any more, so there is nothing
 * for a custom role to be granted here.
 *
 * <p><b>Section A</b> pins each of the 4 endpoint {@code @PreAuthorize}
 * literals to the exact {@code hasRole('SUPADM')} string.
 *
 * <p><b>Section B</b> is the anti-vacuous sweep: exactly 4 request-mapped
 * methods, each carrying that exact literal and nothing else.
 *
 * <p>The sibling {@link FilterChainMasterdataColumnConfigControllerAuthorizationTest}
 * proves SUPADM-only enforcement through the real Spring Security filter chain.
 */
class MasterdataColumnConfigControllerAuthorizationTest {

    // ─── Section A — the 4 pinned literals ────────────────────────────────

    /**
     * The exact authorization literal every handler must carry, written out
     * in full — not assembled from a prefix + a role name, so a typo in a
     * shared fragment would not slip through unnoticed. Matches {@code
     * BootstrapControllerTest.ASSIGN_ROLE_LITERAL}.
     */
    static final String SUPADM_LITERAL = "hasRole('SUPADM')";

    /** methodName -> expected @PreAuthorize literal. */
    private static final List<String[]> EXPECTED_GATES = List.of(
            new String[]{"list", SUPADM_LITERAL},
            new String[]{"create", SUPADM_LITERAL},
            new String[]{"update", SUPADM_LITERAL},
            new String[]{"remove", SUPADM_LITERAL});

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
    void list_isGatedBySupadm() {
        assertThat(preAuthorizeValueOf(MasterdataColumnConfigController.class, "list"))
                .isEqualTo(SUPADM_LITERAL);
    }

    @Test
    void create_isGatedBySupadm() {
        assertThat(preAuthorizeValueOf(MasterdataColumnConfigController.class, "create"))
                .isEqualTo(SUPADM_LITERAL);
    }

    @Test
    void update_isGatedBySupadm() {
        assertThat(preAuthorizeValueOf(MasterdataColumnConfigController.class, "update"))
                .isEqualTo(SUPADM_LITERAL);
    }

    @Test
    void remove_isGatedBySupadm() {
        assertThat(preAuthorizeValueOf(MasterdataColumnConfigController.class, "remove"))
                .isEqualTo(SUPADM_LITERAL);
    }

    // ─── Section B — anti-vacuous sweep ───────────────────────────────────

    private static final List<Class<? extends Annotation>> REQUEST_MAPPING_ANNOTATIONS = List.of(
            GetMapping.class, PostMapping.class, PutMapping.class, PatchMapping.class,
            DeleteMapping.class, RequestMapping.class);

    @Test
    void everyRequestMappedMethodCarriesExactlyTheSupadmGate() {
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

            assertThat(preAuthorize.value())
                    .as("method %s's @PreAuthorize must be exactly hasRole('SUPADM') — no resource-based "
                            + "grant exists for this registry any more", method.getName())
                    .isEqualTo(SUPADM_LITERAL);
        }

        assertThat(inspected)
                .as("the sweep must inspect exactly the 4 declared MasterdataColumnConfigController endpoints")
                .isEqualTo(4);
    }

    @Test
    void masterDataColumnConfigResourceNoLongerExists_regressionGuardForD10Reversal() {
        boolean stillDeclared = Arrays.stream(Resource.values())
                .anyMatch(r -> r.name().equals("MASTER_DATA_COLUMN_CONFIG"));
        assertThat(stillDeclared)
                .as("D-10 was superseded 2026-09-13: MASTER_DATA_COLUMN_CONFIG must stay removed from the "
                        + "grantable Resource catalog — this registry is SUPADM-only via hasRole, not a permission")
                .isFalse();
    }
}

/**
 * SC2 contribution — SUPADM-only enforcement proven through the real Spring
 * Security filter chain on the PROVEN {@code
 * @WebMvcTest(controller) + @Import(SecurityConfig)} harness (see {@code
 * MasterDataControllerAuthorizationTest} / {@code
 * FilterChainConsentTypeControllerAuthorizationTest}).
 *
 * <p>Sibling top-level class (not {@code @Nested}) so the annotation-contract
 * sections stay context-free. Both class names end with {@code
 * MasterdataColumnConfigControllerAuthorizationTest}, so {@code --tests
 * "*MasterdataColumnConfigControllerAuthorizationTest*"} runs both.
 *
 * <p><b>D-10 superseded (2026-09-13):</b> there is no more per-verb grant
 * matrix to prove — {@code hasRole('SUPADM')} either matches the caller's
 * role or it doesn't, so {@code RbacAuthz}/{@code lookupService} are never
 * consulted for this controller at all (kept wired only because the shared
 * {@code SecurityConfig} slice's method-security expression handler resolves
 * a {@code @rbacAuthz} bean generically for the whole app context).
 *
 * <ol>
 *   <li>SUPADM passes all 4 endpoints, with zero {@code lookupService}
 *       interaction (Spring Security's native role check never touches
 *       {@code RbacAuthz}).</li>
 *   <li>A non-SUPADM caller (HEALER) is 403 on all 4 endpoints, with zero
 *       {@code masterdataColumnConfigService} interaction — there is no
 *       grant that could ever let a HEALER through any more.</li>
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

    /** The 4 (method, path, body) tuples under test. */
    static Stream<Arguments> allFourEndpoints() {
        return Stream.of(
                Arguments.of("GET", BASE + "?type=PROTOCOL", null),
                Arguments.of("POST", BASE, CREATE_BODY),
                Arguments.of("PUT", BASE + "/" + ROW_ID, UPDATE_BODY),
                Arguments.of("DELETE", BASE + "/" + ROW_ID, null));
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

    // ─── (1) SUPADM sweep — all 4 pass, RbacAuthz/lookupService untouched ──

    @Test
    void task1SweepCoversExactlyFourEndpoints() {
        assertThat(allFourEndpoints().count())
                .as("the sweep must supply exactly the 4 column-config endpoints")
                .isEqualTo(4L);
    }

    @ParameterizedTest(name = "SUPADM {0} {1} is not 403")
    @MethodSource("allFourEndpoints")
    void supadm_reachesEveryEndpoint_withoutAnyRbacLookup(String method, String path, String body) throws Exception {
        authenticateAs("SUPADM");
        stubServiceHappyPath();

        mockMvc.perform(request(method, path, body))
                .andExpect(status().is(not(403)));

        verifyNoInteractions(lookupService);
    }

    // ─── (2) any non-SUPADM caller — 403 on all 4, zero service interaction ──

    @ParameterizedTest(name = "non-SUPADM HEALER {0} {1} is 403")
    @MethodSource("allFourEndpoints")
    void nonSupadmCaller_is403OnEveryEndpoint_andServiceNeverInvoked(String method, String path, String body) throws Exception {
        authenticateAs("HEALER");

        mockMvc.perform(request(method, path, body))
                .andExpect(status().isForbidden());

        verifyNoInteractions(masterdataColumnConfigService);
        verifyNoInteractions(lookupService);
    }
}
