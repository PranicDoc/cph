package com.pranicdoc.cph.healing.controller;

import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Annotation-level authorization contract for {@link ProtocolController} after the Phase 42
 * Wave-2 capability-gate cutover (plan 42-05, SC1, D-01/D-06).
 *
 * <p>{@code ProtocolController} is a one-method controller — {@code POST /}
 * ({@code saveProtocol}). Its gate, {@code @rbacAuthz.hasPermission('SESSION_PROTOCOL','CREATE')},
 * is the <em>only</em> enforcement site for {@code Resource.SESSION_PROTOCOL}, which is
 * deliberately distinct from the admin protocol catalog {@code Resource.MASTER_DATA_PROTOCOLS}
 * (SC2, D-06): a healer-session protocol grant can never be satisfied by an admin
 * master-data grant.
 *
 * <p>Pure reflection over {@code @PreAuthorize} plus {@link Resource}/{@link Operation}
 * enum validity. No Spring context, no {@code @WebMvcTest}, no {@code MockMvc}. The
 * request-time (real filter chain) allow/deny suite is
 * {@code ScanAndProtocolControllerAuthorizationTest} (same plan).
 *
 * <ul>
 *   <li><b>Section A</b> — the single literal pin for {@code saveProtocol}.</li>
 *   <li><b>Section B</b> — anti-vacuous sweep: inspected == 1, gated == 1, ungated
 *       {@code isEmpty()}, the value a bare {@code @rbacAuthz.hasPermission('R','V')}.</li>
 *   <li><b>Section C</b> — the literal resolves through the allow-list into
 *       {@code Resource.SESSION_PROTOCOL}; the resource is non-identical to
 *       {@code Resource.MASTER_DATA_PROTOCOLS} and supports exactly {@code {CREATE}}.</li>
 * </ul>
 */
class ProtocolControllerAuthorizationContractTest {

    private static final String SESSION_PROTOCOL_CREATE =
            "@rbacAuthz.hasPermission('SESSION_PROTOCOL','CREATE')";

    private static final int EXPECTED_MAPPED = 1;
    private static final int EXPECTED_GATED = 1;

    private static final List<Class<? extends Annotation>> REQUEST_MAPPING_ANNOTATIONS = List.of(
            GetMapping.class, PostMapping.class, PutMapping.class, PatchMapping.class,
            DeleteMapping.class, RequestMapping.class);

    private static final Pattern BARE_GATE =
            Pattern.compile("^@rbacAuthz\\.hasPermission\\('([A-Z_]+)','([A-Z]+)'\\)$");

    private static boolean isRequestMapped(Method m) {
        return REQUEST_MAPPING_ANNOTATIONS.stream().anyMatch(a -> m.getAnnotation(a) != null);
    }

    private static String preAuthorizeValueOf(String methodName) {
        Method match = null;
        for (Method m : ProtocolController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers()) || m.isSynthetic() || !m.getName().equals(methodName)) {
                continue;
            }
            if (match != null) {
                return fail("method %s on ProtocolController is ambiguous (overloaded) — cannot pin its @PreAuthorize",
                        methodName);
            }
            match = m;
        }
        if (match == null) {
            return fail("no public method named %s on ProtocolController", methodName);
        }
        PreAuthorize preAuthorize = match.getAnnotation(PreAuthorize.class);
        if (preAuthorize == null) {
            return fail("method %s on ProtocolController carries no @PreAuthorize", methodName);
        }
        return preAuthorize.value();
    }

    // ─── Section A — the single literal pin ──────────────────────────────

    @Test
    @DisplayName("saveProtocol is gated SESSION_PROTOCOL:CREATE — the sole enforcement site for Resource.SESSION_PROTOCOL")
    void saveProtocol_isGatedBySessionProtocolCreate() {
        assertThat(preAuthorizeValueOf("saveProtocol"))
                .as("saveProtocol must be gated by %s", SESSION_PROTOCOL_CREATE)
                .isEqualTo(SESSION_PROTOCOL_CREATE);
    }

    // ─── Section B — anti-vacuous sweep ─────────────────────────────────

    @Test
    void everyRequestMappedMethodCarriesABareSessionProtocolGate() {
        int inspected = 0;
        int gated = 0;
        List<String> ungated = new ArrayList<>();

        for (Method method : ProtocolController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic() || !isRequestMapped(method)) {
                continue;
            }
            inspected++;

            PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
            if (preAuthorize == null) {
                ungated.add(method.getName());
                continue;
            }
            gated++;

            assertThat(preAuthorize.value())
                    .as("method %s's @PreAuthorize must be a bare @rbacAuthz.hasPermission('R','V') expression",
                            method.getName())
                    .matches(BARE_GATE)
                    .doesNotContain("hasRole")
                    .doesNotContain("hasAnyRole");
        }

        assertThat(inspected)
                .as("ProtocolController has exactly one request-mapped method (POST /) — a method added or "
                        + "removed here must redden the build rather than silently pass")
                .isEqualTo(EXPECTED_MAPPED);
        assertThat(gated)
                .as("the sole ProtocolController POST must carry a bare capability gate (SC1); a dropped gate "
                        + "restores the implicit any-authenticated-healer path over mid-session clinical writes")
                .isEqualTo(EXPECTED_GATED);
        assertThat(new TreeSet<>(ungated))
                .as("ProtocolController has zero deliberately-open endpoints. Found ungated: %s", ungated)
                .isEmpty();
    }

    // ─── Section C — allow-list resolution + SESSION_PROTOCOL distinctness (SC2 / D-06) ──

    @Test
    void sessionProtocolCreateGateResolvesAndIsDistinctFromMasterDataProtocols() {
        String literal = preAuthorizeValueOf("saveProtocol");

        Matcher matcher = BARE_GATE.matcher(literal);
        assertThat(matcher.matches())
                .as("%s must be a bare @rbacAuthz.hasPermission('R','V') expression", literal)
                .isTrue();

        Resource resource = Resource.valueOf(matcher.group(1));
        Operation operation = Operation.valueOf(matcher.group(2));

        assertThat(resource.supports(operation))
                .as("%s:%s is not an allow-listed (resource, verb) pair (D-10) — RbacAuthz would resolve "
                        + "it to false for every non-SUPADM caller, permanently, with no build error",
                        resource, operation)
                .isTrue();
        assertThat(resource)
                .as("%s resolves to %s — the saveProtocol gate must name Resource.SESSION_PROTOCOL", literal, resource)
                .isEqualTo(Resource.SESSION_PROTOCOL);
        assertThat(operation)
                .as("%s resolves to %s — the saveProtocol gate must name Operation.CREATE", literal, operation)
                .isEqualTo(Operation.CREATE);

        assertThat(Resource.SESSION_PROTOCOL)
                .as("SC2 / D-06: SESSION_PROTOCOL (per-session healer submission) must be a DIFFERENT enum "
                        + "constant from MASTER_DATA_PROTOCOLS (the admin protocol catalog) — otherwise a "
                        + "healer-session protocol write would be satisfiable by an admin master-data grant")
                .isNotEqualTo(Resource.MASTER_DATA_PROTOCOLS);
        assertThat(Resource.SESSION_PROTOCOL.getSupportedOperations())
                .as("Resource.SESSION_PROTOCOL allow-lists exactly {CREATE} (D-01/D-02) — ProtocolController "
                        + "has one POST and nothing else; an added verb here would be un-grantable")
                .isEqualTo(Set.of(Operation.CREATE));
    }
}
