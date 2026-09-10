package com.pranicdoc.cph.healing.controller;

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
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Annotation-level authorization contract for {@link FeedbackController} — the inverse
 * shape (plan 42-05, SC4, D-07/D-19).
 *
 * <p>{@code FeedbackController}'s two POSTs ({@code saveSymptomFeedback},
 * {@code saveQualitativeFeedback}) are the live patient feedback submission path, reachable
 * only via a {@code PATIENT}-role token (cph/ui {@code Feedback.tsx}). A capability gate
 * there returns {@code false} for every real caller ({@code getCurrentRole() == "PATIENT"},
 * owning healer holds no grants) and would 403 every patient feedback submission (D-19).
 * They are therefore deliberately <em>ungated</em>, and — per D-07, mirroring
 * {@code LegacyEndpointCutoverSweepTest} Test 2 — that openness is proven by <em>absence</em>
 * asserted by name plus an anti-vacuous total count, not by a {@code permitAll()} marker.
 *
 * <p>Pure reflection plus a comment-stripped source read (the source-stripping shape is
 * copied from {@code LegacyEndpointCutoverSweepTest.readStrippedSource} — this file's
 * javadoc/comments now discuss {@code @PreAuthorize}, so an unstripped scan would be
 * self-invalidating). No Spring context, no {@code @WebMvcTest}, no {@code MockMvc}.
 */
class FeedbackControllerAuthorizationContractTest {

    private static final int EXPECTED_MAPPED = 2;
    private static final int EXPECTED_GATED = 0;

    private static final List<Class<? extends Annotation>> REQUEST_MAPPING_ANNOTATIONS = List.of(
            GetMapping.class, PostMapping.class, PutMapping.class, PatchMapping.class,
            DeleteMapping.class, RequestMapping.class);

    private static boolean isRequestMapped(Method m) {
        return REQUEST_MAPPING_ANNOTATIONS.stream().anyMatch(a -> m.getAnnotation(a) != null);
    }

    /** Same comment-stripping as {@code LegacyEndpointCutoverSweepTest}: block/javadoc comments, then line comments. */
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
        return raw
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("//.*", "");
    }

    // ─── Test 1 — 2 mapped, 0 gated, and the ungated set is exactly the two feedback POSTs ──

    @Test
    void bothFeedbackPostsAreDeliberatelyUngatedByName() {
        int inspected = 0;
        int gated = 0;
        List<String> ungated = new ArrayList<>();

        for (Method method : FeedbackController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic() || !isRequestMapped(method)) {
                continue;
            }
            inspected++;
            if (method.getAnnotation(PreAuthorize.class) != null) {
                gated++;
            } else {
                ungated.add(method.getName());
            }
        }

        assertThat(inspected)
                .as("FeedbackController has exactly 2 request-mapped methods — a method added or removed "
                        + "here must redden the build rather than silently pass")
                .isEqualTo(EXPECTED_MAPPED);
        assertThat(gated)
                .as("FeedbackController must carry ZERO capability gates (D-19): both POSTs are reached only "
                        + "via a PATIENT-role token, so @rbacAuthz.hasPermission(...) returns false for every "
                        + "real caller and a gate would 403 every patient feedback submission")
                .isEqualTo(EXPECTED_GATED);
        assertThat(new TreeSet<>(ungated))
                .as("the deliberately-open FeedbackController endpoints must be exactly these two patient "
                        + "feedback POSTs (D-19: reached by a PATIENT-role token — @rbacAuthz returns false for "
                        + "every real caller). Any OTHER ungated method here, or a missing one, is a contract "
                        + "drift. Found: %s", ungated)
                .hasSize(2)
                .containsExactlyInAnyOrder("saveSymptomFeedback", "saveQualitativeFeedback");
    }

    // ─── Test 2 — openness asserted by source absence, not a marker annotation (D-07) ──

    @Test
    void feedbackControllerSourceHasNoGateAndNoPermitAllMarker() {
        Path source = Paths.get("src", "main", "java",
                "com", "pranicdoc", "cph", "healing", "controller", "FeedbackController.java");
        String stripped = readStrippedSource(source);

        assertThat(stripped)
                .as("FeedbackController executable (comment-stripped) source must contain no @PreAuthorize — "
                        + "openness is by absence (D-07), and any gate 403s every patient feedback submission (D-19)")
                .doesNotContain("PreAuthorize");
        assertThat(stripped)
                .as("FeedbackController must NOT carry a permitAll() marker annotation — D-07 asserts openness "
                        + "by absence plus a named allow-list test, matching the Phase 40 source-sweep style")
                .doesNotContain("permitAll");
    }

    // ─── Test 3 — FEEDBACK verb set is exactly {CREATE}; FEEDBACK:READ is not allow-listed (D-19) ──

    @Test
    void feedbackResourceSupportsOnlyCreateAndNotRead() {
        assertThat(Resource.FEEDBACK.getSupportedOperations())
                .as("Resource.FEEDBACK allow-lists exactly {CREATE} (D-19, amending D-01's {READ, CREATE}) — "
                        + "FEEDBACK:CREATE is enforced solely by the healer-only CaseController.resendFeedback")
                .isEqualTo(Set.of(Operation.CREATE));
        assertThat(Resource.FEEDBACK.supports(Operation.READ))
                .as("FEEDBACK:READ is NOT allow-listed (D-19). A FEEDBACK:READ gate on "
                        + "CaseController.getSymptomsForFeedback would have 403'd every patient feedback form — "
                        + "that endpoint runs on a PATIENT-role token where @rbacAuthz returns false for every "
                        + "real caller. This also proves the {CREATE} assertion above is not vacuous.")
                .isFalse();
    }
}
