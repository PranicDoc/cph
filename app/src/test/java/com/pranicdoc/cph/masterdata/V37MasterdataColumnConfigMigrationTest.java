package com.pranicdoc.cph.masterdata;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain-JUnit (no Spring context) gate over
 * {@code V37__create_masterdata_column_config_table.sql} — Phase 01
 * (masterdata-import-redesign), plan 01-01.
 * <p>
 * Full Spring context boot ({@code @SpringBootTest}) is broken repo-wide
 * (see {@code TenantFilterIntegrationTest}'s {@code PropertyPlaceholderHelper}
 * failure, cited in {@code V35RoleConstraintMigrationTest}/
 * {@code RbacRepositoryQueryDerivationTest}), and the test profile disables
 * Flyway replay entirely ({@code spring.flyway.enabled: false} — H2 cannot
 * parse this repo's Postgres-only DDL). So V37's correctness is proven by
 * reading the raw file off the classpath and asserting on its
 * comment-stripped content, exactly mirroring
 * {@code V35RoleConstraintMigrationTest}'s established pattern.
 * <p>
 * Every keyword/count assertion runs against {@link #SQL} (comment-stripped),
 * never {@link #RAW}.
 */
class V37MasterdataColumnConfigMigrationTest {

    private static final String RESOURCE_PATH = "db/migration/V37__create_masterdata_column_config_table.sql";

    /** The migration file verbatim, exactly as it ships. */
    private static String RAW;

    /**
     * {@link #RAW} with every {@code --} line comment stripped. Every
     * content assertion runs against this view, never {@link #RAW}.
     */
    private static String SQL;

    @BeforeAll
    static void loadMigration() throws IOException {
        try (InputStream in = V37MasterdataColumnConfigMigrationTest.class.getClassLoader()
                .getResourceAsStream(RESOURCE_PATH)) {
            assertNotNull(in, "Migration resource not found on classpath: " + RESOURCE_PATH);
            RAW = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        SQL = stripComments(RAW);
    }

    @Test
    void migrationFileExistsOnClasspath() {
        assertNotNull(RAW, "V37 must exist on the classpath at " + RESOURCE_PATH);
        assertFalse(RAW.isBlank(), "V37 must not be blank — it creates the masterdata column-config registry");
    }

    @Test
    void createsBaseTableAndEnversShadowTable() {
        assertTrue(SQL.contains("CREATE TABLE masterdata_column_config"),
                "V37 must create the masterdata_column_config base table");
        assertTrue(SQL.contains("CREATE TABLE masterdata_column_config_aud"),
                "Pitfall 4: BaseEntity is @Audited at the mapped-superclass level — the _aud shadow table "
                        + "must exist in the same migration or the very first insert fails with SQLGrammarException");
    }

    @Test
    void hasUniqueConstraintOnTypeAndColumnName() {
        assertTrue(SQL.contains("CREATE UNIQUE INDEX uq_masterdata_column_config_type_column"),
                "A column-config row must be unique per (masterdata_type, column_name) among live rows");
        assertTrue(SQL.contains("ON masterdata_column_config (masterdata_type, column_name)"),
                "The uniqueness index must be scoped to (masterdata_type, column_name)");
        assertTrue(SQL.contains("WHERE is_deleted = FALSE"),
                "CR-01: a plain table-level UNIQUE constraint has no is_deleted awareness and would permanently "
                        + "block re-adding a column name after a real soft-delete (MDIMPORT-03 / Pitfall 1) — "
                        + "the uniqueness guarantee must be a partial index scoped to live rows only");
    }

    @Test
    void enversShadowTableReferencesRevinfo() {
        assertTrue(SQL.contains("REFERENCES revinfo(rev)"),
                "The _aud shadow table must FK to revinfo(rev), matching V27/V33's Envers shape");
    }

    @Test
    void hasNoCheckConstraintOnEnumBackedColumns() {
        assertFalse(SQL.contains("CHECK"),
                "Anti-pattern guard: V7's CHECK-constraint-on-enum-column approach was superseded by V33's "
                        + "Java-enum-only enforcement (Enum.valueOf at the JPA layer) — no CHECK belongs here");
    }

    @Test
    void isAdditiveOnlyNoRowDmlBeyondTheSeedInsert() {
        String upper = SQL.toUpperCase();
        assertFalse(upper.contains("UPDATE "), "V37 must be additive-only — no UPDATE statement");
        assertFalse(upper.contains("DELETE FROM"), "V37 must be additive-only — no DELETE FROM statement");
    }

    @Test
    void seedsExactlySevenProtocolRows() {
        assertEquals(7, countSeedRowsForType("PROTOCOL"),
                "MDIMPORT-05/D-08: exactly 7 system-defined PROTOCOL column-config rows must be seeded");
    }

    @Test
    void seedsExactlyFourPranaColorRows() {
        assertEquals(4, countSeedRowsForType("PRANA_COLOR"),
                "MDIMPORT-05/D-08: exactly 4 system-defined PRANA_COLOR column-config rows must be seeded "
                        + "(no Type row — PROPERTY/APPLICATION discriminator is row-level importer logic per D-06)");
    }

    @Test
    void seedsExactlyFourChakraRows() {
        assertEquals(4, countSeedRowsForType("CHAKRA"),
                "MDIMPORT-05/D-08: exactly 4 system-defined CHAKRA column-config rows must be seeded "
                        + "(Chakra had no prior importer — field list grounded in ChakraCatalog.java, Pitfall 5)");
    }

    @Test
    void seedsExactlyFourProtocolColorMappingRows() {
        assertEquals(4, countSeedRowsForType("PROTOCOL_COLOR_MAPPING"),
                "MDIMPORT-05/D-08: exactly 4 system-defined PROTOCOL_COLOR_MAPPING column-config rows must be seeded "
                        + "(no prior importer — field list grounded in ProtocolColorMapping.java, Pitfall 5)");
    }

    @Test
    void seedsNineteenRowsTotalAcrossAllFourTypes() {
        int total = countSeedRowsForType("PROTOCOL") + countSeedRowsForType("PRANA_COLOR")
                + countSeedRowsForType("CHAKRA") + countSeedRowsForType("PROTOCOL_COLOR_MAPPING");
        assertEquals(19, total, "MDIMPORT-05: 19 total system-defined seed rows across all 4 masterdata types");
    }

    // -------------------------------------------------------------------
    // Helper — counts occurrences of the quoted enum literal 'TYPE' inside
    // the seed INSERT's VALUES rows, excluding the masterdata_type
    // VARCHAR(40) column-definition line (which never carries a quoted
    // literal, but is explicitly excluded here per the plan's spec so the
    // count is provably about seed rows, not column declarations).
    // -------------------------------------------------------------------

    private static int countSeedRowsForType(String type) {
        String literal = "'" + type + "'";
        int count = 0;
        for (String line : SQL.split("\n", -1)) {
            if (line.contains("masterdata_type VARCHAR(40)")) {
                continue; // column-definition line — never a seed row
            }
            Matcher m = Pattern.compile(Pattern.quote(literal)).matcher(line);
            while (m.find()) {
                count++;
            }
        }
        return count;
    }

    // -------------------------------------------------------------------
    // Parsing helper — copied verbatim from V35RoleConstraintMigrationTest.
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
