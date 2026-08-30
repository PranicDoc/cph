package com.pranicdoc.cph.rbac;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain-JUnit (no Spring context) gate over
 * {@code V35__shrink_role_enum_check_constraints.sql} — Phase 41
 * (MIGRATE-01 / MIGRATE-05), ROADMAP SC2.
 * <p>
 * This class deliberately carries zero Spring or Mockito annotations and
 * boots no application context. Loading a full Spring context is currently
 * broken repo-wide (see {@code TenantFilterIntegrationTest}, which fails at
 * {@code PropertyPlaceholderHelper}), and the test profile disables database
 * migration replay entirely because H2 cannot parse {@code TIMESTAMPTZ} or
 * the repo's partial indexes. Adding a Postgres Testcontainer for a
 * three-line constraint swap is not proportionate. So V35's correctness is
 * proven by reading the raw file off the classpath and asserting on its
 * comment-stripped content — the same approach
 * {@link V34RoleNameIndexMigrationTest} and {@link RbacSchemaMigrationTest}
 * already established. "Applies cleanly" is discharged outside automated
 * tests: local dev DB on app startup, then the operator for staging/prod
 * after the D-03 checkpoint (both recorded in 41-VALIDATION.md).
 * <p>
 * Every keyword assertion runs against {@link #SQL} (comment-stripped),
 * never {@link #RAW}. This codebase has repeatedly tripped its own
 * grep-style gates on prose living inside SQL comments (STATE.md plans
 * 33-03/04/05, 34-01, 35-02/03/05). The header's D-03 pre-flight query names
 * the retired role values on purpose; comment stripping is exactly why
 * {@link #namesNoRetiredRoleValue()} still passes.
 * <p>
 * NOTE: this class does NOT reuse {@code V34RoleNameIndexMigrationTest}'s
 * {@code isAdditiveOnly()} — that method forbids {@code DROP } and
 * {@code ALTER TABLE} outright, which is correct for an index-creation
 * migration and exactly wrong for a constraint swap.
 * {@link #isConstraintSwapOnlyWithNoRowDml()} is the V35-appropriate check.
 */
class V35RoleConstraintMigrationTest {

    private static final String RESOURCE_PATH = "db/migration/V35__shrink_role_enum_check_constraints.sql";

    /** The migration file verbatim, exactly as it ships. */
    private static String RAW;

    /**
     * {@link #RAW} with every {@code --} line comment and trailing
     * {@code -- ...} comment stripped. Every content assertion runs against
     * this view, never {@link #RAW}.
     */
    private static String SQL;

    @BeforeAll
    static void loadMigration() throws IOException {
        try (InputStream in = V35RoleConstraintMigrationTest.class.getClassLoader()
                .getResourceAsStream(RESOURCE_PATH)) {
            assertNotNull(in, "Migration resource not found on classpath: " + RESOURCE_PATH);
            RAW = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        SQL = stripComments(RAW);
    }

    // -------------------------------------------------------------------
    // Tests — one assertion theme each; every message names the decision
    // it protects.
    // -------------------------------------------------------------------

    @Test
    void migrationFileExistsOnClasspath() {
        assertNotNull(RAW, "V35 must exist on the classpath at " + RESOURCE_PATH);
        assertFalse(RAW.isBlank(), "V35 must not be blank — it is the DB backstop realigning to the two-value Role enum");
    }

    @Test
    void tightensHealerRoleConstraintToTheTwoSurvivingValues() {
        assertTrue(SQL.contains("DROP CONSTRAINT ck_healer_role"),
                "MIGRATE-01: V35 must drop the old five-value ck_healer_role so the DB stops admitting retired role values");
        assertTrue(SQL.contains("ADD CONSTRAINT ck_healer_role"),
                "The healer table must keep a DB CHECK backstop — V35 re-adds ck_healer_role, only narrower");
        assertTrue(SQL.contains("role IN ('HEALER','SUPADM')"),
                "T-41-03: the re-added ck_healer_role must admit exactly HEALER and SUPADM, mirroring the shrunk "
                        + "com.pranicdoc.cph.common.enums.Role — this is the build-time proof the constraint really is that narrow");
    }

    @Test
    void dropsHealerAudRoleConstraintWithNoReplacement() {
        assertTrue(SQL.contains("DROP CONSTRAINT ck_healer_aud_role"),
                "D-01: V35 must drop ck_healer_aud_role");
        assertFalse(SQL.contains("ADD CONSTRAINT ck_healer_aud_role"),
                "D-01 / T-41-05: ck_healer_aud_role is dropped with NO replacement — an Envers append-only audit shadow "
                        + "holding a historical dead value must never be able to block this migration mid-deploy");
    }

    @Test
    void namesNoRetiredRoleValue() {
        assertFalse(SQL.contains("SENIOR_HEALER"),
                "No retired role value may appear in the executable SQL (D-01). The header D-03 pre-flight query does "
                        + "name them, but it is a comment and is stripped before this assertion runs.");
        assertFalse(SQL.contains("RECEPTIONIST"),
                "No retired role value may appear in the executable SQL (D-01) — comment stripping is exactly why this passes.");
        assertFalse(SQL.contains("'ADMIN'"),
                "No retired role literal may appear in the executable SQL (D-01). The single intended occurrence is the "
                        + "header D-03 pre-flight query line, which is a comment and is stripped first.");
    }

    @Test
    void isConstraintSwapOnlyWithNoRowDml() {
        String upper = SQL.toUpperCase();
        for (String forbidden : List.of("UPDATE ", "DELETE FROM", "INSERT INTO", "TRUNCATE")) {
            assertFalse(upper.contains(forbidden),
                    "D-04: V35 is a constraint swap only — found row DML '" + forbidden + "'. No UPDATE/DELETE of row "
                            + "data means rollback stays a pure inverse constraint change, not a data-restore.");
        }
    }

    @Test
    void touchesOnlyTheHealerAndHealerAudTables() {
        Matcher m = Pattern.compile("ALTER TABLE (\\w+)").matcher(SQL);
        int captures = 0;
        Set<String> tables = new LinkedHashSet<>();
        while (m.find()) {
            captures++;
            tables.add(m.group(1));
        }
        assertTrue(captures >= 2,
                "Expected at least two ALTER TABLE statements (healer + healer_aud) so the regex cannot pass vacuously — got "
                        + captures + " " + tables);
        for (String table : tables) {
            assertTrue(Set.of("healer", "healer_aud").contains(table),
                    "D-05: V35 must touch the healer and healer_aud role constraints ONLY — healer_invite_link has no DB "
                            + "CHECK and is app-layer validated. Found ALTER TABLE " + table);
        }
    }

    // -------------------------------------------------------------------
    // Parsing helper — copied verbatim from V34RoleNameIndexMigrationTest.
    // -------------------------------------------------------------------

    private static String stripComments(String raw) {
        StringBuilder out = new StringBuilder();
        for (String line : raw.split("\n", -1)) {
            int idx = line.indexOf("--");
            out.append(idx >= 0 ? line.substring(0, idx) : line).append('\n');
        }
        return out.toString();
    }
}
