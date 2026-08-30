package com.pranicdoc.cph.rbac;

import com.pranicdoc.cph.common.enums.Role;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RoleEnumRetirementSweepTest — the permanent Phase 41 / MIGRATE-01 / SC1 build gate.
 *
 * <p>It exists so that a future PR which reintroduces {@code ADMIN},
 * {@code SENIOR_HEALER}, or {@code RECEPTIONIST} as a static login-tier role value
 * goes red at build time rather than silently re-widening the privilege model.
 * Two independent proofs:
 * <ul>
 *   <li>Reflection — {@link Role#values()} is exactly {@code {HEALER, SUPADM}}.</li>
 *   <li>Source sweep — none of a fixed nine-file {@code src/main/java} scope names a
 *       retired tier, in comments or code.</li>
 * </ul>
 *
 * <p><b>Scope is a fixed file list on purpose (PD-41-05).</b> The gate never walks
 * {@code db/migration/}: applied migrations {@code V0__baseline_schema.sql} and
 * {@code V7__add_enum_check_constraints.sql} carry immutable historical comments and
 * a superseded {@code CHECK} that name all five original tiers. Editing an applied
 * migration breaks Flyway checksum validation in every environment, so those files
 * are out of scope by design, not by a fragile regex escape. The broader repo-wide
 * "full-codebase search" half of SC1 is discharged separately in plan 41-05 as a
 * recorded, categorized, comment-blind sweep.
 *
 * <p>Deliberately zero Spring, zero Mockito — booting an application context is
 * broken repo-wide and {@code application-test.yaml} disables Flyway (H2 cannot
 * parse {@code TIMESTAMPTZ}). This class reads {@code .java} source off disk and
 * asserts on parsed / comment-stripped text, and runs in milliseconds. Same idiom
 * as {@code LegacyEndpointCutoverSweepTest} and
 * {@code RbacAuthzTest#supadmBypassExistsInExactlyOnePlace}.
 */
class RoleEnumRetirementSweepTest {

    /**
     * The nine role-relevant {@code src/main/java} sources, module-relative below
     * {@code src/main/java} (PD-41-05). Adding a role-touching file = one edit here.
     */
    private static final List<String> SCOPE = List.of(
            "com/pranicdoc/cph/common/enums/Role.java",
            "com/pranicdoc/cph/common/security/JwtService.java",
            "com/pranicdoc/cph/common/security/JwtAuthFilter.java",
            "com/pranicdoc/cph/rbac/model/RbacRole.java",
            "com/pranicdoc/cph/auth/service/impl/InviteService.java",
            "com/pranicdoc/cph/auth/controller/AdminInviteController.java",
            "com/pranicdoc/cph/auth/controller/BootstrapController.java",
            "com/pranicdoc/cph/auth/dto/InviteCreateRequest.java",
            "com/pranicdoc/cph/auth/dto/BootstrapRequest.java");

    private static final Path SOURCE_ROOT = Paths.get("src", "main", "java");

    /** {@code SENIOR_HEALER} / {@code RECEPTIONIST} as whole words. */
    private static final Pattern DEAD_TIER_WORD = Pattern.compile("\\b(SENIOR_HEALER|RECEPTIONIST)\\b");

    /**
     * {@code ADMIN} as a standalone token. The lookarounds are load-bearing: they must
     * NOT match {@code ADMIN_INVITE} (a live {@code HealerInviteLink.Source} value) nor
     * {@code ADMIN-01}-style requirement tags (which live in comments and get stripped
     * anyway), and must NOT match the {@code ADMIN} inside {@code SUPADM}.
     */
    private static final Pattern ADMIN_WORD = Pattern.compile("(?<![A-Za-z0-9_])ADMIN(?![A-Za-z0-9_])");

    // ─── helpers ────────────────────────────────────────────────────────

    private static Path resolve(String moduleRelative) {
        return SOURCE_ROOT.resolve(moduleRelative);
    }

    /** Reads the file verbatim, asserting it is a real regular file first. */
    private static String readRawSource(Path source) {
        assertThat(Files.isRegularFile(source))
                .as("expected source file at %s", source.toAbsolutePath())
                .isTrue();
        try {
            return Files.readString(source);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * {@link #readRawSource} then the repo's standard two-regex Java comment stripper
     * (block/javadoc comments first, then line comments), copied verbatim from
     * {@code RbacAuthzTest}. Only comments are removed — string literals are NOT
     * stripped and remain subject to the scan.
     */
    private static String readStrippedSource(Path source) {
        return readRawSource(source)
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("//.*", "");
    }

    // ─── Test 1 — the enum itself, by reflection ────────────────────────

    @Test
    void roleEnumContainsExactlyHealerAndSupadm() {
        assertThat(Role.values())
                .containsExactlyInAnyOrder(Role.HEALER, Role.SUPADM)
                .hasSize(2);
    }

    // ─── Test 2 — anti-vacuous guard: the scope list is nine real files ─

    @Test
    void scopeListResolvesToNineRealSourceFiles() {
        assertThat(SCOPE)
                .as("PD-41-05 fixes the SC1 sweep scope at nine role-relevant sources")
                .hasSize(9)
                .doesNotHaveDuplicates();

        List<String> missing = new ArrayList<>();
        for (String moduleRelative : SCOPE) {
            if (!Files.isRegularFile(resolve(moduleRelative))) {
                missing.add(moduleRelative);
            }
        }
        assertThat(missing)
                .as("every scope entry must resolve to an existing regular file under %s — "
                        + "a typo here would silently make the sweep vacuous", SOURCE_ROOT)
                .isEmpty();
    }

    // ─── Test 3 — no retired tier name in RAW source (comments included) ─

    @Test
    void noRetiredTierNameSurvivesInRoleRelevantSources() {
        List<String> offenders = new ArrayList<>();
        for (String moduleRelative : SCOPE) {
            Path source = resolve(moduleRelative);
            String fileName = source.getFileName().toString();
            String[] lines = readRawSource(source).split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (DEAD_TIER_WORD.matcher(lines[i]).find()) {
                    offenders.add(fileName + ":" + (i + 1));
                }
            }
        }
        assertThat(offenders)
                .as("no role-relevant source may name SENIOR_HEALER or RECEPTIONIST anywhere, "
                        + "including javadoc — after Phase 41 there is no legitimate reason for "
                        + "these nine files to mention a retired tier even in prose. Offenders: %s",
                        offenders)
                .isEmpty();
    }

    // ─── Test 4 — no ADMIN role value in comment-stripped source ────────

    @Test
    void noAdminRoleValueSurvivesInRoleRelevantSources() {
        List<String> offenders = new ArrayList<>();
        for (String moduleRelative : SCOPE) {
            Path source = resolve(moduleRelative);
            String fileName = source.getFileName().toString();
            Matcher matcher = ADMIN_WORD.matcher(readStrippedSource(source));
            while (matcher.find()) {
                offenders.add(fileName + " @" + matcher.start());
            }
        }
        assertThat(offenders)
                .as("no role-relevant source may use ADMIN as a standalone token in executable "
                        + "(comment-stripped) source — ADMIN-01 requirement tags live in javadoc "
                        + "and are stripped, ADMIN_INVITE is excluded by the lookahead, and "
                        + "Operation.ADMIN is an unrelated RBAC verb elsewhere. Offenders: %s",
                        offenders)
                .isEmpty();
    }
}
