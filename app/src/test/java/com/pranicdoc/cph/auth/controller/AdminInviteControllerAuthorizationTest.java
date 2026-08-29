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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

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
 * <p><b>Section D</b> is a source-level guard: it asserts the out-of-scope
 * invite-escalation check ({@code request.role() == Role.SUPADM}) was not swept
 * away by Task 1. Phase 41 owns that guard; plan 40-08's {@code "SUPADM"} sweep
 * deliberately allow-lists this one occurrence.
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

    // ─── Section D — the out-of-scope invite-escalation guard survives ────

    @Test
    @DisplayName("Task 1's sweep did not remove the invite-escalation guard (Phase 41 owns it; "
            + "plan 40-08 allow-lists this one \"SUPADM\" occurrence)")
    void inviteEscalationGuardIsStillPresent() {
        Path controllerSource = Paths.get(
                "src", "main", "java", "com", "pranicdoc", "cph", "auth", "controller",
                "AdminInviteController.java");
        assertThat(Files.isRegularFile(controllerSource))
                .as("expected AdminInviteController source at " + controllerSource.toAbsolutePath())
                .isTrue();

        String source;
        try {
            source = Files.readString(controllerSource);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // Same comment-stripping regexes as RbacAuthzTest.supadmBypassExistsInExactlyOnePlace,
        // so a guard that survives only inside a comment does not count.
        String stripped = source
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("//.*", "");

        assertThat(stripped)
                .as("the line-41 invite-escalation guard must remain in executable source — "
                        + "an ADMIN must not be able to mint a SUPADM (Phase 41 folds this into RBAC)")
                .contains("request.role() == Role.SUPADM");
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
    private static final String HEALER_INVITE_BODY = "{\"email\":\"invitee@example.com\",\"role\":\"HEALER\"}";
    private static final String SUPADM_INVITE_BODY = "{\"email\":\"invitee@example.com\",\"role\":\"SUPADM\"}";

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
        when(inviteService.createInvite(any(), any(), any())).thenReturn(sampleResponse());
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

    // ─── (d) layered guard — HEALER_REG:CREATE does not confer invite-escalation ──

    /**
     * T-40-15: the two guards are independent — a caller who genuinely holds
     * {@code HEALER_REG:CREATE} (so {@code @PreAuthorize} passes) still cannot mint a
     * SUPADM, because the {@code create()} invite-escalation guard throws
     * {@code ResponseStatusException(FORBIDDEN)} before {@code inviteService} is touched.
     *
     * <p>This asserts the <b>resolved exception</b>, not the rendered HTTP status:
     * {@code GlobalExceptionHandler} carries a catch-all
     * {@code @ExceptionHandler(Exception.class)} that runs ahead of Spring's
     * {@code ResponseStatusExceptionResolver}, so a controller-thrown
     * {@code ResponseStatusException(403)} is currently rendered as 500. That masking
     * is a pre-existing defect outside plan 40-03's {@code @PreAuthorize}-cutover scope
     * (logged to {@code deferred-items.md}); it does not weaken this proof — the guard
     * demonstrably fires with a FORBIDDEN status and the invite is never created.
     */
    @Test
    void healerRegCreateGrant_stillCannotMintASupadm() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        // @PreAuthorize passes: the caller genuinely holds HEALER_REG:CREATE.
        when(lookupService.hasPermission(any(), eq(Resource.HEALER_REG), eq(Operation.CREATE)))
                .thenReturn(true);

        // ...but the create() guard still denies a non-SUPADM inviting a SUPADM.
        MvcResult result = mockMvc.perform(post(BASE)
                        .cookie(accessCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SUPADM_INVITE_BODY))
                .andReturn();

        assertThat(result.getResolvedException())
                .as("the invite-escalation guard must reject a non-SUPADM inviting a SUPADM")
                .isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) result.getResolvedException()).getStatusCode())
                .as("the guard denies with FORBIDDEN")
                .isEqualTo(HttpStatus.FORBIDDEN);

        // The invite is never created — granting HEALER_REG:CREATE did not confer escalation.
        verifyNoInteractions(inviteService);
    }
}
