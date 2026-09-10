package com.pranicdoc.cph.consent.controller;

import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.security.OAuth2AuthenticationSuccessHandler;
import com.pranicdoc.cph.common.security.SecurityConfig;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.tenant.TenantInterceptor;
import com.pranicdoc.cph.consent.dto.ConsentTypeResponse;
import com.pranicdoc.cph.consent.service.ConsentTypeService;
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
import org.springframework.security.access.AccessDeniedException;
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
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
 * Authorization contract for {@link ConsentTypeController} after the Phase 40
 * bare-form cutover (40-04, D-01/D-02/D-04). This is the most
 * compliance-sensitive class in the phase: it must prove the D-04 flag swap
 * changed the <em>source</em> of the system-consent-type override without
 * changing <em>who</em> gets it.
 *
 * <p><b>Section A</b> pins each of the 5 endpoint {@code @PreAuthorize} literals.
 *
 * <p><b>Section B</b> is the anti-vacuous sweep: exactly 5 request-mapped
 * methods, each a bare {@code @rbacAuthz.hasPermission('R','V')} gate, no
 * residual {@code hasRole}/{@code hasAnyRole} (T-40-02 mitigation). The
 * {@code isEqualTo(5)} count is mandatory.
 *
 * <p><b>Section C</b> resolves every named (resource, verb) pair through
 * {@link Resource#valueOf} + {@link Operation#valueOf} + {@link Resource#supports},
 * and asserts {@code LEGAL_DOCUMENTS.supports(ADMIN)} is true — the fact that
 * makes the D-04 consent override genuinely grantable, unlike the master-data
 * override in plan 40-05 which is non-grantable by design (PD-40-04).
 *
 * <p>The sibling {@link FilterChainConsentTypeControllerAuthorizationTest}
 * carries <b>Section D</b> — the four-cell D-04 override matrix — through the
 * real Spring Security filter chain (SC2), per the PROVEN verdict in
 * {@code 40-HARNESS-DECISION.md}.
 */
class ConsentTypeControllerAuthorizationTest {

    // ─── Section A — the 5 pinned literals ────────────────────────────────

    static final String LEGAL_DOCUMENTS_READ_LITERAL   = "@rbacAuthz.hasPermission('LEGAL_DOCUMENTS','READ')";
    static final String LEGAL_DOCUMENTS_CREATE_LITERAL = "@rbacAuthz.hasPermission('LEGAL_DOCUMENTS','CREATE')";
    static final String LEGAL_DOCUMENTS_UPDATE_LITERAL = "@rbacAuthz.hasPermission('LEGAL_DOCUMENTS','UPDATE')";

    /** methodName -> expected @PreAuthorize literal. */
    private static final List<String[]> EXPECTED_GATES = List.of(
            new String[]{"list", LEGAL_DOCUMENTS_READ_LITERAL},
            new String[]{"create", LEGAL_DOCUMENTS_CREATE_LITERAL},
            new String[]{"rename", LEGAL_DOCUMENTS_UPDATE_LITERAL},
            new String[]{"setMandatory", LEGAL_DOCUMENTS_UPDATE_LITERAL},
            new String[]{"setActive", LEGAL_DOCUMENTS_UPDATE_LITERAL});

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
    void list_isGatedByLegalDocumentsRead() {
        assertThat(preAuthorizeValueOf(ConsentTypeController.class, "list"))
                .isEqualTo(LEGAL_DOCUMENTS_READ_LITERAL);
    }

    @Test
    void create_isGatedByLegalDocumentsCreate() {
        assertThat(preAuthorizeValueOf(ConsentTypeController.class, "create"))
                .isEqualTo(LEGAL_DOCUMENTS_CREATE_LITERAL);
    }

    @Test
    void rename_isGatedByLegalDocumentsUpdate() {
        assertThat(preAuthorizeValueOf(ConsentTypeController.class, "rename"))
                .isEqualTo(LEGAL_DOCUMENTS_UPDATE_LITERAL);
    }

    @Test
    void setMandatory_isGatedByLegalDocumentsUpdate() {
        assertThat(preAuthorizeValueOf(ConsentTypeController.class, "setMandatory"))
                .isEqualTo(LEGAL_DOCUMENTS_UPDATE_LITERAL);
    }

    @Test
    void setActive_isGatedByLegalDocumentsUpdate() {
        assertThat(preAuthorizeValueOf(ConsentTypeController.class, "setActive"))
                .isEqualTo(LEGAL_DOCUMENTS_UPDATE_LITERAL);
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

        for (Method method : ConsentTypeController.class.getDeclaredMethods()) {
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
                .as("the sweep must inspect exactly the 5 declared ConsentTypeController endpoints")
                .isEqualTo(5);
    }

    // ─── Section C — allow-list validity ─────────────────────────────────

    @Test
    void everyGateNamesAnAllowListedResourceVerbPair() {
        for (String[] entry : EXPECTED_GATES) {
            String methodName = entry[0];
            String literal = preAuthorizeValueOf(ConsentTypeController.class, methodName);

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
    void legalDocumentsSupportsAdminVerb_soTheD04ConsentOverrideIsGrantable() {
        assertThat(Resource.LEGAL_DOCUMENTS.supports(Operation.ADMIN))
                .as("LEGAL_DOCUMENTS is the one resource whose allow-list includes ADMIN — this is what "
                        + "makes the D-04 consent system-row override a genuinely grantable permission, "
                        + "unlike the master-data override in plan 40-05 which is non-grantable by design "
                        + "(PD-40-04)")
                .isTrue();
    }
}

/**
 * SC2 contribution — <b>Section D, the D-04 override matrix</b>, run through the
 * real Spring Security filter chain on the PROVEN
 * {@code @WebMvcTest(controller) + @Import(SecurityConfig)} harness from
 * {@code 40-HARNESS-DECISION.md} (spike: {@code RbacMethodSecuritySliceContractTest},
 * {@code 9174e6e}).
 *
 * <p>Sibling top-level class (not {@code @Nested}) so the annotation-contract
 * sections stay context-free. Both class names end with
 * {@code ConsentTypeControllerAuthorizationTest}, so
 * {@code --tests "*ConsentTypeControllerAuthorizationTest"} runs both.
 *
 * <p>The four-cell matrix, all via {@code ArgumentCaptor} on the actual third
 * argument to {@code ConsentTypeService.setMandatory}/{@code setActive} — not a
 * status code — so the D-04 claim ("changed the source, not the audience") is
 * verified directly:
 * <ol>
 *   <li><b>SUPADM</b> reaches all 5 endpoints without any grant lookup, and the
 *       captured {@code isSuperAdmin} is {@code true} for mandatory-toggle and
 *       deactivate — SUPADM can still alter a SYSTEM consent type after the swap.</li>
 *   <li><b>Granted {@code LEGAL_DOCUMENTS:UPDATE}, not {@code :ADMIN}</b> — passes the
 *       gate, captured {@code isSuperAdmin} is {@code false}; and when the service
 *       throws {@code AccessDeniedException} on a system row, the response is 403.
 *       Holding the edit permission does not confer the system-row override.</li>
 *   <li><b>Granted {@code LEGAL_DOCUMENTS:ADMIN}</b> (same caller) — captured
 *       {@code isSuperAdmin} is {@code true}. The new capability D-04 unlocks.</li>
 *   <li><b>Ungranted</b> ({@code :UPDATE} false) — 403 from {@code @PreAuthorize},
 *       {@code consentTypeService} never invoked. Differs from cell 2 only in the
 *       {@code UPDATE} boolean.</li>
 * </ol>
 */
@WebMvcTest(ConsentTypeController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class,
        FilterChainConsentTypeControllerAuthorizationTest.RbacAuthzSliceConfig.class})
@ActiveProfiles("test")
class FilterChainConsentTypeControllerAuthorizationTest {

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
    private ConsentTypeService consentTypeService;

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
    private static final UUID TYPE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String TYPES = "/api/v1/consents/types";

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

    private static ConsentTypeResponse sampleResponse() {
        return new ConsentTypeResponse(
                UUID.randomUUID(), "marketing-consent", "Marketing Consent",
                false, true, true, Instant.now());
    }

    private static MockHttpServletRequestBuilder request(String method, String path, String body) {
        MockHttpServletRequestBuilder b = switch (method) {
            case "GET" -> get(path);
            case "POST" -> post(path);
            case "PATCH" -> patch(path);
            default -> throw new IllegalArgumentException("unsupported method " + method);
        };
        b.cookie(accessCookie()).contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            b.content(body);
        }
        return b;
    }

    // ─── cell 1 — SUPADM sweep over all 5 concrete paths ─────────────────

    static Stream<Arguments> allFiveEndpoints() {
        return Stream.of(
                Arguments.of("GET", TYPES, null),
                Arguments.of("POST", TYPES, "{\"label\":\"Marketing Consent\"}"),
                Arguments.of("PATCH", TYPES + "/" + TYPE_ID + "/label", "{\"label\":\"Renamed Consent\"}"),
                Arguments.of("PATCH", TYPES + "/" + TYPE_ID + "/mandatory?value=true", null),
                Arguments.of("PATCH", TYPES + "/" + TYPE_ID + "/active?value=false", null));
    }

    @ParameterizedTest(name = "SUPADM {0} {1} is not 403")
    @MethodSource("allFiveEndpoints")
    void supadm_reachesEveryEndpoint_withoutAnyGrantLookup(String method, String path, String body) throws Exception {
        authenticateAs("SUPADM");
        when(securityContextUtil.getCurrentRole()).thenReturn("SUPADM");
        when(consentTypeService.list(anyBoolean())).thenReturn(List.of());
        when(consentTypeService.create(any())).thenReturn(sampleResponse());
        when(consentTypeService.rename(any(), any())).thenReturn(sampleResponse());
        when(consentTypeService.setMandatory(any(), anyBoolean(), anyBoolean())).thenReturn(sampleResponse());
        when(consentTypeService.setActive(any(), anyBoolean(), anyBoolean())).thenReturn(sampleResponse());

        mockMvc.perform(request(method, path, body))
                .andExpect(status().is(not(403)));

        // RBAC-04: the SUPADM short-circuit fires before any grant lookup.
        verifyNoInteractions(lookupService);
    }

    // ─── cell 1 (behavioral) — SUPADM override still reaches the service as true ──

    @Test
    void supadm_mandatoryToggleOnSystemRow_passesIsSuperAdminTrue() throws Exception {
        authenticateAs("SUPADM");
        when(securityContextUtil.getCurrentRole()).thenReturn("SUPADM");
        when(consentTypeService.setMandatory(eq(TYPE_ID), eq(true), anyBoolean())).thenReturn(sampleResponse());

        mockMvc.perform(patch(TYPES + "/" + TYPE_ID + "/mandatory?value=true").cookie(accessCookie()))
                .andExpect(status().is(not(403)));

        var captor = forClass(Boolean.class);
        verify(consentTypeService).setMandatory(eq(TYPE_ID), eq(true), captor.capture());
        assertThat(captor.getValue())
                .as("a SUPADM can still mandatory-toggle a SYSTEM consent type after the D-04 swap")
                .isTrue();
    }

    @Test
    void supadm_deactivateSystemRow_passesIsSuperAdminTrue() throws Exception {
        authenticateAs("SUPADM");
        when(securityContextUtil.getCurrentRole()).thenReturn("SUPADM");
        when(consentTypeService.setActive(eq(TYPE_ID), eq(false), anyBoolean())).thenReturn(sampleResponse());

        mockMvc.perform(patch(TYPES + "/" + TYPE_ID + "/active?value=false").cookie(accessCookie()))
                .andExpect(status().is(not(403)));

        var captor = forClass(Boolean.class);
        verify(consentTypeService).setActive(eq(TYPE_ID), eq(false), captor.capture());
        assertThat(captor.getValue())
                .as("a SUPADM can still deactivate a SYSTEM consent type after the D-04 swap")
                .isTrue();
    }

    // ─── cell 2 — granted LEGAL_DOCUMENTS:UPDATE but NOT :ADMIN ──────────

    @Test
    void updateGrantWithoutAdminGrant_passesGate_butIsSuperAdminIsFalse() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), eq(Resource.LEGAL_DOCUMENTS), eq(Operation.UPDATE)))
                .thenReturn(true);
        when(lookupService.hasPermission(any(), eq(Resource.LEGAL_DOCUMENTS), eq(Operation.ADMIN)))
                .thenReturn(false);
        when(consentTypeService.setActive(eq(TYPE_ID), eq(false), anyBoolean())).thenReturn(sampleResponse());

        mockMvc.perform(patch(TYPES + "/" + TYPE_ID + "/active?value=false").cookie(accessCookie()))
                .andExpect(status().is(not(403)));

        var captor = forClass(Boolean.class);
        verify(consentTypeService).setActive(eq(TYPE_ID), eq(false), captor.capture());
        assertThat(captor.getValue())
                .as("holding LEGAL_DOCUMENTS:UPDATE must NOT confer the system-row override")
                .isFalse();
    }

    @Test
    void updateGrantWithoutAdminGrant_systemRow_serviceThrows_returns403() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), eq(Resource.LEGAL_DOCUMENTS), eq(Operation.UPDATE)))
                .thenReturn(true);
        when(lookupService.hasPermission(any(), eq(Resource.LEGAL_DOCUMENTS), eq(Operation.ADMIN)))
                .thenReturn(false);
        when(consentTypeService.setActive(eq(TYPE_ID), eq(false), anyBoolean()))
                .thenThrow(new AccessDeniedException("System consent types can only be deactivated by SUPADM"));

        mockMvc.perform(patch(TYPES + "/" + TYPE_ID + "/active?value=false").cookie(accessCookie()))
                .andExpect(status().isForbidden());
    }

    // ─── cell 3 — granted LEGAL_DOCUMENTS:ADMIN (same caller) ───────────

    @Test
    void adminGrant_passesIsSuperAdminTrue() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), eq(Resource.LEGAL_DOCUMENTS), eq(Operation.UPDATE)))
                .thenReturn(true);
        when(lookupService.hasPermission(any(), eq(Resource.LEGAL_DOCUMENTS), eq(Operation.ADMIN)))
                .thenReturn(true);
        when(consentTypeService.setActive(eq(TYPE_ID), eq(false), anyBoolean())).thenReturn(sampleResponse());

        mockMvc.perform(patch(TYPES + "/" + TYPE_ID + "/active?value=false").cookie(accessCookie()))
                .andExpect(status().is(not(403)));

        var captor = forClass(Boolean.class);
        verify(consentTypeService).setActive(eq(TYPE_ID), eq(false), captor.capture());
        assertThat(captor.getValue())
                .as("a non-SUPADM caller WITH LEGAL_DOCUMENTS:ADMIN now receives the system-row override "
                        + "— the capability D-04 unlocks, previously unreachable for any non-SUPADM")
                .isTrue();
    }

    // ─── cell 4 — ungranted (differs from cell 2 only in the UPDATE boolean) ──

    @Test
    void ungrantedCaller_isForbidden_andServiceNeverInvoked() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), eq(Resource.LEGAL_DOCUMENTS), eq(Operation.UPDATE)))
                .thenReturn(false);

        mockMvc.perform(patch(TYPES + "/" + TYPE_ID + "/active?value=false").cookie(accessCookie()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(consentTypeService);
    }
}
