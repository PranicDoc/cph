package com.pranicdoc.cph.admin;

import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.auth.service.HealerCacheService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization contract for {@link AdminCreditController} after the Phase 40
 * bare-form cutover (40-02, D-01/D-02).
 *
 * <p><b>Section A</b> pins every one of the 10 endpoint {@code @PreAuthorize}
 * literals individually, so a drifted annotation produces a readable
 * expected/actual diff (T-40-12 mitigation).
 *
 * <p><b>Section B</b> is the anti-vacuous sweep: it counts exactly 10
 * request-mapped methods, asserts each carries a bare
 * {@code @rbacAuthz.hasPermission('R','V')} gate, and rejects any residual
 * {@code hasRole} / {@code hasAnyRole} form (T-40-02 mitigation). The
 * {@code isEqualTo(10)} count assertion is mandatory — without it the sweep
 * passes vacuously if a refactor removes every mapping, or silently ignores a
 * newly added ungated endpoint.
 *
 * <p><b>Section C</b> resolves both quoted literals of every gate through
 * {@link Resource#valueOf} + {@link Operation#valueOf} + {@link Resource#supports}
 * so a mis-typed (resource, verb) pair fails the build instead of shipping an
 * always-403 endpoint (T-40-01 / T-40-14 mitigation).
 *
 * <p>This class covers the annotation-level gate (SC1 contribution); the nested
 * {@link FilterChainAuthorization} class covers the request-level gate through
 * the real Spring Security filter chain (SC2 contribution), per the PROVEN
 * verdict in {@code 40-HARNESS-DECISION.md}.
 */
class AdminCreditControllerAuthorizationTest {

    // ─── Section A — the 10 pinned literals ──────────────────────────────────

    private static final String LIST_ALL_PACKAGES   = "@rbacAuthz.hasPermission('PACKAGES','READ')";
    private static final String CREATE_PACKAGE      = "@rbacAuthz.hasPermission('PACKAGES','CREATE')";
    private static final String UPDATE_PACKAGE      = "@rbacAuthz.hasPermission('PACKAGES','UPDATE')";
    private static final String DEACTIVATE_PACKAGE  = "@rbacAuthz.hasPermission('PACKAGES','DELETE')";
    private static final String LIST_CONFIG         = "@rbacAuthz.hasPermission('GLOBAL_CONFIG','READ')";
    private static final String UPDATE_CONFIG       = "@rbacAuthz.hasPermission('GLOBAL_CONFIG','UPDATE')";
    private static final String LIST_ACCOUNTS       = "@rbacAuthz.hasPermission('CREDIT_ACCOUNTS','READ')";
    private static final String GET_TRANSACTIONS    = "@rbacAuthz.hasPermission('CREDIT_ACCOUNT_TRANSACTIONS','READ')";
    private static final String ADJUST             = "@rbacAuthz.hasPermission('CREDIT_ACCOUNT_TRANSACTIONS','WRITE')";
    private static final String TOGGLE_PAYG         = "@rbacAuthz.hasPermission('CREDIT_ACCOUNT_TRANSACTIONS','WRITE')";

    /** methodName -> expected @PreAuthorize literal, for Section C's parametric resolution. */
    private static final List<String[]> EXPECTED_GATES = List.of(
            new String[]{"listAllPackages", LIST_ALL_PACKAGES},
            new String[]{"createPackage", CREATE_PACKAGE},
            new String[]{"updatePackage", UPDATE_PACKAGE},
            new String[]{"deactivatePackage", DEACTIVATE_PACKAGE},
            new String[]{"listConfig", LIST_CONFIG},
            new String[]{"updateConfig", UPDATE_CONFIG},
            new String[]{"listAccounts", LIST_ACCOUNTS},
            new String[]{"getTransactions", GET_TRANSACTIONS},
            new String[]{"adjust", ADJUST},
            new String[]{"togglePayg", TOGGLE_PAYG});

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
    void listAllPackages_isGatedByPackagesRead() {
        assertThat(preAuthorizeValueOf(AdminCreditController.class, "listAllPackages"))
                .isEqualTo(LIST_ALL_PACKAGES);
    }

    @Test
    void createPackage_isGatedByPackagesCreate() {
        assertThat(preAuthorizeValueOf(AdminCreditController.class, "createPackage"))
                .isEqualTo(CREATE_PACKAGE);
    }

    @Test
    void updatePackage_isGatedByPackagesUpdate() {
        assertThat(preAuthorizeValueOf(AdminCreditController.class, "updatePackage"))
                .isEqualTo(UPDATE_PACKAGE);
    }

    @Test
    void deactivatePackage_isGatedByPackagesDelete() {
        assertThat(preAuthorizeValueOf(AdminCreditController.class, "deactivatePackage"))
                .isEqualTo(DEACTIVATE_PACKAGE);
    }

    @Test
    void listConfig_isGatedByGlobalConfigRead() {
        assertThat(preAuthorizeValueOf(AdminCreditController.class, "listConfig"))
                .isEqualTo(LIST_CONFIG);
    }

    @Test
    void updateConfig_isGatedByGlobalConfigUpdate() {
        assertThat(preAuthorizeValueOf(AdminCreditController.class, "updateConfig"))
                .isEqualTo(UPDATE_CONFIG);
    }

    @Test
    void listAccounts_isGatedByCreditAccountsRead() {
        assertThat(preAuthorizeValueOf(AdminCreditController.class, "listAccounts"))
                .isEqualTo(LIST_ACCOUNTS);
    }

    @Test
    void getTransactions_isGatedByCreditAccountTransactionsRead() {
        assertThat(preAuthorizeValueOf(AdminCreditController.class, "getTransactions"))
                .isEqualTo(GET_TRANSACTIONS);
    }

    @Test
    void adjust_isGatedByCreditAccountTransactionsWrite() {
        assertThat(preAuthorizeValueOf(AdminCreditController.class, "adjust"))
                .isEqualTo(ADJUST);
    }

    @Test
    void togglePayg_isGatedByCreditAccountTransactionsWrite() {
        assertThat(preAuthorizeValueOf(AdminCreditController.class, "togglePayg"))
                .isEqualTo(TOGGLE_PAYG);
    }

    // ─── Section B — anti-vacuous sweep ─────────────────────────────────────

    private static final List<Class<? extends Annotation>> REQUEST_MAPPING_ANNOTATIONS = List.of(
            GetMapping.class, PostMapping.class, PutMapping.class, PatchMapping.class,
            DeleteMapping.class, RequestMapping.class);

    private static final Pattern BARE_GATE =
            Pattern.compile("^@rbacAuthz\\.hasPermission\\('([A-Z_]+)','([A-Z]+)'\\)$");

    @Test
    void everyRequestMappedMethodCarriesABareDynamicGate() {
        int inspected = 0;

        for (Method method : AdminCreditController.class.getDeclaredMethods()) {
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
                .as("the sweep must inspect exactly the 10 declared AdminCreditController endpoints")
                .isEqualTo(10);
    }

    // ─── Section C — allow-list validity of every named (resource, verb) pair ──

    @Test
    void everyGateNamesAnAllowListedResourceVerbPair() {
        for (String[] entry : EXPECTED_GATES) {
            String methodName = entry[0];
            String literal = preAuthorizeValueOf(AdminCreditController.class, methodName);

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
}

/**
 * SC2 contribution — the real-Spring-Security-filter-chain authorization trio
 * for {@link AdminCreditController}, built on the PROVEN
 * {@code @WebMvcTest(controller) + @Import(SecurityConfig)} harness from
 * {@code 40-HARNESS-DECISION.md} (spike: {@code RbacMethodSecuritySliceContractTest},
 * {@code 9174e6e}).
 *
 * <p>Kept as a sibling top-level class (not {@code @Nested}) in the same file so
 * the annotation-contract sections above stay context-free while this class
 * carries its own slice context. Both class names end with
 * {@code AdminCreditControllerAuthorizationTest}, so
 * {@code --tests "*AdminCreditControllerAuthorizationTest"} runs both.
 *
 * <p>Assertions are BEHAVIOR only (HTTP status + mock interaction); no
 * {@code @PreAuthorize} literal is pinned here (that is Section A's job), so a
 * future harmonization pass leaves this class green.
 *
 * <p>AdminCreditController's gates were converted to the bare form in this
 * plan's Task 1, so the granted / ungranted / cross-resource callers
 * authenticate as {@code HEALER} — there is no {@code hasAnyRole} coarse prefix
 * left to confound the result. The mocked {@code creditServiceRestClient}
 * returns {@code null} from its fluent chain, so an authorized request fails
 * downstream (500) rather than returning 2xx; every assertion here is therefore
 * "{@code != 403}" / "{@code isForbidden()}", exactly what SC2 requires.
 */
@WebMvcTest(AdminCreditController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class,
        FilterChainAdminCreditControllerAuthorizationTest.RbacAuthzSliceConfig.class})
@ActiveProfiles("test")
class FilterChainAdminCreditControllerAuthorizationTest {

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

    // ─── per-controller collaborators ──────────────────────────────────────
    @MockBean(name = "creditServiceRestClient")
    private org.springframework.web.client.RestClient creditServiceRestClient;
    @MockBean
    private AdminAuthz adminAuthz;
    @MockBean
    private HealerRepository healerRepository;
    // NOTE: ObjectMapper is deliberately NOT @MockBean'd. A Mockito mock returns
    // null from reader()/writer(), which breaks WebMvcAutoConfiguration's
    // handlerExceptionResolver at context-load time (NPE in
    // ObjectMapper.reader()). The controller injects the real autoconfigured
    // Jackson ObjectMapper, which is inert for these authorization-only tests.

    // ─── consumed by RbacAuthz ─────────────────────────────────────────────
    @MockBean
    private SecurityContextUtil securityContextUtil;
    @MockBean
    private RolePermissionLookupService lookupService;

    // ─── always required by the SecurityConfig slice context ──────────────
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
            case "POST" -> post(path);
            case "PUT" -> put(path);
            case "DELETE" -> delete(path);
            default -> throw new IllegalArgumentException("unsupported method " + method);
        };
        return b.cookie(accessCookie()).contentType(MediaType.APPLICATION_JSON).content("{}");
    }

    // ─── (a) SUPADM reaches every one of the 10 endpoints ──────────────────

    static Stream<Arguments> allTenEndpoints() {
        String p = "/api/admin/credit";
        return Stream.of(
                Arguments.of("GET", p + "/packages"),
                Arguments.of("POST", p + "/packages"),
                Arguments.of("PUT", p + "/packages/" + ROW_ID),
                Arguments.of("DELETE", p + "/packages/" + ROW_ID),
                Arguments.of("GET", p + "/config"),
                Arguments.of("PUT", p + "/config/somekey"),
                Arguments.of("GET", p + "/accounts"),
                Arguments.of("GET", p + "/accounts/HEALER/" + ROW_ID + "/transactions"),
                Arguments.of("POST", p + "/accounts/HEALER/" + ROW_ID + "/adjust"),
                Arguments.of("PUT", p + "/accounts/HEALER/" + ROW_ID + "/payg"));
    }

    @ParameterizedTest(name = "SUPADM {0} {1} is not 403")
    @MethodSource("allTenEndpoints")
    void supadm_reachesEveryEndpoint_withoutAnyGrantLookup(String method, String path) throws Exception {
        authenticateAs("SUPADM");
        when(securityContextUtil.getCurrentRole()).thenReturn("SUPADM");

        mockMvc.perform(request(method, path))
                .andExpect(status().is(not(403)));

        // RBAC-04: the SUPADM short-circuit fires before any grant lookup.
        verifyNoInteractions(lookupService);
    }

    // ─── (b)/(c) granted vs ungranted — differ ONLY in the lookup boolean ──

    @Test
    void grantedCaller_reachesListAccounts() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), eq(Resource.CREDIT_ACCOUNTS), eq(Operation.READ)))
                .thenReturn(true);

        mockMvc.perform(get("/api/admin/credit/accounts").cookie(accessCookie()))
                .andExpect(status().is(not(403)));
    }

    @Test
    void ungrantedCaller_isForbiddenFromListAccounts() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), eq(Resource.CREDIT_ACCOUNTS), eq(Operation.READ)))
                .thenReturn(false);

        mockMvc.perform(get("/api/admin/credit/accounts").cookie(accessCookie()))
                .andExpect(status().isForbidden());

        // Direct proof the @rbacAuthz bean method executed.
        verify(lookupService).hasPermission(any(), eq(Resource.CREDIT_ACCOUNTS), eq(Operation.READ));
    }

    // ─── (d) cross-resource isolation ─────────────────────────────────────

    @Test
    void grantOnCreditAccounts_doesNotUnlockPackages() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), eq(Resource.CREDIT_ACCOUNTS), eq(Operation.READ)))
                .thenReturn(true);
        when(lookupService.hasPermission(any(), eq(Resource.PACKAGES), eq(Operation.READ)))
                .thenReturn(false);

        mockMvc.perform(get("/api/admin/credit/packages").cookie(accessCookie()))
                .andExpect(status().isForbidden());
    }
}
