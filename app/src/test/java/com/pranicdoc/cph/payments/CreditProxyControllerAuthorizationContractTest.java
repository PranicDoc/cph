package com.pranicdoc.cph.payments;

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
 * Annotation-level authorization contract for {@link CreditProxyController} after the Phase 42
 * Wave-2 capability-gate cutover (plan 42-06, SC1, D-04).
 *
 * <p>{@code CreditProxyController} has exactly 3 request-mapped methods spanning the credit
 * domain's two resources: {@code getPackages} ({@code CREDIT_ACCOUNTS:READ}), {@code purchase}
 * ({@code CREDIT_ACCOUNT_TRANSACTIONS:WRITE}) and {@code createAccount}
 * ({@code CREDIT_ACCOUNTS:CREATE}). {@code createAccount}'s gate is the <em>only</em>
 * enforcement site for the {@code CREATE} verb {@code CREDIT_ACCOUNTS} gained this phase
 * (D-04): a wrong literal there = a dead catalog entry + a 403-forever endpoint with no
 * build error.
 *
 * <p>Pure reflection over {@code @PreAuthorize} plus {@link Resource}/{@link Operation} enum
 * validity. No Spring context, no {@code @WebMvcTest}, no {@code MockMvc}. The request-time
 * (real filter chain) allow/deny suite is {@code CreditProxyControllerAuthorizationTest}
 * (same plan).
 *
 * <ul>
 *   <li><b>Section A</b> — 3 independent literal pins, one per method.</li>
 *   <li><b>Section B</b> — anti-vacuous sweep: inspected == 3, gated == 3, ungated
 *       {@code isEmpty()}, every value a bare {@code @rbacAuthz.hasPermission('R','V')}.</li>
 *   <li><b>Section C</b> — the 3 literals resolve through the allow-list;
 *       {@code Resource.CREDIT_ACCOUNTS} supports exactly {@code {READ, CREATE}} and
 *       {@code CREDIT_ACCOUNTS:WRITE} is {@code false} — proving the {@code purchase} verb
 *       choice was forced, not arbitrary (PD-40-02 precedent).</li>
 * </ul>
 */
class CreditProxyControllerAuthorizationContractTest {

    private static final String CREDIT_ACCOUNTS_READ   = "@rbacAuthz.hasPermission('CREDIT_ACCOUNTS','READ')";
    private static final String TXN_WRITE              = "@rbacAuthz.hasPermission('CREDIT_ACCOUNT_TRANSACTIONS','WRITE')";
    private static final String CREDIT_ACCOUNTS_CREATE = "@rbacAuthz.hasPermission('CREDIT_ACCOUNTS','CREATE')";
    private static final String TXN_READ               = "@rbacAuthz.hasPermission('CREDIT_ACCOUNT_TRANSACTIONS','READ')";

    /** methodName -> expected @PreAuthorize literal. All 6 request-mapped methods. */
    private static final List<String[]> EXPECTED_GATES = List.of(
            new String[]{"getPackages",       CREDIT_ACCOUNTS_READ},
            new String[]{"purchase",          TXN_WRITE},
            new String[]{"createOrder",       TXN_WRITE},
            new String[]{"confirmOrder",      TXN_WRITE},
            new String[]{"createAccount",     CREDIT_ACCOUNTS_CREATE},
            new String[]{"getMyTransactions", TXN_READ});

    private static final int EXPECTED_MAPPED = 6;
    private static final int EXPECTED_GATED = 6;

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
        for (Method m : CreditProxyController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers()) || m.isSynthetic() || !m.getName().equals(methodName)) {
                continue;
            }
            if (match != null) {
                return fail("method %s on CreditProxyController is ambiguous (overloaded) — cannot pin its @PreAuthorize",
                        methodName);
            }
            match = m;
        }
        if (match == null) {
            return fail("no public method named %s on CreditProxyController", methodName);
        }
        PreAuthorize preAuthorize = match.getAnnotation(PreAuthorize.class);
        if (preAuthorize == null) {
            return fail("method %s on CreditProxyController carries no @PreAuthorize", methodName);
        }
        return preAuthorize.value();
    }

    private static void assertGate(String methodName, String expected) {
        assertThat(preAuthorizeValueOf(methodName))
                .as("%s must be gated by %s", methodName, expected)
                .isEqualTo(expected);
    }

    // ─── Section A — 3 independent literal pins ───────────────────────────

    @Test
    @DisplayName("getPackages is gated CREDIT_ACCOUNTS:READ")
    void getPackages_isGatedByCreditAccountsRead() {
        assertGate("getPackages", CREDIT_ACCOUNTS_READ);
    }

    @Test
    @DisplayName("purchase is gated CREDIT_ACCOUNT_TRANSACTIONS:WRITE — CREDIT_ACCOUNTS has no "
            + "writable verb (PD-40-02)")
    void purchase_isGatedByTransactionsWrite() {
        assertGate("purchase", TXN_WRITE);
    }

    @Test
    @DisplayName("createAccount is gated CREDIT_ACCOUNTS:CREATE — the sole enforcement site for the "
            + "D-04 verb added this phase")
    void createAccount_isGatedByCreditAccountsCreate() {
        assertGate("createAccount", CREDIT_ACCOUNTS_CREATE);
    }

    // ─── Section B — anti-vacuous sweep ──────────────────────────────────

    @Test
    void everyRequestMappedMethodCarriesABareCreditGate() {
        int inspected = 0;
        int gated = 0;
        List<String> ungated = new ArrayList<>();

        for (Method method : CreditProxyController.class.getDeclaredMethods()) {
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
                .as("CreditProxyController has exactly 6 request-mapped methods — a method added or "
                        + "removed here must redden the build rather than silently pass")
                .isEqualTo(EXPECTED_MAPPED);
        assertThat(gated)
                .as("every one of the 6 CreditProxyController methods must carry a bare capability gate "
                        + "(SC1); a dropped gate restores the implicit any-authenticated-healer path over "
                        + "billing / credit-balance operations")
                .isEqualTo(EXPECTED_GATED);
        assertThat(new TreeSet<>(ungated))
                .as("CreditProxyController has zero deliberately-open endpoints. Found ungated: %s", ungated)
                .isEmpty();
    }

    // ─── Section C — allow-list resolution + CREDIT_ACCOUNTS verb-set pin + PD-40-02 negative pin ──

    @Test
    void everyGateResolvesThroughTheAllowListAndTheCreditAccountsVerbSetIsPinned() {
        List<String> literals = new ArrayList<>();
        for (String[] entry : EXPECTED_GATES) {
            literals.add(preAuthorizeValueOf(entry[0]));
        }

        assertThat(literals)
                .as("the allow-list check must run over all 3 CreditProxyController gates, never an empty list")
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
        }

        // D-04: the CREATE verb added to CREDIT_ACCOUNTS this phase is pinned at the site that uses it.
        assertThat(Resource.CREDIT_ACCOUNTS.getSupportedOperations())
                .as("Resource.CREDIT_ACCOUNTS allow-lists exactly {READ, CREATE, ADMIN} — CREATE (D-04) for "
                        + "CreditProxyController.createAccount, ADMIN for the SUPADM-by-default admin Accounts screen")
                .isEqualTo(Set.of(Operation.READ, Operation.CREATE, Operation.ADMIN));

        // PD-40-02 negative pin: purchase could not use CREDIT_ACCOUNTS:WRITE — it is un-allow-listed.
        assertThat(Resource.CREDIT_ACCOUNTS.supports(Operation.WRITE))
                .as("CREDIT_ACCOUNTS:WRITE is NOT allow-listed — this is why purchase gates "
                        + "CREDIT_ACCOUNT_TRANSACTIONS:WRITE (PD-40-02), the same forced choice "
                        + "AdminCreditController.adjust made. Also proves the positive CREDIT_ACCOUNTS "
                        + "assertion above is not vacuous.")
                .isFalse();
    }
}
