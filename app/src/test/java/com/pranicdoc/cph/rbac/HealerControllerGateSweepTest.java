package com.pranicdoc.cph.rbac;

import com.pranicdoc.cph.auth.controller.AuthController;
import com.pranicdoc.cph.auth.controller.BootstrapController;
import com.pranicdoc.cph.consent.controller.ReadingSessionController;
import com.pranicdoc.cph.healing.controller.CaseController;
import com.pranicdoc.cph.healing.controller.FeedbackController;
import com.pranicdoc.cph.healing.controller.ProtocolController;
import com.pranicdoc.cph.healing.controller.ScanController;
import com.pranicdoc.cph.healing.controller.SessionController;
import com.pranicdoc.cph.notification.controller.WhatsappWebhookController;
import com.pranicdoc.cph.patient.controller.PatientController;
import com.pranicdoc.cph.payments.CreditProxyController;
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HealerControllerGateSweepTest — the Phase 42 parallel to
 * {@link LegacyEndpointCutoverSweepTest} (D-15 Option B / D-21). It is the
 * surface-wide build gate for Success Criterion 4 (SC4): every healer-facing
 * controller either carries a bare {@code @rbacAuthz.hasPermission('R','V')}
 * capability gate or is asserted deliberately-open <em>by name, with a recorded
 * reason</em>. Like the Phase 40 sweep it uses no Spring context — pure
 * reflection plus comment-stripped source reads — so it runs in milliseconds and
 * is a genuine build gate rather than a slow integration test.
 *
 * <p><b>Two halves, two waves, two commits.</b>
 * <ul>
 *   <li><b>This plan (42-10)</b> creates the class: the shared reflection /
 *       source helpers ({@link #requestMappedMethods}, {@link #ungatedMethodNames},
 *       {@link #requestMappedCount}, {@link #readStrippedSource},
 *       {@link #sourcePathFor}, {@link #countOccurrences}, {@link #BARE_GATE})
 *       plus the four SC4 tests for the deliberately-open controllers that live
 *       <em>outside</em> the seven gated ones: {@code AuthController} &times;14,
 *       {@code ReadingSessionController} &times;2, {@code WhatsappWebhookController}
 *       &times;2, and {@code BootstrapController} (with
 *       {@code BootstrapController.assignRole}'s deliberate coarse gate reproduced,
 *       not flagged).</li>
 *   <li><b>Plan 42-11 (Wave 4)</b> extends this same file: it adds a
 *       {@code CONTROLLERS} list of the seven Phase-42 healer controllers and its
 *       own sweep tests, reusing the helpers declared here rather than
 *       re-declaring them.</li>
 * </ul>
 *
 * <p><b>What it deliberately does NOT do</b> — it does not re-assert anything
 * {@link LegacyEndpointCutoverSweepTest} owns. The Phase 40 counts stay frozen in
 * that file so a Phase 42 miscount can never redden a Phase 40 assertion. The one
 * exception is {@code BootstrapController.assignRole}: its {@code hasRole('SUPADM')}
 * pin is reproduced here (consistent with Phase 40 Test 6 and
 * {@code BootstrapControllerTest.ASSIGN_ROLE_LITERAL}) because SC4 names
 * {@code BootstrapController} explicitly and a Phase 42 reader must see why it is
 * out of scope.
 */
class HealerControllerGateSweepTest {

    // ─── shared constants (plan 42-11 reuses these) ─────────────────────

    private static final List<Class<? extends Annotation>> REQUEST_MAPPING_ANNOTATIONS = List.of(
            GetMapping.class, PostMapping.class, PutMapping.class, PatchMapping.class,
            DeleteMapping.class, RequestMapping.class);

    /**
     * Anchored bare-form matcher — the whole {@code @PreAuthorize} value must be
     * exactly {@code @rbacAuthz.hasPermission('<RESOURCE>','<VERB>')} with nothing
     * before or after. Declared here for plan 42-11's seven-controller sweep;
     * this plan's four controllers carry no such gate.
     */
    private static final Pattern BARE_GATE =
            Pattern.compile("^@rbacAuthz\\.hasPermission\\('([A-Z_]+)','([A-Z]+)'\\)$");

    /**
     * The 8 {@code AuthController} routes that are {@code permitAll} in
     * {@code SecurityConfig} — reached before any principal exists.
     */
    private static final Set<String> AUTH_PERMIT_ALL_ROUTES = Set.of(
            "login", "refresh", "logout", "acceptInvite", "signup", "initSession", "initFeedbackSession");

    /**
     * The 7 {@code AuthController} routes reachable by every authenticated
     * principal without a capability grant — same self-service posture Phase 40
     * locked for {@code ConsentController}.
     */
    private static final Set<String> AUTH_SELF_SERVICE_ROUTES = Set.of(
            "getCurrentUser", "getMyPermissions", "getCurrentHealer", "updateProfile", "updateUiVersion",
            "changePassword", "linkGoogle", "unlinkGoogle", "clearPatientSession");

    // ─── Phase 42 seven-controller healer surface (added by plan 42-11) ──────

    /**
     * One row per Phase-42 healer controller. Named {@code SweptController} so it
     * does not collide with the production
     * {@code com.pranicdoc.cph.auth.controller.HealerController} class.
     */
    private record SweptController(Class<?> type, int expectedMapped, int expectedGated) {
    }

    /**
     * The seven Phase-42 healer controllers with hardcoded per-controller
     * {@code (expectedMapped, expectedGated)} counts. These were DERIVED, not
     * pasted: each triple is the value recorded in the Wave-2 summaries
     * (42-02 {@code PatientController} 11/9/2, 42-04 {@code SessionController}
     * 5/5/0, <b>42-13</b> {@code CaseController} 21/17/4 (22/18/4 since the unlock endpoint, 23/19/4 since the HPU prefill read) — superseding 42-03's
     * 18/14/4 after the CR-01 route split added three ungated
     * {@code /api/v1/cases/self-service} patient twins and gated their three
     * healer-path siblings, 42-05 {@code ScanController} 1/1/0 +
     * {@code ProtocolController} 1/1/0 + {@code FeedbackController} 2/0/2,
     * 42-06 {@code CreditProxyController} 3/3/0) and was re-confirmed against the
     * controller source at branch tip. {@code CaseController}'s class-level
     * {@code /api/v1/cases} {@code @RequestMapping} is still not counted — the
     * 21 are handler methods only (Pitfall 6 / D-18, recorded in 42-03).
     */
    private static final List<SweptController> CONTROLLERS = List.of(
            new SweptController(PatientController.class, 11, 9),
            new SweptController(SessionController.class, 6, 6),
            new SweptController(CaseController.class, 23, 19),
            new SweptController(ScanController.class, 1, 1),
            new SweptController(ProtocolController.class, 1, 1),
            new SweptController(FeedbackController.class, 2, 0),
            new SweptController(CreditProxyController.class, 4, 4));

    private static final int EXPECTED_TOTAL_MAPPED = 48;
    private static final int EXPECTED_TOTAL_GATED = 40;

    /**
     * The eight request-mapped methods on the seven that are DELIBERATELY ungated,
     * keyed by {@code <fully-qualified-class>#<methodName>} with the reason each
     * one stays open as the value. Test 2 builds its failure message from this map
     * so every entry carries its own reason in the output, not one blanket comment.
     */
    private static final Map<String, String> DELIBERATELY_OPEN = deliberatelyOpen();

    private static Map<String, String> deliberatelyOpen() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("com.pranicdoc.cph.patient.controller.PatientController#generateLink",
                "emits a patient self-registration link scoped server-side to the calling healer; "
                        + "there is no healer-scoped request input, and SC4/D-09 lock it open so link "
                        + "generation is not blocked for a freshly-onboarded healer who holds no grants");
        m.put("com.pranicdoc.cph.patient.controller.PatientController#selfRegister",
                "runs on a SELF_REG / PATIENT-role token; @rbacAuthz returns false for every real "
                        + "caller, so a gate would 403 all patient self-registration (D-08 / D-19)");
        m.put("com.pranicdoc.cph.healing.controller.CaseController#create",
                "reached by a PATIENT-role token (patient self-registration flow); a gate would 403 "
                        + "every real caller (D-19)");
        m.put("com.pranicdoc.cph.healing.controller.CaseController#updateCaseDetailsSelfService",
                "reached only by a ROLE_PATIENT principal (SELF_REG / PATIENT_CASE token) whose "
                        + "TenantDetails.healerId is the OWNING healer's, so @rbacAuthz would resolve "
                        + "the patient's request against the wrong principal's grants; open at the "
                        + "capability layer only — the caller-class restriction is SecurityConfig's "
                        + "requestMatchers(\"/api/v1/cases/self-service\", "
                        + "\"/api/v1/cases/self-service/**\").hasRole(\"PATIENT\") rule, proven at "
                        + "request time by CaseControllerAuthorizationTest; created by the CR-01 route "
                        + "split (2026-09-02 ruling / D-19a). Its healer-path sibling updateCaseDetails "
                        + "is now gated CASE:UPDATE.");
        m.put("com.pranicdoc.cph.healing.controller.CaseController#getByIdSelfService",
                "reached only by a ROLE_PATIENT principal (SELF_REG / PATIENT_CASE token) whose "
                        + "TenantDetails.healerId is the OWNING healer's, so @rbacAuthz would resolve "
                        + "the patient's request against the wrong principal's grants; open at the "
                        + "capability layer only — the caller-class restriction is SecurityConfig's "
                        + "requestMatchers(\"/api/v1/cases/self-service\", "
                        + "\"/api/v1/cases/self-service/**\").hasRole(\"PATIENT\") rule, proven at "
                        + "request time by CaseControllerAuthorizationTest; created by the CR-01 route "
                        + "split (2026-09-02 ruling / D-19a). Its healer-path sibling getById is now "
                        + "gated CASE:READ.");
        m.put("com.pranicdoc.cph.healing.controller.CaseController#getSymptomsForFeedbackSelfService",
                "reached only by a ROLE_PATIENT principal (SELF_REG / PATIENT_CASE token) whose "
                        + "TenantDetails.healerId is the OWNING healer's, so @rbacAuthz would resolve "
                        + "the patient's request against the wrong principal's grants; open at the "
                        + "capability layer only — the caller-class restriction is SecurityConfig's "
                        + "requestMatchers(\"/api/v1/cases/self-service\", "
                        + "\"/api/v1/cases/self-service/**\").hasRole(\"PATIENT\") rule, proven at "
                        + "request time by CaseControllerAuthorizationTest; created by the CR-01 route "
                        + "split (2026-09-02 ruling / D-19a). Its healer-path sibling "
                        + "getSymptomsForFeedback is now gated CASE:READ — deliberately not "
                        + "FEEDBACK:READ, which D-19 dropped from the catalog.");
        m.put("com.pranicdoc.cph.healing.controller.FeedbackController#saveSymptomFeedback",
                "patient feedback submission, PATIENT-role token only (D-19)");
        m.put("com.pranicdoc.cph.healing.controller.FeedbackController#saveQualitativeFeedback",
                "patient qualitative feedback submission, PATIENT-role token only (D-19)");
        return m;
    }

    // ─── shared reflection / source helpers (plan 42-11 reuses these) ───

    private static boolean isRequestMapped(Method m) {
        return REQUEST_MAPPING_ANNOTATIONS.stream().anyMatch(a -> m.getAnnotation(a) != null);
    }

    private static List<Method> requestMappedMethods(Class<?> controller) {
        List<Method> out = new ArrayList<>();
        for (Method m : controller.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers()) || m.isSynthetic()) {
                continue;
            }
            if (isRequestMapped(m)) {
                out.add(m);
            }
        }
        return out;
    }

    /** Count of public, non-synthetic, declared request-mapped methods on a controller. */
    private static int requestMappedCount(Class<?> controller) {
        return requestMappedMethods(controller).size();
    }

    /**
     * Names of every request-mapped method on {@code controller} whose
     * {@code getAnnotation(PreAuthorize.class)} is {@code null} — i.e. the
     * deliberately-open surface. A {@link TreeSet} so assertion failures print a
     * stable, sorted diff.
     */
    private static TreeSet<String> ungatedMethodNames(Class<?> controller) {
        TreeSet<String> names = new TreeSet<>();
        for (Method m : requestMappedMethods(controller)) {
            if (m.getAnnotation(PreAuthorize.class) == null) {
                names.add(m.getName());
            }
        }
        return names;
    }

    /** Module-relative path to a class's .java source, e.g. src/main/java/com/pranicdoc/.../Foo.java */
    private static Path sourcePathFor(Class<?> type) {
        return Paths.get("src", "main", "java").resolve(type.getName().replace('.', '/') + ".java");
    }

    private static String readStrippedSource(Path source) {
        assertThat(Files.isRegularFile(source))
                .as("expected source file at %s", source.toAbsolutePath())
                .isTrue();
        String raw;
        try {
            raw = Files.readString(source);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // block/javadoc comments first, then line comments — same order
        // RbacAuthzTest / LegacyEndpointCutoverSweepTest use. Essential here:
        // these controllers' javadoc discusses authorization posture in prose,
        // and an unstripped scan for "PreAuthorize" would be self-invalidating.
        return raw
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("//.*", "");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = haystack.indexOf(needle);
        while (idx != -1) {
            count++;
            idx = haystack.indexOf(needle, idx + needle.length());
        }
        return count;
    }

    // ─── SC4 Test 1 — AuthController stays fully ungated, in two reasoned groups ──

    @Test
    void authControllerRemainsFullyUngated() {
        assertThat(requestMappedCount(AuthController.class))
                .as("AuthController request-mapped method count — an added or removed endpoint must "
                        + "redden the build rather than silently escape this open-list (anti-vacuous "
                        + "guard T-42-Assurance-O1)")
                .isEqualTo(16);

        TreeSet<String> ungated = ungatedMethodNames(AuthController.class);
        assertThat(ungated)
                .as("every AuthController request-mapped method must carry NO @PreAuthorize — D-07: "
                        + "openness is proven by annotation absence plus this named allow-list, never "
                        + "a @PreAuthorize(\"permitAll()\") marker annotation")
                .hasSize(16)
                .containsExactlyInAnyOrder(
                        "login", "refresh", "logout", "acceptInvite", "signup", "initSession",
                        "initFeedbackSession", "getCurrentUser", "getMyPermissions", "getCurrentHealer", "updateProfile", "updateUiVersion",
                        "changePassword", "linkGoogle", "unlinkGoogle", "clearPatientSession");

        assertThat(AUTH_PERMIT_ALL_ROUTES)
                .as("pre-auth 7 — permitAll in SecurityConfig; there is no authenticated principal "
                        + "yet, so a capability gate could never resolve and would lock every caller "
                        + "out of login/signup (T-42-DoS-O1)")
                .hasSize(7)
                .allMatch(ungated::contains);

        assertThat(AUTH_SELF_SERVICE_ROUTES)
                .as("self-service 9 — every authenticated principal must reach their own identity, own permissions, "
                        + "profile and UI preference (classic / new view) without a grant, the same posture Phase 40 locked for "
                        + "ConsentController's self-service endpoints; gating /me or /profile would "
                        + "break a freshly-onboarded healer who holds no grants at all (T-42-DoS-O2)")
                .hasSize(9)
                .allMatch(ungated::contains);

        TreeSet<String> union = new TreeSet<>(AUTH_PERMIT_ALL_ROUTES);
        union.addAll(AUTH_SELF_SERVICE_ROUTES);
        assertThat(union)
                .as("the two reasoned sub-groups must partition the full ungated set exactly — no "
                        + "overlap, no gap; a method that drifts into neither group fails here")
                .isEqualTo(ungated);

        assertThat(countOccurrences(readStrippedSource(sourcePathFor(AuthController.class)), "PreAuthorize"))
                .as("AuthController.java comment-stripped source must contain zero occurrences of "
                        + "PreAuthorize — openness is asserted by absence (D-07), and no "
                        + "@PreAuthorize(\"permitAll()\") marker annotation may be introduced")
                .isEqualTo(0);
    }

    // ─── SC4 Test 2 — ReadingSessionController stays ungated (corrected D-22 reason) ──

    @Test
    void readingSessionControllerRemainsUngated() {
        assertThat(requestMappedCount(ReadingSessionController.class))
                .as("ReadingSessionController request-mapped method count — anti-vacuous guard "
                        + "(T-42-Assurance-O1)")
                .isEqualTo(2);

        assertThat(ungatedMethodNames(ReadingSessionController.class))
                .as("D-22: ReadingSessionController.open/close stay open on the ConsentController "
                        + "SELF-SERVICE PRECEDENT — a freshly-onboarded healer with no grants must be "
                        + "able to open and close a reading session for their mandatory legal "
                        + "documents, or the Phase 34 registration legal gate breaks (T-42-DoS-O3). "
                        + "This is explicitly NOT the 'no healerId' rationale from CONTEXT D-08: "
                        + "research verified ReadingSessionService.open()/close() run on a healer "
                        + "ACCESS token and call getCurrentHealerId() unconditionally, and "
                        + "DocumentReader is rendered only under /legal-center, inside ProtectedRoute "
                        + "— anyone re-deriving the reason from D-08's original wording would reach a "
                        + "false conclusion. Note: 'close' is a single Java method mapped to both "
                        + "PATCH and POST via @RequestMapping(method = {...}), so it counts once in "
                        + "this reflection sweep — '2 methods' vs '3 HTTP routes' is not a discrepancy.")
                .hasSize(2)
                .containsExactlyInAnyOrder("open", "close");

        assertThat(countOccurrences(
                readStrippedSource(sourcePathFor(ReadingSessionController.class)), "PreAuthorize"))
                .as("ReadingSessionController.java comment-stripped source must contain zero "
                        + "occurrences of PreAuthorize — openness asserted by absence (D-07)")
                .isEqualTo(0);
    }

    // ─── SC4 Test 3 — WhatsappWebhookController stays ungated (Meta, no JWT) ──

    @Test
    void whatsappWebhookControllerRemainsUngated() {
        assertThat(requestMappedCount(WhatsappWebhookController.class))
                .as("WhatsappWebhookController request-mapped method count — anti-vacuous guard "
                        + "(T-42-Assurance-O1)")
                .isEqualTo(2);

        assertThat(ungatedMethodNames(WhatsappWebhookController.class))
                .as("Meta calls both methods with no JWT (permitAll in SecurityConfig); they perform "
                        + "their own verify-token and HMAC X-Hub-Signature-256 checks, so a capability "
                        + "gate would reject every real webhook delivery while adding no security "
                        + "(T-42-DoS-O4)")
                .hasSize(2)
                .containsExactlyInAnyOrder("verify", "handleEvent");

        assertThat(countOccurrences(
                readStrippedSource(sourcePathFor(WhatsappWebhookController.class)), "PreAuthorize"))
                .as("WhatsappWebhookController.java comment-stripped source must contain zero "
                        + "occurrences of PreAuthorize — openness asserted by absence (D-07)")
                .isEqualTo(0);
    }

    // ─── SC4 Test 4 — BootstrapController: bootstrap open, assignRole coarse-gate reproduced ──

    @Test
    void bootstrapControllerBootstrapIsUngatedAndAssignRoleStaysCoarseGated() {
        assertThat(requestMappedCount(BootstrapController.class))
                .as("BootstrapController request-mapped method count — anti-vacuous guard "
                        + "(T-42-Assurance-O1)")
                .isEqualTo(2);

        assertThat(ungatedMethodNames(BootstrapController.class))
                .as("bootstrap is permitAll and self-locks once any healer exists; it is the "
                        + "empty-system seeding path and cannot require a grant that does not yet "
                        + "exist. assignRole is deliberately absent from this set — its coarse gate "
                        + "is reproduced below")
                .hasSize(1)
                .containsExactly("bootstrap");

        // Reproduce LegacyEndpointCutoverSweepTest Test 6's pin, with the same overload guard.
        Method assignRole = null;
        for (Method method : BootstrapController.class.getDeclaredMethods()) {
            if (method.getName().equals("assignRole")) {
                assertThat(assignRole)
                        .as("assignRole is overloaded on BootstrapController — cannot pin its gate")
                        .isNull();
                assignRole = method;
            }
        }
        assertThat(assignRole)
                .as("BootstrapController must still declare the assignRole endpoint")
                .isNotNull();

        PreAuthorize preAuthorize = assignRole.getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize)
                .as("BootstrapController.assignRole must still carry @PreAuthorize")
                .isNotNull();
        assertThat(preAuthorize.value())
                .as("PD-40-05: BootstrapController.assignRole's hasRole('SUPADM') literal is "
                        + "DELIBERATE and OUT OF SCOPE for Phase 42 — it is the circular "
                        + "SUPADM-to-SUPADM role-seeding path by which the first grants come into "
                        + "existence, so gating it on a grant would make the system unbootstrappable "
                        + "(T-42-EoP-O1). It is already pinned by LegacyEndpointCutoverSweepTest "
                        + "Test 6 and BootstrapControllerTest.ASSIGN_ROLE_LITERAL; Phase 42 "
                        + "REPRODUCES the pin here rather than flagging it as a non-bare gate. "
                        + "Forward constraint: plan 42-11's hasRole( source sweep MUST be scoped to "
                        + "the 7 Phase-42 controllers only — a repo-wide or class-list-wide sweep "
                        + "would collide with this pin and pressure its deletion to stay green "
                        + "(42-RESEARCH Pitfall 7 / T-42-Tamper-O1).")
                .isEqualTo("hasRole('SUPADM')");
    }

    // ─── SC1 Test 1 — per-controller + grand-total mapped/gated count sweep ──

    @Test
    void everyGatedEndpointOnTheHealerSurfaceUsesTheBareDynamicForm() {
        assertThat(CONTROLLERS)
                .as("the Phase-42 healer surface is exactly the seven controllers plans "
                        + "42-02..42-06 gated")
                .hasSize(7);
        assertThat(CONTROLLERS.stream().mapToInt(SweptController::expectedMapped).sum())
                .as("EXPECTED_TOTAL_MAPPED must equal the sum of the per-controller expectedMapped "
                        + "rows — a constant adjusted away from the row sum hides a real drift "
                        + "(T-42-Assurance-SW2)")
                .isEqualTo(EXPECTED_TOTAL_MAPPED);
        assertThat(CONTROLLERS.stream().mapToInt(SweptController::expectedGated).sum())
                .as("EXPECTED_TOTAL_GATED must equal the sum of the per-controller expectedGated rows")
                .isEqualTo(EXPECTED_TOTAL_GATED);

        int totalMapped = 0;
        int totalGated = 0;

        for (SweptController controller : CONTROLLERS) {
            int mapped = 0;
            int gated = 0;

            for (Method method : requestMappedMethods(controller.type())) {
                mapped++;
                PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
                if (preAuthorize == null) {
                    continue;
                }
                gated++;
                assertThat(preAuthorize.value())
                        .as("%s.%s: @PreAuthorize must be exactly a bare "
                                + "@rbacAuthz.hasPermission('RESOURCE','VERB') expression (D-02/D-16) "
                                + "— no hasRole/hasAnyRole prefix, no boolean AND",
                                controller.type().getSimpleName(), method.getName())
                        .matches(BARE_GATE)
                        .doesNotContain("hasRole")
                        .doesNotContain("hasAnyRole");
            }

            assertThat(mapped)
                    .as("%s: expected %d request-mapped methods, found %d — the Phase-42 healer "
                            + "surface inventory drifted: an endpoint was added, removed, or the "
                            + "controller renamed out of the list since the Wave-2 cutover",
                            controller.type().getSimpleName(), controller.expectedMapped(), mapped)
                    .isEqualTo(controller.expectedMapped());
            assertThat(gated)
                    .as("%s: expected %d capability-gated methods, found %d — a gate was dropped or "
                            + "added since plans 42-02..42-06 (SC1 / T-42-EoP-SW1)",
                            controller.type().getSimpleName(), controller.expectedGated(), gated)
                    .isEqualTo(controller.expectedGated());

            totalMapped += mapped;
            totalGated += gated;
        }

        assertThat(totalMapped)
                .as("grand total of request-mapped methods across all 7 Phase-42 healer controllers")
                .isEqualTo(EXPECTED_TOTAL_MAPPED);
        assertThat(totalGated)
                .as("grand total of bare-form capability gates across all 7 Phase-42 healer "
                        + "controllers")
                .isEqualTo(EXPECTED_TOTAL_GATED);
    }

    // ─── SC1 Test 3 — every gate literal names an allow-listed (resource, verb) pair ──

    @Test
    void everyHealerGateLiteralNamesAnAllowListedResourceVerbPair() {
        List<String> literals = new ArrayList<>();
        for (SweptController controller : CONTROLLERS) {
            for (Method method : requestMappedMethods(controller.type())) {
                PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
                if (preAuthorize != null) {
                    literals.add(preAuthorize.value());
                }
            }
        }

        assertThat(literals)
                .as("the allow-list resolution must run over all %d gates on the Phase-42 healer "
                        + "surface — asserting the size FIRST means the loop below can never run "
                        + "over an empty or shrunken set", EXPECTED_TOTAL_GATED)
                .hasSize(EXPECTED_TOTAL_GATED);

        Set<Resource> resolvedResources = new LinkedHashSet<>();
        for (String literal : literals) {
            Matcher matcher = BARE_GATE.matcher(literal);
            assertThat(matcher.matches())
                    .as("%s must be a bare @rbacAuthz.hasPermission('R','V') expression", literal)
                    .isTrue();

            // An unknown constant MUST throw here (IllegalArgumentException) rather
            // than being silently skipped — a mistyped resource key or verb fails the build.
            Resource resource = Resource.valueOf(matcher.group(1));
            Operation operation = Operation.valueOf(matcher.group(2));
            resolvedResources.add(resource);

            assertThat(resource.supports(operation))
                    .as("%s:%s is not an allow-listed (resource, verb) pair. RbacAuthz resolves an "
                            + "un-allow-listed pair to false for every non-SUPADM caller, "
                            + "permanently, with no build error (Pitfall 3 / T-42-Tamper-SW1) — so "
                            + "a mistyped resource key or verb on the healer surface must fail HERE.",
                            resource, operation)
                    .isTrue();
        }

        assertThat(resolvedResources)
                .as("the distinct resolved Resource set across the 7 pins the phase's exact "
                        + "resource footprint. A valid-but-wrong gate — e.g. MASTER_DATA_PROTOCOLS "
                        + "on ProtocolController instead of SESSION_PROTOCOL (the D-06 confusion) — "
                        + "resolves cleanly and supports() accepts it, but it must fail HERE "
                        + "(T-42-Tamper-SW2).")
                .containsExactlyInAnyOrder(
                        Resource.PATIENT, Resource.SESSION, Resource.CASE, Resource.PDF,
                        Resource.SCAN, Resource.SESSION_PROTOCOL, Resource.FEEDBACK,
                        Resource.CREDIT_ACCOUNTS, Resource.CREDIT_ACCOUNT_TRANSACTIONS);
    }

    // ─── SC1/SC4 Test 2 — exactly the named 8 methods are deliberately ungated ──

    @Test
    void exactlyEightHealerSurfaceEndpointsAreDeliberatelyUngated() {
        List<String> ungated = new ArrayList<>();
        for (SweptController controller : CONTROLLERS) {
            for (Method method : requestMappedMethods(controller.type())) {
                if (method.getAnnotation(PreAuthorize.class) == null) {
                    ungated.add(controller.type().getName() + "#" + method.getName());
                }
            }
        }

        assertThat(DELIBERATELY_OPEN)
                .as("the deliberately-open allow-list must itself name exactly eight methods")
                .hasSize(8);

        String reasons = DELIBERATELY_OPEN.entrySet().stream()
                .map(e -> "  - " + e.getKey() + " — " + e.getValue())
                .collect(Collectors.joining("\n"));

        assertThat(ungated)
                .as("exactly eight request-mapped methods on the 7-controller Phase-42 healer "
                        + "surface may be ungated, and they are exactly the named set (SC1 D-20 "
                        + "reading: every method except the named deliberately-open allow-list; the "
                        + "hasSize(8) is the anti-vacuous guard). A NINTH entry appearing is an "
                        + "unguarded healerId-scoped write or read — a privilege-escalation hole. An "
                        + "entry DISAPPEARING means someone gated one of the "
                        + "CaseController#{updateCaseDetailsSelfService, getByIdSelfService, "
                        + "getSymptomsForFeedbackSelfService} patient twins and just 403'd a live "
                        + "patient self-service flow. Note: the three former dual-caller entries "
                        + "CaseController#{updateCaseDetails, getById, getSymptomsForFeedback} were "
                        + "REMOVED from this list by the CR-01 route-split ruling (2026-09-02 / "
                        + "D-19a), not lost — their healer paths are now gated CASE:UPDATE / "
                        + "CASE:READ / CASE:READ and the patient callers moved to the three "
                        + "*SelfService twins above. The eight, each with its own reason:\n%s\n"
                        + "Found: %s", reasons, ungated)
                .hasSize(8)
                .containsExactlyInAnyOrderElementsOf(DELIBERATELY_OPEN.keySet());
    }

    // ─── SC4 Test 4 — no hardcoded role gate on the scoped healer surface ──

    @Test
    void noHardcodedRoleGateSurvivesOnTheHealerSurface() {
        List<String> offenders = new ArrayList<>();
        for (SweptController controller : CONTROLLERS) {
            String stripped = readStrippedSource(sourcePathFor(controller.type()));
            if (stripped.contains("hasRole(") || stripped.contains("hasAnyRole(")) {
                offenders.add(controller.type().getSimpleName() + " (hasRole/hasAnyRole call)");
            }
            if (stripped.contains("\"SUPADM\"")) {
                offenders.add(controller.type().getSimpleName() + " (\"SUPADM\" literal)");
            }
        }

        assertThat(offenders)
                .as("no Phase-42 healer controller may contain hasRole( / hasAnyRole( or a "
                        + "\"SUPADM\" literal in comment-stripped source — every gate is the bare "
                        + "@rbacAuthz.hasPermission form and SUPADM is handled solely by the single "
                        + "RbacAuthz short-circuit (SC1 / SC4 / T-42-EoP-SW3). "
                        + "SCOPING IS LOAD-BEARING (42-RESEARCH Pitfall 7 / T-42-Assurance-SW1): "
                        + "this sweep iterates ONLY the seven-row CONTROLLERS list. It must never "
                        + "become a repo-wide file walk and must never include BootstrapController "
                        + "— BootstrapController.assignRole deliberately carries "
                        + "@PreAuthorize(\"hasRole('SUPADM')\") (PD-40-05), pinned by "
                        + "LegacyEndpointCutoverSweepTest Test 6 and this class's "
                        + "bootstrapControllerBootstrapIsUngatedAndAssignRoleStaysCoarseGated. A "
                        + "wider sweep would fail against that pin and create pressure to delete a "
                        + "real authorization control to stay green. It must also NEVER be widened "
                        + "to SecurityConfig, which since the CR-01 route split deliberately carries "
                        + "requestMatchers(\"/api/v1/cases/self-service\", "
                        + "\"/api/v1/cases/self-service/**\").hasRole(\"PATIENT\") — the framework-"
                        + "native caller-class layer for the patient self-service twins; widening "
                        + "this sweep onto SecurityConfig would create the same pressure to delete a "
                        + "real authorization control as it would for BootstrapController.assignRole's "
                        + "hasRole('SUPADM') pin. Offenders: %s", offenders)
                .isEmpty();
    }

}
