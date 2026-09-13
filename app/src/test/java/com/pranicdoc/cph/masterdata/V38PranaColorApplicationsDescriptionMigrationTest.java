package com.pranicdoc.cph.masterdata;

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
 * {@code V38__add_prana_color_applications_and_description.sql} — Phase 02
 * (masterdata-import-redesign), plan 02-02 (D-14/D-14b).
 * <p>
 * Full Spring context boot is broken repo-wide and the test profile disables
 * Flyway replay entirely (H2 cannot parse this repo's Postgres-only DDL), so
 * V38's correctness is proven by reading the raw file off the classpath and
 * asserting on its content.
 */
class V38PranaColorApplicationsDescriptionMigrationTest {

    private static final String RESOURCE_PATH = "db/migration/V38__add_prana_color_applications_and_description.sql";

    /** The migration file verbatim, exactly as it ships. */
    private static String RAW;

    /**
     * {@link #RAW} with every {@code --} line comment stripped. Every
     * content assertion runs against this view, never {@link #RAW}.
     */
    private static String SQL;

    @BeforeAll
    static void loadMigration() throws IOException {
        try (InputStream in = V38PranaColorApplicationsDescriptionMigrationTest.class.getClassLoader()
                .getResourceAsStream(RESOURCE_PATH)) {
            assertNotNull(in, "Migration resource not found on classpath: " + RESOURCE_PATH);
            RAW = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        SQL = stripComments(RAW);
    }

    @Test
    void migrationFileExistsOnClasspath() {
        assertNotNull(RAW, "V38 must exist on the classpath at " + RESOURCE_PATH);
        assertFalse(RAW.isBlank(), "V38 must not be blank — it adds applications/description to PranaColorCatalog");
    }

    @Test
    void createsApplicationsElementCollectionTableAndEnversShadowTable() {
        assertTrue(SQL.contains("CREATE TABLE prana_color_catalog_applications"),
                "V38 must create the prana_color_catalog_applications @ElementCollection child table");
        assertTrue(SQL.contains("CREATE TABLE prana_color_catalog_applications_aud"),
                "PranaColorCatalog is @Audited — the _aud shadow table must exist in the same migration "
                        + "or the very first insert fails with SQLGrammarException");
    }

    @Test
    void enversShadowTableReferencesRevinfo() {
        assertTrue(SQL.contains("REFERENCES revinfo(rev)"),
                "The _aud shadow table must FK to revinfo(rev), matching V18/V37's Envers shape");
    }

    @Test
    void addsDescriptionColumnToBaseAndAudTables() {
        assertTrue(SQL.contains("ALTER TABLE prana_color_catalog") && SQL.contains("ADD COLUMN description"),
                "V38 must add a description column to prana_color_catalog");
        assertTrue(SQL.contains("ALTER TABLE prana_color_catalog_aud") && SQL.contains("ADD COLUMN description"),
                "V38 must add a description column to prana_color_catalog_aud (Envers shadow table)");
    }

    // -------------------------------------------------------------------
    // Parsing helper — same comment-strip pattern used by other migration tests in this suite.
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
