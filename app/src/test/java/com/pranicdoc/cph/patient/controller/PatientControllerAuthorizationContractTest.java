package com.pranicdoc.cph.patient.controller;

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
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Annotation-level authorization contract for {@link PatientController} after the
 * Phase 42 Wave-2 capability-gate cutover (plan 42-02, SC1 / SC4, D-08 / D-09 / D-16 / D-19).
 *
 * <p>{@code PatientController} is the highest-PII surface in the phase. Before this
 * plan all 11 request-mapped methods ran on an implicit "any authenticated healer"
 * rule ({@code .anyRequest().authenticated()} plus a {@code WHERE healer_id = ?}
 * clause in the service). This test pins what the annotations now say — pure
 * reflection over {@code @PreAuthorize} plus {@link Resource}/{@link Operation}
 * enum validity. No Spring context, no {@code @WebMvcTest}, no {@code MockMvc}: it
 * cannot pass for filter-chain reasons and runs in milliseconds. The request-time
 * (real filter chain) allow/deny suite is {@code PatientControllerAuthorizationTest}
 * (plan 42-08).
 *
 * <ul>
 *   <li><b>Section A</b> — 9 independent literal pins, one per gated method.</li>
 *   <li><b>Section B</b> — anti-vacuous over-reach guard: inspected == 11,
 *       gated == 9, ungated == {generateLink, selfRegister}.</li>
 *   <li><b>Section C</b> — every named (resource, verb) pair resolves through the
 *       {@link Resource}/{@link Operation} allow-list; PATIENT:SHARE is proven not.</li>
 * </ul>
 */
class PatientControllerAuthorizationContractTest {

    // ─── expected literals (several methods share a value; each is pinned independently) ──

    private static final String PATIENT_READ   = "@rbacAuthz.hasPermission('PATIENT','READ')";
    private static final String PATIENT_CREATE = "@rbacAuthz.hasPermission('PATIENT','CREATE')";
    private static final String PATIENT_UPDATE = "@rbacAuthz.hasPermission('PATIENT','UPDATE')";
    private static final String PATIENT_DELETE = "@rbacAuthz.hasPermission('PATIENT','DELETE')";

    /** methodName -> expected @PreAuthorize literal. All 9 gated endpoints. */
    private static final List<String[]> EXPECTED_GATES = List.of(
            new String[]{"create",               PATIENT_CREATE},
            new String[]{"register",              PATIENT_CREATE},
            new String[]{"getAllPatients",        PATIENT_READ},
            new String[]{"getById",               PATIENT_READ},
            new String[]{"search",                PATIENT_READ},
            new String[]{"update",                PATIENT_UPDATE},
            new String[]{"delete",                PATIENT_DELETE},
            new String[]{"updateMedicalHistory",  PATIENT_UPDATE},
            new String[]{"getMedicalHistory",     PATIENT_READ});

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
        for (Method m : PatientController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers()) || m.isSynthetic() || !m.getName().equals(methodName)) {
                continue;
            }
            if (match != null) {
                return fail("method %s on PatientController is ambiguous (overloaded) — cannot pin its @PreAuthorize",
                        methodName);
            }
            match = m;
        }
        if (match == null) {
            return fail("no public method named %s on PatientController", methodName);
        }
        PreAuthorize preAuthorize = match.getAnnotation(PreAuthorize.class);
        if (preAuthorize == null) {
            return fail("method %s on PatientController carries no @PreAuthorize", methodName);
        }
        return preAuthorize.value();
    }

    private static void assertGate(String methodName, String expected) {
        assertThat(preAuthorizeValueOf(methodName))
                .as("%s must be gated by %s", methodName, expected)
                .isEqualTo(expected);
    }

    // ─── Section A — 9 independent literal pins ───────────────────────────

    @Test @DisplayName("create is gated PATIENT:CREATE")
    void create_isGatedByPatientCreate() { assertGate("create", PATIENT_CREATE); }

    @Test @DisplayName("register is gated PATIENT:CREATE (D-09 trace: healer-authenticated /registration route)")
    void register_isGatedByPatientCreate() { assertGate("register", PATIENT_CREATE); }

    @Test @DisplayName("getAllPatients is gated PATIENT:READ")
    void getAllPatients_isGatedByPatientRead() { assertGate("getAllPatients", PATIENT_READ); }

    @Test @DisplayName("getById is gated PATIENT:READ")
    void getById_isGatedByPatientRead() { assertGate("getById", PATIENT_READ); }

    @Test @DisplayName("search is gated PATIENT:READ")
    void search_isGatedByPatientRead() { assertGate("search", PATIENT_READ); }

    @Test @DisplayName("update is gated PATIENT:UPDATE")
    void update_isGatedByPatientUpdate() { assertGate("update", PATIENT_UPDATE); }

    @Test @DisplayName("delete is gated PATIENT:DELETE")
    void delete_isGatedByPatientDelete() { assertGate("delete", PATIENT_DELETE); }

    @Test @DisplayName("updateMedicalHistory is gated PATIENT:UPDATE")
    void updateMedicalHistory_isGatedByPatientUpdate() { assertGate("updateMedicalHistory", PATIENT_UPDATE); }

    @Test @DisplayName("getMedicalHistory is gated PATIENT:READ")
    void getMedicalHistory_isGatedByPatientRead() { assertGate("getMedicalHistory", PATIENT_READ); }

    // ─── Section B — anti-vacuous over-reach guard ───────────────────────

    @Test
    void everyRequestMappedMethodIsEitherBareGatedOrADeliberatelyOpenException() {
        int inspected = 0;
        int gated = 0;
        List<String> ungated = new ArrayList<>();

        for (Method method : PatientController.class.getDeclaredMethods()) {
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
                .as("the sweep must inspect exactly the 11 declared PatientController request-mapped methods — "
                        + "a method added or removed here changes the highest-PII surface in the phase and must "
                        + "redden the build rather than silently pass")
                .isEqualTo(11);
        assertThat(gated)
                .as("exactly 9 of the 11 PatientController methods carry a bare PATIENT:* capability gate (SC1); "
                        + "a dropped gate restores the implicit any-authenticated-healer path over patient PII")
                .isEqualTo(9);

        TreeSet<String> ungatedSet = new TreeSet<>(ungated);
        assertThat(ungatedSet)
                .as("the only PatientController methods that may stay ungated (SC4) are: "
                        + "generateLink — an SC4-locked patient self-registration-link emitter scoped "
                        + "server-side to the calling healer, with no healer-scoped request input, kept open "
                        + "so a freshly-onboarded healer with no grants can still issue links (same posture as "
                        + "the self-service consent endpoints); and selfRegister — reached only on a "
                        + "SELF_REG / PATIENT-role token where @rbacAuthz returns false for every real caller, "
                        + "so a gate would 403 all patient self-registration (D-08 / D-19). Any OTHER ungated "
                        + "method here is an unguarded PII endpoint. Found: %s", ungated)
                .hasSize(2)
                .containsExactlyInAnyOrder("generateLink", "selfRegister");
    }

    // ─── Section C — allow-listed (resource, verb) pair resolution ────────

    @Test
    void everyGateNamesAnAllowListedResourceVerbPair() {
        List<String> literals = new ArrayList<>();
        for (String[] entry : EXPECTED_GATES) {
            literals.add(preAuthorizeValueOf(entry[0]));
        }

        assertThat(literals)
                .as("the allow-list check must run over all 9 PatientController gates, never an empty list")
                .hasSize(9);

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

        // Negative control: an allow-listed pair must be distinguishable from a non-allow-listed one.
        assertThat(Resource.PATIENT.supports(Operation.SHARE))
                .as("PATIENT:SHARE is NOT allow-listed — Resource.PATIENT permits only "
                        + "{READ, CREATE, UPDATE, DELETE, EXPORT}. This proves the Section-C loop can tell an "
                        + "allow-listed pair from a rejected one, so its 9 positive assertions are not vacuous.")
                .isFalse();
    }
}
