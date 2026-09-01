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
 * Annotation-level authorization contract for {@link ScanController} after the Phase 42
 * Wave-2 capability-gate cutover (plan 42-05, SC1, D-01/D-03).
 *
 * <p>{@code ScanController} is a one-method controller — {@code POST /} ({@code saveScan}).
 * Its gate, {@code @rbacAuthz.hasPermission('SCAN','CREATE')}, is the <em>only</em>
 * enforcement site for {@code Resource.SCAN}: if the literal is wrong the resource is dead
 * weight in the catalog and the endpoint is 403-forever with no build error. {@code SCAN}
 * allow-lists {@code CREATE} only (D-01/D-03) — the sole scan-read path,
 * {@code SessionController.getAllScannedChakras}, gates {@code SESSION:READ}.
 *
 * <p>Pure reflection over {@code @PreAuthorize} plus {@link Resource}/{@link Operation}
 * enum validity. No Spring context, no {@code @WebMvcTest}, no {@code MockMvc}. The
 * request-time (real filter chain) allow/deny suite is
 * {@code ScanAndProtocolControllerAuthorizationTest} (same plan).
 *
 * <ul>
 *   <li><b>Section A</b> — the single literal pin for {@code saveScan}.</li>
 *   <li><b>Section B</b> — anti-vacuous sweep: inspected == 1, gated == 1, ungated
 *       {@code isEmpty()}, the value a bare {@code @rbacAuthz.hasPermission('R','V')}.</li>
 *   <li><b>Section C</b> — the literal resolves through the allow-list into
 *       {@code Resource.SCAN}; {@code SCAN} supports exactly {@code {CREATE}} and
 *       {@code SCAN:READ} is {@code false} (D-01/D-03 negative pin).</li>
 * </ul>
 */
class ScanControllerAuthorizationContractTest {

    private static final String SCAN_CREATE = "@rbacAuthz.hasPermission('SCAN','CREATE')";

    private static final int EXPECTED_MAPPED = 1;
    private static final int EXPECTED_GATED = 1;

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

    private static String preAuthorizeValueOf(String methodName) {
        Method match = null;
        for (Method m : ScanController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers()) || m.isSynthetic() || !m.getName().equals(methodName)) {
                continue;
            }
            if (match != null) {
                return fail("method %s on ScanController is ambiguous (overloaded) — cannot pin its @PreAuthorize",
                        methodName);
            }
            match = m;
        }
        if (match == null) {
            return fail("no public method named %s on ScanController", methodName);
        }
        PreAuthorize preAuthorize = match.getAnnotation(PreAuthorize.class);
        if (preAuthorize == null) {
            return fail("method %s on ScanController carries no @PreAuthorize", methodName);
        }
        return preAuthorize.value();
    }

    // ─── Section A — the single literal pin ──────────────────────────────

    @Test
    @DisplayName("saveScan is gated SCAN:CREATE — the sole enforcement site for Resource.SCAN")
    void saveScan_isGatedByScanCreate() {
        assertThat(preAuthorizeValueOf("saveScan"))
                .as("saveScan must be gated by %s", SCAN_CREATE)
                .isEqualTo(SCAN_CREATE);
    }

    // ─── Section B — anti-vacuous sweep ─────────────────────────────────

    @Test
    void everyRequestMappedMethodCarriesABareScanGate() {
        int inspected = 0;
        int gated = 0;
        List<String> ungated = new ArrayList<>();

        for (Method method : ScanController.class.getDeclaredMethods()) {
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
                .as("ScanController has exactly one request-mapped method (POST /) — a method added or "
                        + "removed here must redden the build rather than silently pass")
                .isEqualTo(EXPECTED_MAPPED);
        assertThat(gated)
                .as("the sole ScanController POST must carry a bare capability gate (SC1); a dropped gate "
                        + "restores the implicit any-authenticated-healer path over mid-session clinical writes")
                .isEqualTo(EXPECTED_GATED);
        assertThat(new TreeSet<>(ungated))
                .as("ScanController has zero deliberately-open endpoints. Found ungated: %s", ungated)
                .isEmpty();
    }

    // ─── Section C — allow-list resolution + SCAN verb-set pin + D-03 negative pin ──

    @Test
    void scanCreateGateResolvesAndScanReadIsNotAllowListed() {
        String literal = preAuthorizeValueOf("saveScan");

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
                .as("%s resolves to %s — the saveScan gate must name Resource.SCAN", literal, resource)
                .isEqualTo(Resource.SCAN);
        assertThat(operation)
                .as("%s resolves to %s — the saveScan gate must name Operation.CREATE", literal, operation)
                .isEqualTo(Operation.CREATE);

        assertThat(Resource.SCAN.getSupportedOperations())
                .as("Resource.SCAN allow-lists exactly {CREATE} (D-01/D-02) — an added verb here has no "
                        + "enforcing endpoint this phase and would be un-grantable")
                .isEqualTo(Set.of(Operation.CREATE));
        assertThat(Resource.SCAN.supports(Operation.READ))
                .as("SCAN:READ is NOT allow-listed (D-03) — the only scan-read path, "
                        + "SessionController.getAllScannedChakras, gates SESSION:READ. A future SCAN:READ gate "
                        + "would be un-grantable and 403 forever for every non-SUPADM caller. This also proves "
                        + "the positive Resource.SCAN assertion above is not vacuous.")
                .isFalse();
    }
}
