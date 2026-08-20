package com.pranicdoc.cph.rbac;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain-JUnit (no Spring context) gate over {@code V31__create_rbac_schema.sql}'s
 * additive-only / tenant-free schema shape.
 * <p>
 * This class deliberately carries zero Spring or Mockito annotations and
 * runs in milliseconds. Booting a full Spring application context is
 * currently broken in this repo (see {@code TenantFilterIntegrationTest},
 * which fails at {@code PropertyPlaceholderHelper}), and {@code application-test.yaml}
 * disables Flyway entirely because H2 cannot parse {@code TIMESTAMPTZ}. So
 * this migration's correctness is proven by reading the raw file off the
 * classpath and asserting on its parsed content, never by booting Spring or
 * applying the migration.
 * <p>
 * Superseded scope note: this class originally also verified V30's seeded
 * resource/permission/resource_permission catalog (8 resources, 7 verbs, a
 * 23-row allow-list). That catalog was replaced by the static Java enums
 * {@link com.pranicdoc.cph.rbac.model.Resource}/{@link com.pranicdoc.cph.rbac.model.Operation} —
 * see {@link RbacResourceOperationCatalogTest} for the equivalent catalog
 * gate, now enforced in Java instead of DB seed data.
 */
class RbacSchemaMigrationTest {

    private static final String RESOURCE_PATH = "db/migration/V31__create_rbac_schema.sql";

    /** The migration file verbatim, exactly as it ships. */
    private static String RAW;

    /**
     * {@link #RAW} with every {@code --} line comment and every trailing
     * {@code -- ...} comment stripped. Every keyword/deny-list assertion in
     * this class runs against this view, never {@code RAW} — this codebase
     * has repeatedly tripped its own grep-style gates on prose living inside
     * SQL comments (documented in STATE.md for plans 33-03, 33-04, 33-05,
     * 34-01).
     */
    private static String SQL;

    @BeforeAll
    static void loadMigration() throws IOException {
        try (InputStream in = RbacSchemaMigrationTest.class.getClassLoader()
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
    void declaresExactlySixTables() {
        Pattern createTable = Pattern.compile("CREATE TABLE (\\w+) \\(");
        Matcher m = createTable.matcher(SQL);
        Set<String> tableNames = new java.util.LinkedHashSet<>();
        while (m.find()) {
            tableNames.add(m.group(1));
        }

        Set<String> expected = Set.of(
                "rbac_role", "role_permission", "user_role",
                "rbac_role_aud", "role_permission_aud", "user_role_aud"
        );

        assertEquals(6, tableNames.size(), "Expected 6 tables, found: " + tableNames);
        assertEquals(expected, tableNames);
    }

    @Test
    void everyBaseTableHasAnEnversShadowTable() {
        List<String> baseTables = List.of("rbac_role", "role_permission", "user_role");

        for (String base : baseTables) {
            String audTable = base + "_aud";
            String block = extractCreateTableBlock(SQL, audTable);
            assertTrue(block.contains("rev"), audTable + " missing rev column");
            assertTrue(block.contains("revtype"), audTable + " missing revtype column");
            assertTrue(block.contains("PRIMARY KEY (id, rev)"), audTable + " missing composite PK (id, rev)");
            assertTrue(block.contains("REFERENCES revinfo(rev)"), audTable + " missing FK to revinfo(rev)");
        }
    }

    @Test
    void containsNoTenantShapedColumn() {
        String lower = SQL.toLowerCase();
        for (String forbidden : List.of("tenant_id", "organization_id", "org_id", "clinic_id", "team_id")) {
            assertFalse(lower.contains(forbidden),
                    "Found tenant-shaped column '" + forbidden + "' — multi-tenancy relapse, "
                            + "see PITFALLS.md Pitfall 8");
        }
    }

    @Test
    void isAdditiveOnly() {
        String upper = SQL.toUpperCase();
        for (String forbidden : List.of("DROP ", "ALTER TABLE", "TRUNCATE", "UPDATE ", "DELETE FROM")) {
            assertFalse(upper.contains(forbidden), "Found destructive statement: " + forbidden);
        }
    }

    @Test
    void seedsNoRolesGrantsOrAssignments() {
        String noStandingPrivilegeMessage =
                "V30 must seed zero rows here — no standing privilege before enforcement + backfill, "
                        + "see PITFALLS.md Pitfall 7";
        assertFalse(SQL.contains("INSERT INTO rbac_role"), "INSERT INTO rbac_role found — " + noStandingPrivilegeMessage);
        assertFalse(SQL.contains("INSERT INTO role_permission"), "INSERT INTO role_permission found — " + noStandingPrivilegeMessage);
        assertFalse(SQL.contains("INSERT INTO user_role"), "INSERT INTO user_role found — " + noStandingPrivilegeMessage);
    }

    @Test
    void userRoleIsPolymorphicAndNotHealerBound() {
        String block = extractCreateTableBlock(SQL, "user_role");
        assertTrue(block.contains("principal_type"), "user_role missing principal_type");
        assertTrue(block.contains("principal_id"), "user_role missing principal_id");
        assertTrue(block.contains("assigned_at"), "user_role missing assigned_at");
        assertTrue(block.contains("assigned_by"), "user_role missing assigned_by");
        assertTrue(block.contains("REFERENCES rbac_role(id)"), "user_role missing FK to rbac_role(id)");
        assertFalse(block.contains("healer_id"), "user_role must not carry a healer_id column (D-08)");
        assertFalse(block.contains("REFERENCES healer("), "user_role must not FK to healer (D-08)");
    }

    @Test
    void rolePermissionCarriesResourceAndOperationDirectly() {
        String block = extractCreateTableBlock(SQL, "role_permission");
        assertTrue(block.contains("resource"), "role_permission missing resource column");
        assertTrue(block.contains("operation"), "role_permission missing operation column");

        // No catalog-table FK columns from the superseded design — the
        // allow-list check now lives in Java (Resource.supports(Operation)),
        // not a DB FK to a resource_permission table.
        assertFalse(block.contains("resource_permission_id"),
                "role_permission must not carry resource_permission_id — superseded by direct resource/operation columns");
        Pattern barePermissionId = Pattern.compile("(?<![A-Za-z0-9_])permission_id");
        assertFalse(barePermissionId.matcher(block).find(),
                "role_permission must not declare a permission_id column — superseded by direct resource/operation columns");
    }

    @Test
    void rbacRoleHasNoIsSystemFlag() {
        String block = extractCreateTableBlock(SQL, "rbac_role");
        assertFalse(block.contains("is_system"), "rbac_role must not carry an is_system column (PD-03)");
        assertFalse(block.contains("system_defined"), "rbac_role must not carry a system_defined column (PD-03)");
    }

    // -------------------------------------------------------------------
    // Parsing helpers — deliberately hand-rolled and local to this class,
    // no SQL-parser dependency added.
    // -------------------------------------------------------------------

    private static String stripComments(String raw) {
        StringBuilder out = new StringBuilder();
        for (String line : raw.split("\n", -1)) {
            int idx = line.indexOf("--");
            out.append(idx >= 0 ? line.substring(0, idx) : line).append('\n');
        }
        return out.toString();
    }

    private static String extractBlock(String sql, String prefix, String terminator) {
        int start = sql.indexOf(prefix);
        assertTrue(start >= 0, "Expected to find: " + prefix);
        int end = sql.indexOf(terminator, start);
        assertTrue(end >= 0, "Expected terminator '" + terminator + "' after '" + prefix + "'");
        return sql.substring(start, end);
    }

    private static String extractCreateTableBlock(String sql, String tableName) {
        return extractBlock(sql, "CREATE TABLE " + tableName + " (", ");");
    }
}
