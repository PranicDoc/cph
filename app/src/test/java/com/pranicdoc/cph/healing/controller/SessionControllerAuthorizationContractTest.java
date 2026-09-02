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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Annotation-level authorization contract for {@link SessionController} after the Phase 42
 * Wave-2 capability-gate cutover (plan 42-04, SC1, D-03).
 *
 * <p>{@code SessionController} is the smallest single-resource surface in the healing
 * domain — 5 request-mapped methods, all {@code SESSION:*}. It owns the one non-obvious
 * mapping in the domain: {@code GET /{id}/chakras} returns session-detail and gates
 * {@code SESSION:READ}, deliberately <em>not</em> {@code SCAN:READ} (D-03) — which is why
 * {@code Resource.SCAN} ships with {@code {CREATE}} only.
 *
 * <p>Pure reflection over {@code @PreAuthorize} plus {@link Resource}/{@link Operation}
 * enum validity. No Spring context, no {@code @WebMvcTest}, no {@code MockMvc}: it cannot
 * pass for filter-chain reasons and runs in milliseconds. The request-time (real filter
 * chain) allow/deny suite is {@code SessionControllerAuthorizationTest} (same plan).
 *
 * <ul>
 *   <li><b>Section A</b> — 5 independent literal pins, one per method.</li>
 *   <li><b>Section B</b> — anti-vacuous sweep: inspected == 5, gated == 5, ungated
 *       {@code isEmpty()}, every value a bare {@code @rbacAuthz.hasPermission('R','V')}.</li>
 *   <li><b>Section C</b> — the 5 literals resolve through the allow-list into exactly
 *       {@code Resource.SESSION}; the D-03 negative pin asserts
 *       {@code Resource.SCAN.supports(Operation.READ)} is {@code false}.</li>
 *   <li><b>WR-02 pin</b> — {@code sessionControllerCreateEnforcesSessionCreateOnly_wr02Acknowledged}
 *       is the executable record that {@code SCAN:CREATE} / {@code SESSION_PROTOCOL:CREATE}
 *       are acknowledged inert for the bundled {@code POST /sessions/case} write path
 *       (WR-02 ruling 2026-09-02 / D-25 / deferred item {@code D-42-14-01}).</li>
 * </ul>
 */
class SessionControllerAuthorizationContractTest {

    // ─── Section A expected literals (several methods share a value; each pinned independently) ──

    private static final String SESSION_READ   = "@rbacAuthz.hasPermission('SESSION','READ')";
    private static final String SESSION_CREATE = "@rbacAuthz.hasPermission('SESSION','CREATE')";
    private static final String SESSION_UPDATE = "@rbacAuthz.hasPermission('SESSION','UPDATE')";

    /** methodName -> expected @PreAuthorize literal. All 5 request-mapped methods. */
    private static final List<String[]> EXPECTED_GATES = List.of(
            new String[]{"create",               SESSION_CREATE},
            new String[]{"getById",              SESSION_READ},
            new String[]{"getByCaseId",          SESSION_READ},
            new String[]{"complete",             SESSION_UPDATE},
            new String[]{"getAllScannedChakras", SESSION_READ});

    private static final int EXPECTED_MAPPED = 5;
    private static final int EXPECTED_GATED = 5;

    private static final List<Class<? extends Annotation>> REQUEST_MAPPING_ANNOTATIONS = List.of(
            GetMapping.class, PostMapping.class, PutMapping.class, PatchMapping.class,
            DeleteMapping.class, RequestMapping.class);

    /**
     * Anchored bare-form matcher (identical to {@code LegacyEndpointCutoverSweepTest.BARE_GATE}):
     * the whole {@code @PreAuthorize} value must be exactly
     * {@code @rbacAuthz.hasPermission('<RESOURCE>','<VERB>')} with nothing before or after.
     */
    private static final Pattern BARE_GATE =
            Pattern.compile("^@rbacAuthz\\.hasPermission\\('([A-Z_]+)','([A-Z]+)'\\)$");

    private static boolean isRequestMapped(Method m) {
        return REQUEST_MAPPING_ANNOTATIONS.stream().anyMatch(a -> m.getAnnotation(a) != null);
    }

    /** Resolve a public method by NAME (not parameter types); fail if the name is overloaded. */
    private static String preAuthorizeValueOf(String methodName) {
        Method match = null;
        for (Method m : SessionController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers()) || m.isSynthetic() || !m.getName().equals(methodName)) {
                continue;
            }
            if (match != null) {
                return fail("method %s on SessionController is ambiguous (overloaded) — cannot pin its @PreAuthorize",
                        methodName);
            }
            match = m;
        }
        if (match == null) {
            return fail("no public method named %s on SessionController", methodName);
        }
        PreAuthorize preAuthorize = match.getAnnotation(PreAuthorize.class);
        if (preAuthorize == null) {
            return fail("method %s on SessionController carries no @PreAuthorize", methodName);
        }
        return preAuthorize.value();
    }

    private static void assertGate(String methodName, String expected) {
        assertThat(preAuthorizeValueOf(methodName))
                .as("%s must be gated by %s", methodName, expected)
                .isEqualTo(expected);
    }

    // ─── Section A — 5 independent literal pins ───────────────────────────

    @Test @DisplayName("create is gated SESSION:CREATE")
    void create_isGatedBySessionCreate() { assertGate("create", SESSION_CREATE); }

    @Test @DisplayName("getById is gated SESSION:READ")
    void getById_isGatedBySessionRead() { assertGate("getById", SESSION_READ); }

    @Test @DisplayName("getByCaseId is gated SESSION:READ")
    void getByCaseId_isGatedBySessionRead() { assertGate("getByCaseId", SESSION_READ); }

    @Test @DisplayName("complete is gated SESSION:UPDATE")
    void complete_isGatedBySessionUpdate() { assertGate("complete", SESSION_UPDATE); }

    @Test
    @DisplayName("getAllScannedChakras is gated SESSION:READ, deliberately not SCAN:READ (D-03) — "
            + "it returns the set of chakra names scanned in a session and reads session-detail; "
            + "Resource.SCAN allow-lists CREATE only")
    void getAllScannedChakras_isGatedBySessionReadNotScanRead() {
        assertGate("getAllScannedChakras", SESSION_READ);
    }

    // ─── Section B — anti-vacuous sweep ──────────────────────────────────

    @Test
    void everyRequestMappedMethodCarriesABareSessionGate() {
        int inspected = 0;
        int gated = 0;
        List<String> ungated = new ArrayList<>();

        for (Method method : SessionController.class.getDeclaredMethods()) {
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

            String value = preAuthorize.value();
            assertThat(value)
                    .as("method %s's @PreAuthorize must be a bare @rbacAuthz.hasPermission('R','V') expression",
                            method.getName())
                    .matches(BARE_GATE)
                    .doesNotContain("hasRole")
                    .doesNotContain("hasAnyRole");
        }

        assertThat(inspected)
                .as("the sweep must inspect exactly the 5 declared SessionController request-mapped methods — "
                        + "a method added or removed here must redden the build rather than silently pass")
                .isEqualTo(EXPECTED_MAPPED);
        assertThat(gated)
                .as("every one of the 5 SessionController methods must carry a bare capability gate (SC1); "
                        + "a dropped gate restores the implicit any-authenticated-healer path over clinical "
                        + "healing-session records")
                .isEqualTo(EXPECTED_GATED);
        assertThat(new TreeSet<>(ungated))
                .as("SessionController has zero deliberately-open endpoints — every request-mapped method is "
                        + "healer-only and must be gated. Found ungated: %s", ungated)
                .isEmpty();
    }

    // ─── Section C — allow-listed pair resolution + SESSION-only partition + D-03 negative pin ──

    @Test
    void everyGateResolvesToSessionAndScanReadIsNotAllowListed() {
        List<String> literals = new ArrayList<>();
        for (String[] entry : EXPECTED_GATES) {
            literals.add(preAuthorizeValueOf(entry[0]));
        }

        assertThat(literals)
                .as("the allow-list check must run over all 5 SessionController gates, never an empty list")
                .hasSize(EXPECTED_GATED);

        for (String literal : literals) {
            Matcher matcher = BARE_GATE.matcher(literal);
            assertThat(matcher.matches())
                    .as("%s must be a bare @rbacAuthz.hasPermission('R','V') expression", literal)
                    .isTrue();

            // An unknown literal must FAIL here, never be silently skipped.
            Resource resource = Resource.valueOf(matcher.group(1));
            Operation operation = Operation.valueOf(matcher.group(2));

            assertThat(resource.supports(operation))
                    .as("%s:%s is not an allow-listed (resource, verb) pair (D-10) — RbacAuthz would resolve "
                            + "it to false for every non-SUPADM caller, permanently, with no build error",
                            resource, operation)
                    .isTrue();

            assertThat(resource)
                    .as("%s resolves to %s — every SessionController gate must name Resource.SESSION", literal, resource)
                    .isEqualTo(Resource.SESSION);
        }

        // D-03 negative pin: anyone who later "fixes" getAllScannedChakras to SCAN:READ gets a
        // red build here instead of an endpoint that 403s forever for every non-SUPADM caller
        // with no error, because Resource.SCAN allow-lists CREATE only.
        assertThat(Resource.SCAN.supports(Operation.READ))
                .as("SCAN:READ is NOT allow-listed (D-03) — Resource.SCAN permits only {CREATE}. "
                        + "getAllScannedChakras therefore gates SESSION:READ, not SCAN:READ. A future SCAN:READ "
                        + "gate would be un-grantable and 403 forever. This also proves the positive "
                        + "Resource.SESSION assertions above are not vacuous.")
                .isFalse();
    }

    // ─── WR-02 acknowledgement — executable record that SCAN / SESSION_PROTOCOL are inert here ──

    @Test
    @DisplayName("create is gated SESSION:CREATE only — SCAN:CREATE / SESSION_PROTOCOL:CREATE are "
            + "acknowledged inert for the bundled POST /sessions/case write path (WR-02 ruling 2026-09-02)")
    void sessionControllerCreateEnforcesSessionCreateOnly_wr02Acknowledged() {
        assertThat(preAuthorizeValueOf("create"))
                .as("POST /api/v1/sessions/case is the cph/ui wizard's real write path and bundles "
                        + "scan + session-protocol sub-writes inside SessionService.create. Per the "
                        + "WR-02 ruling (2026-09-02 / D-25) those sub-capabilities are acknowledged "
                        + "rather than enforced — this endpoint carries the bare SESSION:CREATE gate "
                        + "and nothing else. Reasons: enforcing in the service layer would move "
                        + "enforcement off the annotation surface the whole phase contract is built "
                        + "on, entangle a fix with the pre-existing red SessionServiceTest baseline "
                        + "class, and 403 the primary wizard flow once a Practitioner role is seeded "
                        + "(seeding is out of scope per ROADMAP Phase 42).")
                .isEqualTo(SESSION_CREATE);

        Set<Resource> resolved = new LinkedHashSet<>();
        for (Method method : SessionController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic() || !isRequestMapped(method)) {
                continue;
            }
            PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
            if (preAuthorize == null) {
                continue;
            }
            Matcher matcher = BARE_GATE.matcher(preAuthorize.value());
            assertThat(matcher.matches())
                    .as("%s must be a bare @rbacAuthz.hasPermission('R','V') expression", preAuthorize.value())
                    .isTrue();
            resolved.add(Resource.valueOf(matcher.group(1)));
        }

        assertThat(resolved)
                .as("no SessionController gate literal may name Resource.SCAN or "
                        + "Resource.SESSION_PROTOCOL — this test is the executable record that the "
                        + "two resources are inert for the bundled write path. If a future change "
                        + "starts enforcing them here, this test must be updated AND the "
                        + "Resource.SCAN / Resource.SESSION_PROTOCOL / SessionController.create "
                        + "javadocs AND deferred item D-42-14-01 must be revised in the same commit.")
                .doesNotContain(Resource.SCAN, Resource.SESSION_PROTOCOL)
                .containsExactly(Resource.SESSION);
    }
}
