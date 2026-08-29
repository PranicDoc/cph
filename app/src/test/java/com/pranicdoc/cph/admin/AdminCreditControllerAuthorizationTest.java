package com.pranicdoc.cph.admin;

import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;
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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

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
