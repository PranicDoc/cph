package com.pranicdoc.cph.patient.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fails the build when someone adds a table that points at a healer, patient, case or session and forgets
 * to teach the erase code about it. It reads the Flyway scripts as plain text (no database needed) and
 * compares the tables with {@link EraseCoverage}.
 */
class EraseCoverageGuardTest {

    private static final Pattern STATEMENT_TABLE = Pattern.compile(
            "^\\s*(create\\s+table(?:\\s+if\\s+not\\s+exists)?|alter\\s+table(?:\\s+only)?(?:\\s+if\\s+exists)?|drop\\s+table(?:\\s+if\\s+exists)?)\\s+(?:public\\.)?\"?([a-z_0-9]+)\"?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern REFERENCES_OWNER = Pattern.compile(
            "references\\s+(?:public\\.)?\"?(healer|patient|healing_case|healing_session)\"?\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    @Test
    void everyTableHoldingHealerOrPatientDataIsErasedOrKeptOnPurpose() throws IOException {
        Schema schema = readMigrations();
        Set<String> known = new TreeSet<>(EraseCoverage.ERASED);
        known.addAll(EraseCoverage.KEPT.keySet());

        Set<String> unclassified = new TreeSet<>(schema.ownedTables);
        unclassified.removeAll(known);
        assertThat(unclassified)
                .as("These tables point at a healer, patient, healing_case or healing_session but the erase code "
                        + "does not know them. Erase them in RecordPurger (patient/case data) or HealerEraseService "
                        + "(healer-owned rows) and add each to EraseCoverage.ERASED, or add it to EraseCoverage.KEPT "
                        + "with the reason it must survive an erase.")
                .isEmpty();

        Set<String> vanished = new TreeSet<>(known);
        vanished.removeAll(schema.existingTables);
        assertThat(vanished)
                .as("EraseCoverage lists tables that no longer exist in the Flyway migrations; remove them from "
                        + "EraseCoverage (and from the erase code).")
                .isEmpty();

        Set<String> both = new TreeSet<>(EraseCoverage.ERASED);
        both.retainAll(EraseCoverage.KEPT.keySet());
        assertThat(both).as("A table cannot be both ERASED and KEPT").isEmpty();
        assertThat(EraseCoverage.KEPT.values()).as("every KEPT table needs a reason").allMatch(r -> !r.isBlank());
    }

    private record Schema(Set<String> existingTables, Set<String> ownedTables) {
    }

    private static Schema readMigrations() throws IOException {
        Set<String> existing = new TreeSet<>();
        Set<String> owned = new TreeSet<>();
        Resource[] scripts = new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/V*.sql");
        assertThat(scripts).as("Flyway scripts on the classpath").isNotEmpty();
        // Versions in numeric order so a later DROP TABLE wins over the earlier CREATE.
        java.util.Arrays.sort(scripts, java.util.Comparator.comparingInt(EraseCoverageGuardTest::version));
        for (Resource script : scripts) {
            String sql = new String(script.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("--[^\\r\\n]*", " ");
            for (String statement : sql.split(";")) {
                Matcher m = STATEMENT_TABLE.matcher(statement);
                if (!m.find()) {
                    continue;
                }
                String kind = m.group(1).toLowerCase(Locale.ROOT);
                String table = m.group(2).toLowerCase(Locale.ROOT);
                if (kind.startsWith("drop")) {
                    existing.remove(table);
                    owned.remove(table);
                    continue;
                }
                if (kind.startsWith("create")) {
                    existing.add(table);
                }
                if (!table.endsWith("_aud") && REFERENCES_OWNER.matcher(statement).find()) {
                    owned.add(table);
                }
            }
        }
        return new Schema(existing, owned);
    }

    private static int version(Resource script) {
        Matcher m = Pattern.compile("V(\\d+)(?:_\\d+)?__").matcher(String.valueOf(script.getFilename()));
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
    }
}
