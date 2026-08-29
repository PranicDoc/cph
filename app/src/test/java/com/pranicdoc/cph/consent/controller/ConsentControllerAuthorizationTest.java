package com.pranicdoc.cph.consent.controller;

import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.security.OAuth2AuthenticationSuccessHandler;
import com.pranicdoc.cph.common.security.SecurityConfig;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.tenant.TenantInterceptor;
import com.pranicdoc.cph.consent.service.ConsentService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.access.prepost.PreAuthorize;
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
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization contract for {@link ConsentController} after the Phase 40
 * bare-form cutover (40-04, D-01/D-02). This controller is compliance-sensitive
 * in <b>both</b> directions: {@code publish} decides who can release a legal
 * policy version every healer must then accept, and the other five endpoints
 * are deliberately self-service (T-30-08) — gating them would break consent
 * acceptance and the Phase 34 registration legal gate for every user.
 *
 * <p><b>Section A</b> pins {@code publish}'s {@code @PreAuthorize} literal.
 *
 * <p><b>Section B</b> is the anti-vacuous over-reach guard: exactly 6
 * request-mapped methods, and the set of methods carrying {@code @PreAuthorize}
 * must equal exactly {@code {publish}}. Any gated method must be a bare
 * {@code @rbacAuthz.hasPermission('R','V')} expression with no {@code hasRole}/
 * {@code hasAnyRole}.
 *
 * <p><b>Section C</b> resolves {@code LEGAL_DOCUMENTS} + {@code CREATE} through
 * {@link Resource#valueOf} / {@link Operation#valueOf} and asserts
 * {@link Resource#supports}.
 *
 * <p>The sibling {@link FilterChainConsentControllerAuthorizationTest} covers
 * the request-level gate through the real Spring Security filter chain (SC2),
 * per the PROVEN verdict in {@code 40-HARNESS-DECISION.md}.
 */
class ConsentControllerAuthorizationTest {

    // ─── Section A — the single pinned literal ────────────────────────────

    static final String LEGAL_DOCUMENTS_CREATE_LITERAL =
            "@rbacAuthz.hasPermission('LEGAL_DOCUMENTS','CREATE')";

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
    void publish_isGatedByLegalDocumentsCreate() {
        assertThat(preAuthorizeValueOf(ConsentController.class, "publish"))
                .isEqualTo(LEGAL_DOCUMENTS_CREATE_LITERAL);
    }

    // ─── Section B — exactly one gated endpoint, the rest deliberately open ─

    private static final List<Class<? extends Annotation>> REQUEST_MAPPING_ANNOTATIONS = List.of(
            GetMapping.class, PostMapping.class, PutMapping.class, PatchMapping.class,
            DeleteMapping.class, RequestMapping.class);

    private static final Pattern BARE_GATE =
            Pattern.compile("^@rbacAuthz\\.hasPermission\\('([A-Z_]+)','([A-Z]+)'\\)$");

    @Test
    void exactlyOneEndpointIsGatedAndTheRestAreDeliberatelyOpen() {
        int inspected = 0;
        Set<String> gated = new TreeSet<>();

        for (Method method : ConsentController.class.getDeclaredMethods()) {
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
            if (preAuthorize == null) {
                continue;
            }

            gated.add(method.getName());

            String value = preAuthorize.value();
            assertThat(value)
                    .as("gated method %s must be a bare @rbacAuthz.hasPermission('R','V') expression",
                            method.getName())
                    .matches(BARE_GATE);
            assertThat(value)
                    .as("gated method %s must not retain a hasRole gate", method.getName())
                    .doesNotContain("hasRole");
            assertThat(value)
                    .as("gated method %s must not retain a hasAnyRole gate", method.getName())
                    .doesNotContain("hasAnyRole");
        }

        assertThat(inspected)
                .as("the sweep must inspect exactly the 6 declared ConsentController endpoints")
                .isEqualTo(6);

        assertThat(gated)
                .as("only `publish` may carry @PreAuthorize — gating `accept`, `withdraw`, `history`, "
                        + "`activePolicies` or `outstanding` would break self-service consent (T-30-08) "
                        + "and the Phase 34 registration legal gate for every healer")
                .containsExactly("publish");
    }

    // ─── Section C — allow-list validity ─────────────────────────────────

    @Test
    void publishGateNamesAnAllowListedResourceVerbPair() {
        Matcher matcher = BARE_GATE.matcher(LEGAL_DOCUMENTS_CREATE_LITERAL);
        assertThat(matcher.matches()).isTrue();

        Resource resource = Resource.valueOf(matcher.group(1));
        Operation operation = Operation.valueOf(matcher.group(2));

        assertThat(resource.supports(operation))
                .as("%s:%s must be an allow-listed (resource, verb) pair (D-10)", resource, operation)
                .isTrue();
    }
}

/**
 * SC2 contribution — the real-Spring-Security-filter-chain authorization checks
 * for {@link ConsentController}, built on the PROVEN
 * {@code @WebMvcTest(controller) + @Import(SecurityConfig)} harness from
 * {@code 40-HARNESS-DECISION.md} (spike: {@code RbacMethodSecuritySliceContractTest},
 * {@code 9174e6e}).
 *
 * <p>Sibling top-level class (not {@code @Nested}) so the annotation-contract
 * sections above stay context-free. Both class names end with
 * {@code ConsentControllerAuthorizationTest}, so
 * {@code --tests "*ConsentControllerAuthorizationTest"} runs both.
 *
 * <p>Assertions are BEHAVIOR only (HTTP status + mock interaction); no
 * {@code @PreAuthorize} literal is pinned here. Task 1 (40-04) already put the
 * gate in the bare form, so granted / ungranted callers authenticate as
 * {@code HEALER} — only the {@code @rbacAuthz} bean call decides.
 *
 * <p>The last test is the request-level proof that the conversion did NOT
 * accidentally close a self-service path: {@code GET /outstanding} must stay
 * reachable for a plain {@code HEALER} with every grant stubbed false.
 */
@WebMvcTest(ConsentController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class,
        FilterChainConsentControllerAuthorizationTest.RbacAuthzSliceConfig.class})
@ActiveProfiles("test")
class FilterChainConsentControllerAuthorizationTest {

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
    private ConsentService consentService;

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
    private static final String BASE = "/api/v1/consents";

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

    // ─── (a) SUPADM publishes without any grant lookup ───────────────────

    @Test
    void supadm_canPublish_withoutAnyGrantLookup() throws Exception {
        authenticateAs("SUPADM");
        when(securityContextUtil.getCurrentRole()).thenReturn("SUPADM");

        mockMvc.perform(multipart(BASE + "/policy-versions")
                        .param("consentType", "PRIVACY_POLICY")
                        .param("content", "Sample policy text.")
                        .cookie(accessCookie()))
                .andExpect(status().is(not(403)));

        verifyNoInteractions(lookupService);
    }

    // ─── (b)/(c) granted vs ungranted on publish — differ ONLY in the boolean ──

    @Test
    void grantedCaller_canPublish() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), eq(Resource.LEGAL_DOCUMENTS), eq(Operation.CREATE)))
                .thenReturn(true);

        mockMvc.perform(multipart(BASE + "/policy-versions")
                        .param("consentType", "PRIVACY_POLICY")
                        .param("content", "Sample policy text.")
                        .cookie(accessCookie()))
                .andExpect(status().is(not(403)));
    }

    @Test
    void ungrantedCaller_isForbiddenFromPublish() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), eq(Resource.LEGAL_DOCUMENTS), eq(Operation.CREATE)))
                .thenReturn(false);

        mockMvc.perform(multipart(BASE + "/policy-versions")
                        .param("consentType", "PRIVACY_POLICY")
                        .param("content", "Sample policy text.")
                        .cookie(accessCookie()))
                .andExpect(status().isForbidden());

        // Direct proof the @rbacAuthz bean method executed.
        verify(lookupService).hasPermission(any(), eq(Resource.LEGAL_DOCUMENTS), eq(Operation.CREATE));
    }

    // ─── (d) self-service stays open — the conversion did not close /outstanding ──

    @Test
    void selfServiceOutstanding_staysOpen_forHealerWithNoGrants() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), any(), any())).thenReturn(false);

        mockMvc.perform(get(BASE + "/outstanding").cookie(accessCookie()))
                .andExpect(status().is(not(403)));
    }
}
