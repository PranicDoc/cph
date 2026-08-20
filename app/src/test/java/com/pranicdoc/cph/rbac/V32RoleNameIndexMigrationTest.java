package com.pranicdoc.cph.rbac;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain-JUnit (no Spring context) gate over
 * {@code V32__add_case_insensitive_role_name_index.sql}'s additive-only,
 * index-only shape (ROLE-10 / D-04).
 * <p>
 * This class deliberately carries zero Spring or Mockito annotations and
 * runs in milliseconds. Booting a full Spring application context is
 * currently broken in this repo (see {@code TenantFilterIntegrationTest},
 * which fails at {@code PropertyPlaceholderHelper}), and
 * {@code application-test.yaml} disables Flyway entirely because H2 cannot
 * parse {@code TIMESTAMPTZ}. So this migration's correctness is proven by
 * reading the raw file off the classpath and asserting on its parsed
 * content, never by booting Spring or applying the migration -- the same
 * approach {@link RbacSchemaMigrationTest} already established for V31.
 */
class V32RoleNameIndexMigrationTest {

    private static final String RESOURCE_PATH = "db/migration/V32__add_case_insensitive_role_name_index.sql";

    /** The migration file verbatim, exactly as it ships. */
    private static String RAW;

    /**
     * {@link #RAW} with every {@code --} line comment and every trailing
     * {@code -- ...} comment stripped. Every keyword assertion in this class
     * runs against this view, never {@code RAW} -- this codebase has
     * repeatedly tripped its own grep-style gates on prose living inside SQL
     * comments (documented in STATE.md for plans 33-03, 33-04, 33-05,
     * 34-01, 35-02, 35-03, 35-05).
     */
    private static String SQL;

    @BeforeAll
    static void loadMigration() throws IOException {
        try (InputStream in = V32RoleNameIndexMigrationTest.class.getClassLoader()
                .getResourceAsStream(RESOURCE_PATH)) {
            assertNotNull(in, "Migration resource not found on classpath: " + RESOURCE_PATH);
            RAW = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        SQL = stripComments(RAW);
    }

    // -------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------

    @Test
    void migrationFileExistsOnClasspath() {
        assertNotNull(RAW);
        assertFalse(RAW.isBlank());
    }

    @Test
    void createsCaseInsensitiveUniqueIndexOnRoleName() {
        assertTrue(SQL.toUpperCase().contains("CREATE UNIQUE INDEX"),
                "Expected a CREATE UNIQUE INDEX statement");
        assertTrue(SQL.contains("uq_rbac_role_name_ci"),
                "Expected the index name uq_rbac_role_name_ci");
        assertTrue(SQL.contains("LOWER(name)"),
                "Expected the functional index expression LOWER(name)");
        assertTrue(SQL.contains("rbac_role"),
                "Expected the index to target the rbac_role table");
    }

    @Test
    void isAdditiveOnly() {
        String upper = SQL.toUpperCase();
        for (String forbidden : java.util.List.of("DROP ", "ALTER TABLE", "TRUNCATE", "UPDATE ", "DELETE FROM")) {
            assertFalse(upper.contains(forbidden), "Found destructive statement: " + forbidden);
        }
    }

    @Test
    void createsNoTablesAndSeedsNoRows() {
        String upper = SQL.toUpperCase();
        assertFalse(upper.contains("CREATE TABLE"), "V32 is index-only -- must not create tables");
        assertFalse(upper.contains("INSERT INTO"), "V32 is index-only -- must not seed rows");
    }

    // -------------------------------------------------------------------
    // Parsing helpers -- copied verbatim from RbacSchemaMigrationTest.
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
