package com.pranicdoc.cph.rbac;

import com.pranicdoc.cph.auth.controller.AuthController;
import com.pranicdoc.cph.auth.controller.BootstrapController;
import com.pranicdoc.cph.consent.controller.ReadingSessionController;
import com.pranicdoc.cph.notification.controller.WhatsappWebhookController;
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
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

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
     * The 7 {@code AuthController} routes that are {@code permitAll} in
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
            "getCurrentUser", "getCurrentHealer", "updateProfile", "changePassword",
            "linkGoogle", "unlinkGoogle", "clearPatientSession");

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
                .isEqualTo(14);

        TreeSet<String> ungated = ungatedMethodNames(AuthController.class);
        assertThat(ungated)
                .as("every AuthController request-mapped method must carry NO @PreAuthorize — D-07: "
                        + "openness is proven by annotation absence plus this named allow-list, never "
                        + "a @PreAuthorize(\"permitAll()\") marker annotation")
                .hasSize(14)
                .containsExactlyInAnyOrder(
                        "login", "refresh", "logout", "acceptInvite", "signup", "initSession",
                        "initFeedbackSession", "getCurrentUser", "getCurrentHealer", "updateProfile",
                        "changePassword", "linkGoogle", "unlinkGoogle", "clearPatientSession");

        assertThat(AUTH_PERMIT_ALL_ROUTES)
                .as("pre-auth 7 — permitAll in SecurityConfig; there is no authenticated principal "
                        + "yet, so a capability gate could never resolve and would lock every caller "
                        + "out of login/signup (T-42-DoS-O1)")
                .hasSize(7)
                .allMatch(ungated::contains);

        assertThat(AUTH_SELF_SERVICE_ROUTES)
                .as("self-service 7 — every authenticated principal must reach their own identity "
                        + "and profile without a grant, the same posture Phase 40 locked for "
                        + "ConsentController's self-service endpoints; gating /me or /profile would "
                        + "break a freshly-onboarded healer who holds no grants at all (T-42-DoS-O2)")
                .hasSize(7)
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
}
