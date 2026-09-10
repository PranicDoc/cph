package com.pranicdoc.cph.auth.controller;

import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.auth.service.HealerService;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.security.OAuth2AuthenticationSuccessHandler;
import com.pranicdoc.cph.common.security.SecurityConfig;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.tenant.TenantInterceptor;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization contract for {@link HealerController} after the Phase 40
 * bare-form cutover (40-03, D-01/D-02).
 *
 * <p><b>Section A</b> pins each of the 3 endpoint {@code @PreAuthorize} literals
 * individually, so a drifted annotation produces a readable expected/actual diff.
 *
 * <p><b>Section B</b> is the anti-vacuous sweep: it counts exactly 3
 * request-mapped methods, asserts each carries a bare
 * {@code @rbacAuthz.hasPermission('R','V')} gate, and rejects any residual
 * {@code hasRole} / {@code hasAnyRole} form (T-40-02 mitigation). The
 * {@code isEqualTo(3)} count is mandatory — without it the sweep passes
 * vacuously if a refactor removes every mapping, or silently ignores a newly
 * added ungated endpoint.
 *
 * <p><b>Section C</b> resolves both quoted literals of every gate through
 * {@link Resource#valueOf} + {@link Operation#valueOf} + {@link Resource#supports}
 * so a mis-typed (resource, verb) pair fails the build (T-40-01 / T-40-14). It
 * also asserts {@code ACCOUNTS} has no {@code CREATE} verb — no handler on this
 * controller may ever be mapped to it.
 *
 * <p>This class covers the annotation-level gate (SC1 contribution); the sibling
 * {@link FilterChainHealerControllerAuthorizationTest} covers the request-level
 * gate through the real Spring Security filter chain (SC2 contribution), per the
 * PROVEN verdict in {@code 40-HARNESS-DECISION.md}.
 */
class HealerControllerAuthorizationTest {

    // ─── Section A — the 3 pinned literals ─────────────────────────────────

    static final String ACCOUNTS_READ_LITERAL   = "@rbacAuthz.hasPermission('ACCOUNTS','READ')";
    static final String ACCOUNTS_UPDATE_LITERAL = "@rbacAuthz.hasPermission('ACCOUNTS','UPDATE')";
    static final String ACCOUNTS_DELETE_LITERAL = "@rbacAuthz.hasPermission('ACCOUNTS','DELETE')";

    /** methodName -> expected @PreAuthorize literal, for Section C's parametric resolution. */
    private static final List<String[]> EXPECTED_GATES = List.of(
            new String[]{"getAllHealers", ACCOUNTS_READ_LITERAL},
            new String[]{"setActive", ACCOUNTS_UPDATE_LITERAL},
            new String[]{"remove", ACCOUNTS_DELETE_LITERAL});

    /**
     * Returns the {@code @PreAuthorize} value of the single public method named
     * {@code methodName} on {@code controller}. Fails the test if the method is
     * absent, ambiguous (overloaded), or unannotated. Used instead of
     * {@code getMethod(name, paramTypes...)} so these tests do not break on
     * unrelated signature churn.
     */
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
    void getAllHealers_isGatedByAccountsRead() {
        assertThat(preAuthorizeValueOf(HealerController.class, "getAllHealers"))
                .isEqualTo(ACCOUNTS_READ_LITERAL);
    }

    @Test
    void setActive_isGatedByAccountsUpdate() {
        assertThat(preAuthorizeValueOf(HealerController.class, "setActive"))
                .isEqualTo(ACCOUNTS_UPDATE_LITERAL);
    }

    @Test
    void remove_isGatedByAccountsDelete() {
        assertThat(preAuthorizeValueOf(HealerController.class, "remove"))
                .isEqualTo(ACCOUNTS_DELETE_LITERAL);
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

        for (Method method : HealerController.class.getDeclaredMethods()) {
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
                .as("the sweep must inspect exactly the 3 declared HealerController endpoints")
                .isEqualTo(3);
    }

    // ─── Section C — allow-list validity of every named (resource, verb) pair ──

    @Test
    void everyGateNamesAnAllowListedResourceVerbPair() {
        for (String[] entry : EXPECTED_GATES) {
            String methodName = entry[0];
            String literal = preAuthorizeValueOf(HealerController.class, methodName);

            Matcher matcher = BARE_GATE.matcher(literal);
            assertThat(matcher.matches())
                    .as("gate for %s (%s) must be a bare @rbacAuthz.hasPermission('R','V') expression",
                            methodName, literal)
                    .isTrue();

            // Unknown literal must fail the test (IllegalArgumentException), not be skipped.
            Resource resource = Resource.valueOf(matcher.group(1));
            Operation operation = Operation.valueOf(matcher.group(2));

            assertThat(resource.supports(operation))
                    .as("%s: %s:%s is not an allow-listed (resource, verb) pair (D-10)",
                            methodName, resource, operation)
                    .isTrue();
        }
    }

    @Test
    void accountsHasNoCreateVerb_soNoHandlerMayEverMapToIt() {
        assertThat(Resource.ACCOUNTS.supports(Operation.CREATE))
                .as("ACCOUNTS is healer-account administration (list/activate/delete) — it has no CREATE "
                        + "verb, so no HealerController handler may ever be gated on ACCOUNTS:CREATE")
                .isFalse();
    }
}

/**
 * SC2 contribution — the real-Spring-Security-filter-chain authorization checks
 * for {@link HealerController}, built on the PROVEN
 * {@code @WebMvcTest(controller) + @Import(SecurityConfig)} harness from
 * {@code 40-HARNESS-DECISION.md} (spike: {@code RbacMethodSecuritySliceContractTest},
 * {@code 9174e6e}).
 *
 * <p>Kept as a sibling top-level class (not {@code @Nested}) in the same file so
 * the annotation-contract sections above stay context-free while this class
 * carries its own slice context. Both class names end with
 * {@code HealerControllerAuthorizationTest}, so
 * {@code --tests "*HealerControllerAuthorizationTest"} runs both.
 *
 * <p>Assertions are BEHAVIOR only (HTTP status + mock interaction); no
 * {@code @PreAuthorize} literal is pinned here (that is Section A's job).
 *
 * <p>Task 1 stripped the {@code hasAnyRole} coarse prefix from every gate on this
 * controller, so the granted / ungranted / verb-isolation callers authenticate
 * as {@code HEALER} — only the {@code @rbacAuthz} bean call decides. {@code HealerService}
 * mock methods return {@code null}/void, so an authorized request completes 2xx;
 * every assertion here is therefore "{@code != 403}" / "{@code isForbidden()}".
 *
 * <p>The {@code HealerCacheService} and {@code OAuth2AuthenticationSuccessHandler}
 * mocks are required by the {@code SecurityConfig} slice context (per
 * {@code 40-HARNESS-DECISION.md}); the plan's Section-D mock list omitted them,
 * and {@code RbacAuthz} is contributed via a nested {@code @TestConfiguration}
 * {@code @Bean} named {@code rbacAuthz} rather than {@code @Import(RbacAuthz.class)}
 * (the FQN bean name breaks the {@code @rbacAuthz} SpEL).
 */
@WebMvcTest(HealerController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class,
        FilterChainHealerControllerAuthorizationTest.RbacAuthzSliceConfig.class})
@ActiveProfiles("test")
class FilterChainHealerControllerAuthorizationTest {

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

    // ─── per-controller collaborator ──────────────────────────────────────
    @MockBean
    private HealerService healerService;

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
    private static final UUID ROW_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String BASE = "/api/v1/healers";

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

    private static MockHttpServletRequestBuilder request(String method, String path) {
        MockHttpServletRequestBuilder b = switch (method) {
            case "GET" -> get(path);
            case "PATCH" -> patch(path);
            case "DELETE" -> delete(path);
            default -> throw new IllegalArgumentException("unsupported method " + method);
        };
        return b.cookie(accessCookie()).contentType(MediaType.APPLICATION_JSON).content("{}");
    }

    // ─── (a) SUPADM reaches all 3 endpoints without any grant lookup ──────

    static Stream<Arguments> allThreeEndpoints() {
        return Stream.of(
                Arguments.of("GET", BASE),
                Arguments.of("PATCH", BASE + "/" + ROW_ID + "/active?active=true"),
                Arguments.of("DELETE", BASE + "/" + ROW_ID));
    }

    @ParameterizedTest(name = "SUPADM {0} {1} is not 403")
    @MethodSource("allThreeEndpoints")
    void supadm_reachesEveryEndpoint_withoutAnyGrantLookup(String method, String path) throws Exception {
        authenticateAs("SUPADM");
        when(securityContextUtil.getCurrentRole()).thenReturn("SUPADM");

        mockMvc.perform(request(method, path))
                .andExpect(status().is(not(403)));

        // RBAC-04: the SUPADM short-circuit fires before any grant lookup.
        verifyNoInteractions(lookupService);
    }

    // ─── (b)/(c) granted vs ungranted on DELETE — differ ONLY in the boolean ──

    @Test
    void grantedCaller_reachesRemove() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), eq(Resource.ACCOUNTS), eq(Operation.DELETE)))
                .thenReturn(true);

        mockMvc.perform(delete(BASE + "/" + ROW_ID).cookie(accessCookie()))
                .andExpect(status().is(not(403)));
    }

    @Test
    void ungrantedCaller_isForbiddenFromRemove() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), eq(Resource.ACCOUNTS), eq(Operation.DELETE)))
                .thenReturn(false);

        mockMvc.perform(delete(BASE + "/" + ROW_ID).cookie(accessCookie()))
                .andExpect(status().isForbidden());

        // Direct proof the @rbacAuthz bean method executed.
        verify(lookupService).hasPermission(any(), eq(Resource.ACCOUNTS), eq(Operation.DELETE));
    }

    // ─── (d) verb isolation — a READ grant does not unlock DELETE ─────────

    @Test
    void accountsReadGrant_doesNotUnlockAccountsDelete() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), eq(Resource.ACCOUNTS), eq(Operation.READ)))
                .thenReturn(true);
        when(lookupService.hasPermission(any(), eq(Resource.ACCOUNTS), eq(Operation.DELETE)))
                .thenReturn(false);

        // DELETE denied — the DELETE verb is enforced independently of READ.
        mockMvc.perform(delete(BASE + "/" + ROW_ID).cookie(accessCookie()))
                .andExpect(status().isForbidden());

        // GET allowed — same caller, same grant map.
        mockMvc.perform(get(BASE).cookie(accessCookie()))
                .andExpect(status().is(not(403)));
    }
}
