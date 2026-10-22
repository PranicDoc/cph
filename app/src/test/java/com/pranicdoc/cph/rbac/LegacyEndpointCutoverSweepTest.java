package com.pranicdoc.cph.rbac;

import com.pranicdoc.cph.admin.AdminCreditController;
import com.pranicdoc.cph.analytics.controller.DashboardController;
import com.pranicdoc.cph.auth.controller.AdminInviteController;
import com.pranicdoc.cph.auth.controller.AuthController;
import com.pranicdoc.cph.auth.controller.BootstrapController;
import com.pranicdoc.cph.auth.controller.HealerController;
import com.pranicdoc.cph.consent.controller.ConsentController;
import com.pranicdoc.cph.consent.controller.ConsentTypeController;
import com.pranicdoc.cph.consent.controller.ReadingSessionController;
import com.pranicdoc.cph.healing.controller.CaseController;
import com.pranicdoc.cph.healing.controller.FeedbackController;
import com.pranicdoc.cph.healing.controller.ProtocolController;
import com.pranicdoc.cph.healing.controller.ScanController;
import com.pranicdoc.cph.healing.controller.SessionController;
import com.pranicdoc.cph.masterdata.controller.MasterDataController;
import com.pranicdoc.cph.notification.controller.InternalNotificationController;
import com.pranicdoc.cph.notification.controller.WhatsappWebhookController;
import com.pranicdoc.cph.patient.controller.PatientController;
import com.pranicdoc.cph.payments.CreditProxyController;
import com.pranicdoc.cph.rbac.controller.RbacCatalogController;
import com.pranicdoc.cph.rbac.controller.RbacRoleController;
import com.pranicdoc.cph.rbac.controller.RoleUserAssignmentController;
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
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LegacyEndpointCutoverSweepTest — the SC1 build gate for MIGRATE-02.
 *
 * <p>Plans 40-02 through 40-06 each converted and verified one controller. Nothing
 * else verifies the <em>surface</em>: that no ninth controller was forgotten, that
 * no hardcoded gate was reintroduced during rework, that no gate literal is
 * mistyped, and — equally — that the sweep did not over-reach and pressure the
 * deletion of an out-of-scope authorization control to stay green. This one class,
 * driven by a single static {@link #CONTROLLERS} list, is that surface-wide gate.
 * It uses no Spring context — pure reflection plus comment-stripped source reads —
 * so it runs in milliseconds and is a genuine build gate rather than a slow
 * integration test.
 *
 * <p><b>What it covers</b>
 * <ul>
 *   <li>Test 1 — every {@code @PreAuthorize} on the 9-controller cutover surface is
 *       exactly the bare {@code @rbacAuthz.hasPermission('RESOURCE','VERB')} form
 *       (D-02), with per-controller mapped/gated counts and grand totals of 60
 *       request-mapped methods and 55 gates.</li>
 *   <li>Test 2 — exactly 5 request-mapped methods on the surface are ungated, and
 *       all 5 are the deliberately self-service {@link ConsentController} endpoints
 *       (T-30-08).</li>
 *   <li>Test 3 — all 55 gate literals resolve through {@link Resource#valueOf} +
 *       {@link Operation#valueOf} + {@link Resource#supports} (D-10); an unknown or
 *       un-allow-listed pair fails the build (Pitfall 3).</li>
 *   <li>Test 4 — no {@code hasRole(} / {@code hasAnyRole(} survives in the
 *       comment-stripped source of any of the 9 controllers.</li>
 *   <li>Test 5 — the deliberately retained {@code "SUPADM"} control sites
 *       ({@code AdminAuthz} x2, {@code HealerServiceImpl}, {@code RbacAuthz} bypass)
 *       are asserted to STILL exist with exact counts. {@code AdminInviteController}
 *       is pinned at ZERO — Phase 41 retired its invite-escalation guard for the
 *       {@code InviteService} HEALER-only invariant — and the five converted
 *       controllers carry zero.</li>
 *   <li>Test 6 — {@code BootstrapController.assignRole} remains deliberately
 *       {@code hasRole('SUPADM')}-gated (PD-40-05), pinned rather than silently
 *       excluded.</li>
 * </ul>
 *
 * <p><b>What it does NOT cover</b> — request-time behavior. Whether the filter
 * chain actually evaluates these annotations and denies an ungranted caller is the
 * SC2 half: see {@code RbacMethodSecuritySliceContractTest} (plan 40-01) and the
 * per-controller {@code *AuthorizationTest} classes
 * ({@code AdminCreditControllerAuthorizationTest},
 * {@code HealerControllerAuthorizationTest},
 * {@code AdminInviteControllerAuthorizationTest},
 * {@code ConsentControllerAuthorizationTest},
 * {@code ConsentTypeControllerAuthorizationTest},
 * {@code MasterDataControllerAuthorizationTest}). It also does not cover the
 * in-body D-04 {@code isSuperAdmin} capability flags (those are pinned by the
 * per-controller contract tests), nor anything under {@code rbac/} beyond the three
 * harmonized controllers ({@code RbacAuthzTest} owns the rbac-package SUPADM sweep).
 *
 * <p><b>Phase 42 additive coverage (Tests 7-8).</b> Test 7 asserts the seven
 * Phase-42 healer controllers are swept by {@link HealerControllerGateSweepTest}
 * and are disjoint from this class's Phase-40 {@link #CONTROLLERS} list — their
 * per-controller mapped/gated counts live in that class, not here. Test 8 walks
 * {@code src/main/java} for every {@code @RestController} and asserts each one is
 * claimed by exactly one sweep (Phase-40 here, Phase-42 in
 * {@code HealerControllerGateSweepTest}) or listed in {@link #DELIBERATELY_UNSWEPT}
 * with a reason — discharging D-18 / research assumption A3. Neither test touches
 * {@link #CONTROLLERS}, {@link #EXPECTED_TOTAL_MAPPED}, {@link #EXPECTED_TOTAL_GATED}
 * or Tests 1-6; the frozen Phase-40 counts stay frozen (D-15 Option B).
 */
class LegacyEndpointCutoverSweepTest {

    /** One row per controller on the cutover surface. Adding a controller = one edit here. */
    private record CutoverController(Class<?> type, int expectedMapped, int expectedGated) {
    }

    /**
     * The single source of truth for the cutover surface. Both the reflection sweep
     * (Tests 1-3) and the source sweep (Tests 4-5) derive everything — class objects,
     * expected counts, and source-file paths — from this list, so the two halves can
     * never drift apart.
     */
    private static final List<CutoverController> CONTROLLERS = List.of(
            new CutoverController(AdminCreditController.class, 10, 10),
            new CutoverController(AdminInviteController.class, 4, 4),
            new CutoverController(HealerController.class, 3, 3),
            new CutoverController(ConsentController.class, 6, 1),
            new CutoverController(ConsentTypeController.class, 5, 5),
            // 23 from Phase 40 + the 2 protocol->chakra->color relationship reads, + 4
            // single-protocol chakra/color self-serve management endpoints (GET/POST/PUT/DELETE
            // .../protocols/{id}/chakra-colors), + 2 import-job status reads added when the
            // bulk import became async, + 1 SUPADM-only bulk-wipe endpoint (DELETE /wipe), + 1
            // paged protocol search read (GET /protocols/page).
            new CutoverController(MasterDataController.class, 31, 31),
            new CutoverController(RbacRoleController.class, 5, 5),
            new CutoverController(RbacCatalogController.class, 1, 1),
            new CutoverController(RoleUserAssignmentController.class, 3, 3));

    private static final int EXPECTED_TOTAL_MAPPED = 68;
    private static final int EXPECTED_TOTAL_GATED = 63;

    /**
     * The seven Phase-42 healer controllers. Their per-controller mapped/gated
     * counts are pinned in {@link HealerControllerGateSweepTest}, NOT here — this
     * list exists only so Tests 7-8 can assert none of the seven is forgotten by
     * both sweeps at once (D-15 Option B / D-21).
     */
    private static final List<Class<?>> PHASE_42_HEALER_CONTROLLERS = List.of(
            PatientController.class, SessionController.class, CaseController.class,
            ScanController.class, ProtocolController.class, FeedbackController.class,
            CreditProxyController.class);

    /**
     * {@code @RestController} classes deliberately covered by NEITHER sweep, keyed
     * by fully-qualified name with a one-line reason. Test 8's repo-wide census
     * asserts every {@code @RestController} in {@code src/main/java} is either in a
     * sweep list or here.
     */
    private static final Map<String, String> DELIBERATELY_UNSWEPT = deliberatelyUnswept();

    private static Map<String, String> deliberatelyUnswept() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(DashboardController.class.getName(),
                "permission model is a separate discussion (ROADMAP §Phase 42 'Explicitly out "
                        + "of scope'); carries zero @PreAuthorize and must continue to — asserted "
                        + "directly in Test 8");
        m.put(AuthController.class.getName(),
                "asserted fully open by HealerControllerGateSweepTest.authControllerRemainsFullyUngated "
                        + "— 7 pre-auth permitAll routes + 7 authenticated self-service routes, no "
                        + "capability grant reachable");
        m.put(ReadingSessionController.class.getName(),
                "asserted open by HealerControllerGateSweepTest.readingSessionControllerRemainsUngated "
                        + "— ConsentController self-service precedent (D-22): a grantless freshly-"
                        + "onboarded healer must open/close a reading session for mandatory legal docs");
        m.put(WhatsappWebhookController.class.getName(),
                "asserted open by HealerControllerGateSweepTest.whatsappWebhookControllerRemainsUngated "
                        + "— Meta calls it with no JWT; it runs its own verify-token + HMAC "
                        + "X-Hub-Signature-256 checks, so a capability gate would reject every real "
                        + "delivery");
        m.put(InternalNotificationController.class.getName(),
                "machine-to-machine outbox sweep trigger (Cloud Scheduler) — no user or healerId "
                        + "in play; served by InternalApiSecurityConfig's own filter chain, where "
                        + "InternalApiKeyFilter checks X-Internal-Key (404 when unconfigured), pinned in "
                        + "InternalApiKeyFilterTest");
        m.put(BootstrapController.class.getName(),
                "asserted open by HealerControllerGateSweepTest.bootstrapControllerBootstrapIsUngated"
                        + "AndAssignRoleStaysCoarseGated — bootstrap is permitAll and self-locks; "
                        + "assignRole deliberately carries hasRole('SUPADM') (PD-40-05), pinned by "
                        + "this class's Test 6");
        m.put("com.pranicdoc.cph.rbac.controller.BaselineRoleBackfillController",
                "post-cutover deploy-ops endpoint (POST /api/admin/roles/practitioner/backfill), "
                        + "added after the MIGRATE-02 surface was frozen and outside the Phase-42 "
                        + "healer surface. Carries the bare gate @rbacAuthz.hasPermission('ROLES',"
                        + "'UPDATE') — pinned in BaselineRoleBackfillControllerAuthorizationContract"
                        + "Test — so it is not an ungated hole; it is simply owned by neither sweep's "
                        + "pinned counts");
        return m;
    }

    private static final List<Class<? extends Annotation>> REQUEST_MAPPING_ANNOTATIONS = List.of(
            GetMapping.class, PostMapping.class, PutMapping.class, PatchMapping.class,
            DeleteMapping.class, RequestMapping.class);

    /**
     * Anchored bare-form matcher: the whole {@code @PreAuthorize} value must be
     * exactly {@code @rbacAuthz.hasPermission('<RESOURCE>','<VERB>')} with nothing
     * before or after. A re-introduced {@code hasAnyRole(...) and ...} prefix, or
     * any wider expression, fails {@code matches(...)}.
     */
    private static final Pattern BARE_GATE =
            Pattern.compile("^@rbacAuthz\\.hasPermission\\('([A-Z_]+)','([A-Z]+)'\\)$");

    // ─── shared reflection / source helpers ──────────────────────────────

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

    /** Module-relative path to a class's .java source, e.g. src/main/java/com/pranicdoc/.../Foo.java */
    private static Path sourcePathFor(Class<?> type) {
        return Paths.get("src", "main", "java").resolve(type.getName().replace('.', '/') + ".java");
    }

    /** Module-relative path to a class's .java TEST source, e.g. src/test/java/com/pranicdoc/.../FooTest.java */
    private static Path testSourcePathFor(Class<?> type) {
        return Paths.get("src", "test", "java").resolve(type.getName().replace('.', '/') + ".java");
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
        // Same comment-stripping RbacAuthzTest.supadmBypassExistsInExactlyOnePlace uses:
        // block/javadoc comments first, then line comments. Essential here — every one
        // of these controllers now has javadoc discussing the old hasAnyRole('ADMIN',
        // 'SUPADM') form, and an unstripped scan would be self-invalidating.
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

    // ─── Test 1 — cross-controller reflection sweep with exact counts ─────

    @Test
    void everyGatedEndpointOnTheCutoverSurfaceUsesTheBareDynamicForm() {
        int totalMapped = 0;
        int totalGated = 0;

        for (CutoverController controller : CONTROLLERS) {
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
                                + "@rbacAuthz.hasPermission('RESOURCE','VERB') expression (D-02) — "
                                + "no hasRole/hasAnyRole prefix, no boolean AND",
                                controller.type().getSimpleName(), method.getName())
                        .matches(BARE_GATE)
                        .doesNotContain("hasRole")
                        .doesNotContain("hasAnyRole");
            }

            assertThat(mapped)
                    .as("%s: expected %d request-mapped methods, found %d — the cutover surface "
                            + "inventory drifted (an endpoint was added, removed, or the controller "
                            + "renamed out of the list)",
                            controller.type().getSimpleName(), controller.expectedMapped(), mapped)
                    .isEqualTo(controller.expectedMapped());
            assertThat(gated)
                    .as("%s: expected %d @PreAuthorize-gated methods, found %d — a gate was dropped "
                            + "or added since the plan 40-02..40-06 cutover",
                            controller.type().getSimpleName(), controller.expectedGated(), gated)
                    .isEqualTo(controller.expectedGated());

            totalMapped += mapped;
            totalGated += gated;
        }

        assertThat(totalMapped)
                .as("grand total of request-mapped methods across all 9 cutover controllers")
                .isEqualTo(EXPECTED_TOTAL_MAPPED);
        assertThat(totalGated)
                .as("grand total of bare-form @PreAuthorize gates across all 9 cutover controllers")
                .isEqualTo(EXPECTED_TOTAL_GATED);
    }

    // ─── Test 2 — only ConsentController is deliberately ungated ─────────

    @Test
    void onlyConsentControllerHasDeliberatelyUngatedEndpoints() {
        List<String> ungated = new ArrayList<>();
        for (CutoverController controller : CONTROLLERS) {
            for (Method method : requestMappedMethods(controller.type())) {
                if (method.getAnnotation(PreAuthorize.class) == null) {
                    ungated.add(controller.type().getName() + "#" + method.getName());
                }
            }
        }

        assertThat(ungated)
                .as("exactly five request-mapped methods on the cutover surface may be ungated, and "
                        + "every one is a self-service consent endpoint (T-30-08): a healer must be "
                        + "able to accept and withdraw their own consents and read their own policy "
                        + "state without an admin grant — gating them would break the Phase 34 "
                        + "registration legal gate. Any OTHER ungated endpoint here is an unguarded "
                        + "admin endpoint and a privilege-escalation hole. Found: %s", ungated)
                .hasSize(5)
                .allSatisfy(fqn -> assertThat(fqn)
                        .as("ungated method %s is not declared on ConsentController", fqn)
                        .startsWith(ConsentController.class.getName() + "#"));

        TreeSet<String> simpleNames = new TreeSet<>();
        for (String fqn : ungated) {
            simpleNames.add(fqn.substring(fqn.indexOf('#') + 1));
        }
        assertThat(simpleNames)
                .as("the five ungated ConsentController endpoints must be exactly these self-service methods")
                .containsExactlyInAnyOrder("accept", "withdraw", "history", "activePolicies", "outstanding");
    }

    // ─── Test 3 — every gate literal names an allow-listed (resource, verb) pair ──

    @Test
    void everyGateLiteralNamesAnAllowListedResourceVerbPair() {
        List<String> literals = new ArrayList<>();
        for (CutoverController controller : CONTROLLERS) {
            for (Method method : requestMappedMethods(controller.type())) {
                PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
                if (preAuthorize != null) {
                    literals.add(preAuthorize.value());
                }
            }
        }

        assertThat(literals)
                .as("the allow-list check must run over all 63 gates on the surface, never an empty set")
                .hasSize(EXPECTED_TOTAL_GATED);

        for (String literal : literals) {
            Matcher matcher = BARE_GATE.matcher(literal);
            assertThat(matcher.matches())
                    .as("%s must be a bare @rbacAuthz.hasPermission('R','V') expression", literal)
                    .isTrue();

            // An unknown literal must FAIL the test here — never be silently skipped.
            Resource resource = Resource.valueOf(matcher.group(1));
            Operation operation = Operation.valueOf(matcher.group(2));

            assertThat(resource.supports(operation))
                    .as("%s:%s is not an allow-listed (resource, verb) pair (D-10). RbacAuthz resolves "
                            + "an unsupported pair to false for every non-SUPADM caller, permanently, "
                            + "with no build error (Pitfall 3) — so a mistyped resource key or verb on "
                            + "the cutover surface must fail HERE.", resource, operation)
                    .isTrue();
        }
    }

    // ─── Test 4 — no hardcoded role gate survives on the cutover surface ──

    @Test
    void noHardcodedRoleGateSurvivesOnTheCutoverSurface() {
        List<String> offenders = new ArrayList<>();
        for (CutoverController controller : CONTROLLERS) {
            String stripped = readStrippedSource(sourcePathFor(controller.type()));
            if (stripped.contains("hasRole(") || stripped.contains("hasAnyRole(")) {
                offenders.add(controller.type().getSimpleName());
            }
        }
        assertThat(offenders)
                .as("no controller on the cutover surface may contain a hasRole(...) or "
                        + "hasAnyRole(...) call in executable (comment-stripped) source — every gate "
                        + "must be the bare @rbacAuthz.hasPermission form (SC1 / MIGRATE-02). "
                        + "Offending files: %s", offenders)
                .isEmpty();
    }

    // ─── Test 5 — deliberately retained "SUPADM" control sites still exist ──

    @Test
    void deliberatelyRetainedSupadmSitesStillExist() {
        Map<String, Integer> expectedCounts = new LinkedHashMap<>();
        expectedCounts.put("com/pranicdoc/cph/admin/AdminAuthz.java", 2);
        expectedCounts.put("com/pranicdoc/cph/auth/service/impl/HealerServiceImpl.java", 1);
        expectedCounts.put("com/pranicdoc/cph/auth/controller/AdminInviteController.java", 0);
        expectedCounts.put("com/pranicdoc/cph/rbac/RbacAuthz.java", 1);

        String rationale =
                "These sites are authorization controls the cutover must PRESERVE, not "
                        + "leftovers to sweep: AdminAuthz holds two ABAC row-scoping checks that decide "
                        + "whether a permitted admin may act on ANOTHER healer's row (healerScopeFilter "
                        + "+ assertHealerAccess); HealerServiceImpl has the self-vs-other scoping in "
                        + "setActiveStatus; RbacAuthz holds the single sanctioned SUPADM bypass the whole "
                        + "model depends on. AdminInviteController.create() is now pinned at ZERO: Phase 41 "
                        + "retired its invite-escalation guard in favour of the InviteService HEALER-only "
                        + "invariant, so no \"SUPADM\" literal may reappear in its executable source. "
                        + "Deleting any of the preserved checks would make this sweep greener and the "
                        + "system less safe — so each count is pinned exactly, and a change turns the "
                        + "build red rather than green.";

        for (Map.Entry<String, Integer> entry : expectedCounts.entrySet()) {
            Path source = Paths.get("src", "main", "java").resolve(entry.getKey());
            String stripped = readStrippedSource(source);
            assertThat(countOccurrences(stripped, "\"SUPADM\""))
                    .as("%s must retain exactly %d \"SUPADM\" literal(s) in comment-stripped source. %s",
                            entry.getKey(), entry.getValue(), rationale)
                    .isEqualTo(entry.getValue());
        }

        List<Class<?>> convertedControllersThatMustBeClean = List.of(
                AdminCreditController.class, HealerController.class, ConsentController.class,
                ConsentTypeController.class, MasterDataController.class);
        for (Class<?> type : convertedControllersThatMustBeClean) {
            assertThat(readStrippedSource(sourcePathFor(type)))
                    .as("%s is a converted controller — no \"SUPADM\" literal may appear in its "
                            + "executable source; SUPADM is handled solely by RbacAuthz's short-circuit",
                            type.getSimpleName())
                    .doesNotContain("\"SUPADM\"");
        }
    }

    // ─── Test 6 — BootstrapController stays deliberately coarse-gated (PD-40-05) ──

    @Test
    void bootstrapControllerRemainsDeliberatelyCoarseGated() {
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
                .as("PD-40-05: BootstrapController.assignRole is the deploy-time SUPADM-to-SUPADM "
                        + "role-seeding path (ASSIGN-05). Converting it to a dynamic grant would be "
                        + "circular — it is how the first grants come into existence, so gating it on a "
                        + "grant would make the system unbootstrappable. It is deliberately excluded "
                        + "from the cutover surface and pinned here so the exception cannot drift into "
                        + "an accidental conversion OR an accidental removal. "
                        + "BootstrapControllerTest.ASSIGN_ROLE_LITERAL pins the same string and must "
                        + "stay green.")
                .isEqualTo("hasRole('SUPADM')");
    }

    // ─── Test 7 — the 7 Phase-42 healer controllers are swept elsewhere, not here ──

    @Test
    void healerSurfaceControllersAreSweptByThePhase42Class() {
        List<String> phase40 = CONTROLLERS.stream()
                .map(c -> c.type().getName())
                .collect(Collectors.toList());

        for (Class<?> healer : PHASE_42_HEALER_CONTROLLERS) {
            assertThat(phase40)
                    .as("%s is a Phase-42 healer controller and must NOT appear in this class's "
                            + "Phase-40 CONTROLLERS list — the two sweeps cover disjoint sets, which "
                            + "is what keeps them independently blamable (D-15 Option B / D-21)",
                            healer.getSimpleName())
                    .doesNotContain(healer.getName());

            assertThat(requestMappedMethods(healer))
                    .as("%s has no request-mapped method — a renamed or gutted healer controller "
                            + "must redden the build", healer.getSimpleName())
                    .isNotEmpty();
        }

        String sweepSource = readStrippedSource(testSourcePathFor(HealerControllerGateSweepTest.class));
        for (Class<?> healer : PHASE_42_HEALER_CONTROLLERS) {
            assertThat(sweepSource)
                    .as("SC1 requires the cross-controller cutover sweep to be extended to include "
                            + "the 7 healer controllers (D-15 Option B / D-21). Their per-controller "
                            + "counts live in HealerControllerGateSweepTest; this assertion only "
                            + "guarantees none of them is forgotten by both sweeps at once. Missing "
                            + "from the Phase-42 sweep source: %s", healer.getSimpleName())
                    .contains(healer.getSimpleName());
        }
    }

    // ─── Test 8 — repo-wide @RestController census: everything is claimed (D-18 / A3) ──

    @Test
    void everyRestControllerIsClaimedByExactlyOneSweepOrAnExplicitExclusion() {
        Path root = Paths.get("src", "main", "java");
        Pattern restControllerAnnotation = Pattern.compile("@RestController\\b");

        TreeSet<String> found = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        if (restControllerAnnotation.matcher(readStrippedSource(p)).find()) {
                            String rel = root.relativize(p).toString().replace('\\', '/');
                            found.add(rel.substring(0, rel.length() - ".java".length()).replace('/', '.'));
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        TreeSet<String> claimed = new TreeSet<>();
        CONTROLLERS.forEach(c -> claimed.add(c.type().getName()));
        PHASE_42_HEALER_CONTROLLERS.forEach(c -> claimed.add(c.getName()));
        claimed.addAll(DELIBERATELY_UNSWEPT.keySet());

        String exclusions = DELIBERATELY_UNSWEPT.entrySet().stream()
                .map(e -> "  - " + e.getKey() + " — " + e.getValue())
                .collect(Collectors.joining("\n"));

        assertThat(found)
                .as("every @RestController in src/main/java must be claimed by exactly one sweep or "
                        + "an explicit exclusion (D-18 / research assumption A3 — automating the "
                        + "Phase 40 repo-wide comment-blind grep). The 9 Phase-40 cutover "
                        + "controllers are swept by THIS class; the 7 Phase-42 healer controllers by "
                        + "HealerControllerGateSweepTest; everything else must be in "
                        + "DELIBERATELY_UNSWEPT with a reason:\n%s\n"
                        + "A newly-added @RestController failing here must be classified: sweep it "
                        + "in HealerControllerGateSweepTest, or record why it carries no capability "
                        + "gate. If it is healer-facing and healerId-scoped it is an unplanned "
                        + "gating surface — do NOT paper over it with an exclusion entry.", exclusions)
                .containsExactlyInAnyOrderElementsOf(claimed);

        // ROADMAP out-of-scope boundary, asserted rather than assumed.
        assertThat(countOccurrences(
                readStrippedSource(sourcePathFor(DashboardController.class)), "PreAuthorize"))
                .as("DashboardController.java comment-stripped source must contain zero occurrences "
                        + "of PreAuthorize — its permission model is explicitly out of scope for "
                        + "Phase 42 (ROADMAP §Phase 42 'Explicitly out of scope'), so it must carry "
                        + "no capability gate until that separate discussion happens")
                .isEqualTo(0);
    }
}
