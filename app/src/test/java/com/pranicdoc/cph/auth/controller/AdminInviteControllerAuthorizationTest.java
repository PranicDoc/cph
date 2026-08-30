package com.pranicdoc.cph.auth.controller;

import com.pranicdoc.cph.auth.dto.InviteResponse;
import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.auth.service.impl.InviteService;
import com.pranicdoc.cph.common.enums.Role;
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
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.data.domain.Page;
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization contract for {@link AdminInviteController} after the Phase 40
 * bare-form cutover (40-03, D-01/D-02).
 *
 * <p><b>Section A</b> pins each of the 4 endpoint {@code @PreAuthorize} literals.
 * {@code resend} and {@code revoke} share the {@code HEALER_REG:UPDATE} literal
 * but are asserted independently so a future divergence is caught.
 *
 * <p><b>Section B</b> is the anti-vacuous sweep: exactly 4 request-mapped
 * methods, each a bare {@code @rbacAuthz.hasPermission('R','V')} gate, no
 * residual {@code hasRole} / {@code hasAnyRole}. {@code isEqualTo(4)} is
 * mandatory.
 *
 * <p><b>Section C</b> resolves every named (resource, verb) pair through
 * {@link Resource#valueOf} + {@link Operation#valueOf} + {@link Resource#supports}
 * and asserts {@code HEALER_REG} has no {@code DELETE} verb — the reason
 * {@code revoke} maps to {@code UPDATE} (PD-40-07).
 *
 * <p><b>Section D</b> is a source-level guard: Phase 41 retired the invite-escalation
 * check from {@code create()} and replaced it with a broader HEALER-only invariant in
 * {@code InviteService} that covers every persist branch (D-06). It asserts the
 * controller no longer reads {@code request.role()} AND that the service now carries
 * {@code Role.HEALER} plus the {@code IllegalStateException} throw — the control was
 * strengthened, not removed.
 *
 * <p>The sibling {@link FilterChainAdminInviteControllerAuthorizationTest}
 * covers the request-level gate through the real Spring Security filter chain
 * (SC2 contribution), per the PROVEN verdict in {@code 40-HARNESS-DECISION.md}.
 *
 * <p>This class does NOT modify the pre-existing {@code AdminInviteControllerTest}
 * (plain Mockito, no authorization-literal pins) — the two files stay independent.
 */
class AdminInviteControllerAuthorizationTest {

    // ─── Section A — the 4 pinned literals ────────────────────────────────

    static final String HEALER_REG_CREATE_LITERAL = "@rbacAuthz.hasPermission('HEALER_REG','CREATE')";
    static final String HEALER_REG_READ_LITERAL   = "@rbacAuthz.hasPermission('HEALER_REG','READ')";
    static final String HEALER_REG_UPDATE_LITERAL = "@rbacAuthz.hasPermission('HEALER_REG','UPDATE')";

    /** methodName -> expected @PreAuthorize literal, for Section C's parametric resolution. */
    private static final List<String[]> EXPECTED_GATES = List.of(
            new String[]{"create", HEALER_REG_CREATE_LITERAL},
            new String[]{"list", HEALER_REG_READ_LITERAL},
            new String[]{"resend", HEALER_REG_UPDATE_LITERAL},
            new String[]{"revoke", HEALER_REG_UPDATE_LITERAL});

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
    void create_isGatedByHealerRegCreate() {
        assertThat(preAuthorizeValueOf(AdminInviteController.class, "create"))
                .isEqualTo(HEALER_REG_CREATE_LITERAL);
    }

    @Test
    void list_isGatedByHealerRegRead() {
        assertThat(preAuthorizeValueOf(AdminInviteController.class, "list"))
                .isEqualTo(HEALER_REG_READ_LITERAL);
    }

    @Test
    void resend_isGatedByHealerRegUpdate() {
        assertThat(preAuthorizeValueOf(AdminInviteController.class, "resend"))
                .isEqualTo(HEALER_REG_UPDATE_LITERAL);
    }

    @Test
    void revoke_isGatedByHealerRegUpdate() {
        // Asserted independently from resend so a future divergence is caught (PD-40-07).
        assertThat(preAuthorizeValueOf(AdminInviteController.class, "revoke"))
                .isEqualTo(HEALER_REG_UPDATE_LITERAL);
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

        for (Method method : AdminInviteController.class.getDeclaredMethods()) {
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
                .as("the sweep must inspect exactly the 4 declared AdminInviteController endpoints")
                .isEqualTo(4);
    }

    // ─── Section C — allow-list validity ─────────────────────────────────

    @Test
    void everyGateNamesAnAllowListedResourceVerbPair() {
        for (String[] entry : EXPECTED_GATES) {
            String methodName = entry[0];
            String literal = preAuthorizeValueOf(AdminInviteController.class, methodName);

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
    void healerRegHasNoDeleteVerb_soRevokeMapsToUpdate() {
        assertThat(Resource.HEALER_REG.supports(Operation.DELETE))
                .as("HEALER_REG's allow-list is READ/CREATE/UPDATE only — this is why revoke maps to "
                        + "HEALER_REG:UPDATE (PD-40-07); a HEALER_REG:DELETE gate would fail closed "
                        + "permanently for every non-SUPADM grant")
                .isFalse();
    }

    // ─── Section D — the invite-escalation guard is retired in favour of the service invariant ────

    /**
     * Reads a {@code src/main/java} source file and strips comments with the same two
     * regexes as {@code RbacAuthzTest.supadmBypassExistsInExactlyOnePlace}, so a match
     * that survives only inside a comment does not count.
     */
    private static String readStrippedMainSource(String... moduleRelative) {
        Path source = Paths.get("src", "main", "java", moduleRelative[0]);
        for (int i = 1; i < moduleRelative.length; i++) {
            source = source.resolve(moduleRelative[i]);
        }
        assertThat(Files.isRegularFile(source))
                .as("expected source file at " + source.toAbsolutePath())
                .isTrue();
        try {
            return Files.readString(source)
                    .replaceAll("(?s)/\\*.*?\\*/", "")
                    .replaceAll("//.*", "");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("Phase 41 retired the invite-escalation guard; the InviteService HEALER-only "
            + "invariant replaced it and covers every persist branch (D-06)")
    void inviteEscalationGuardIsRetiredInFavourOfTheServiceInvariant() {
        String strippedController = readStrippedMainSource(
                "com", "pranicdoc", "cph", "auth", "controller", "AdminInviteController.java");
        assertThat(strippedController)
                .as("Phase 41 removed the invite-escalation guard — AdminInviteController must no "
                        + "longer read request.role() in executable source")
                .doesNotContain("request.role()");

        String strippedService = readStrippedMainSource(
                "com", "pranicdoc", "cph", "auth", "service", "impl", "InviteService.java");
        assertThat(strippedService)
                .as("the guard was replaced by a broader HEALER-only invariant in InviteService "
                        + "covering every persist branch (D-06)")
                .contains("Role.HEALER")
                .contains("IllegalStateException");
    }
}

/**
 * SC2 contribution — the real-Spring-Security-filter-chain authorization checks
 * for {@link AdminInviteController}, built on the PROVEN
 * {@code @WebMvcTest(controller) + @Import(SecurityConfig)} harness from
 * {@code 40-HARNESS-DECISION.md} (spike: {@code RbacMethodSecuritySliceContractTest},
 * {@code 9174e6e}).
 *
 * <p>Sibling top-level class (not {@code @Nested}) so the annotation-contract
 * sections above stay context-free. Both class names end with
 * {@code AdminInviteControllerAuthorizationTest}, so
 * {@code --tests "*AdminInviteControllerAuthorizationTest"} runs both.
 *
 * <p>Assertions are BEHAVIOR only (HTTP status + mock interaction); no
 * {@code @PreAuthorize} literal is pinned here. Task 1 stripped the coarse
 * {@code hasAnyRole} prefix, so granted / ungranted callers authenticate as
 * {@code HEALER} — only the {@code @rbacAuthz} bean call decides.
 *
 * <p>{@code HealerCacheService} and {@code OAuth2AuthenticationSuccessHandler}
 * mocks are required by the {@code SecurityConfig} slice context (per
 * {@code 40-HARNESS-DECISION.md}); the plan's Section-E mock list omitted them.
 * {@code RbacAuthz} is contributed via a nested {@code @TestConfiguration}
 * {@code @Bean} named {@code rbacAuthz}, not {@code @Import(RbacAuthz.class)}.
 */
@WebMvcTest(AdminInviteController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class,
        FilterChainAdminInviteControllerAuthorizationTest.RbacAuthzSliceConfig.class})
@ActiveProfiles("test")
class FilterChainAdminInviteControllerAuthorizationTest {

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

    // ─── per-controller collaborators ────────────────────────────────────
    @MockBean
    private InviteService inviteService;

    // ─── consumed by RbacAuthz (also a controller collaborator) ──────────
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
    private static final UUID INVITE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String BASE = "/api/admin/invites";
    private static final String HEALER_INVITE_BODY = "{\"email\":\"invitee@example.com\"}";

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

    private static InviteResponse sampleResponse() {
        return new InviteResponse(
                UUID.randomUUID(), "invitee@example.com", Role.HEALER.name(),
                "PENDING", Instant.now(), Instant.now().plusSeconds(3600), true);
    }

    private static MockHttpServletRequestBuilder request(String method, String path) {
        MockHttpServletRequestBuilder b = switch (method) {
            case "GET" -> get(path);
            case "POST" -> post(path);
            default -> throw new IllegalArgumentException("unsupported method " + method);
        };
        b.cookie(accessCookie()).contentType(MediaType.APPLICATION_JSON);
        if ("POST".equals(method) && path.equals(BASE)) {
            b.content(HEALER_INVITE_BODY);
        } else {
            b.content("{}");
        }
        return b;
    }

    // ─── (a) SUPADM reaches all 4 endpoints without any grant lookup ─────

    static Stream<Arguments> allFourEndpoints() {
        return Stream.of(
                Arguments.of("POST", BASE),
                Arguments.of("GET", BASE),
                Arguments.of("POST", BASE + "/" + INVITE_ID + "/resend"),
                Arguments.of("POST", BASE + "/" + INVITE_ID + "/revoke"));
    }

    @ParameterizedTest(name = "SUPADM {0} {1} is not 403")
    @MethodSource("allFourEndpoints")
    void supadm_reachesEveryEndpoint_withoutAnyGrantLookup(String method, String path) throws Exception {
        authenticateAs("SUPADM");
        when(securityContextUtil.getCurrentRole()).thenReturn("SUPADM");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(inviteService.createInvite(any(), any())).thenReturn(sampleResponse());
        when(inviteService.list(any())).thenReturn(Page.empty());
        when(inviteService.resend(any())).thenReturn(sampleResponse());
        when(inviteService.revoke(any())).thenReturn(sampleResponse());

        mockMvc.perform(request(method, path))
                .andExpect(status().is(not(403)));

        // RBAC-04: the SUPADM short-circuit fires before any grant lookup.
        verifyNoInteractions(lookupService);
    }

    // ─── (b)/(c) granted vs ungranted on revoke — differ ONLY in the boolean ──

    @Test
    void grantedCaller_reachesRevoke() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(inviteService.revoke(any())).thenReturn(sampleResponse());
        when(lookupService.hasPermission(any(), eq(Resource.HEALER_REG), eq(Operation.UPDATE)))
                .thenReturn(true);

        mockMvc.perform(post(BASE + "/" + INVITE_ID + "/revoke").cookie(accessCookie()))
                .andExpect(status().is(not(403)));
    }

    @Test
    void ungrantedCaller_isForbiddenFromRevoke() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), eq(Resource.HEALER_REG), eq(Operation.UPDATE)))
                .thenReturn(false);

        mockMvc.perform(post(BASE + "/" + INVITE_ID + "/revoke").cookie(accessCookie()))
                .andExpect(status().isForbidden());

        verify(lookupService).hasPermission(any(), eq(Resource.HEALER_REG), eq(Operation.UPDATE));
    }

    // Phase 41 deleted the former layered-guard test here: the request body can no longer
    // carry a role, so a "non-SUPADM mints a SUPADM through the DTO" scenario is
    // unconstructible. The HEALER-only rule now lives as an InviteService invariant covering
    // every persist branch (D-06), asserted at the source level in Section D and behaviourally
    // in InviteServiceTest.
}
