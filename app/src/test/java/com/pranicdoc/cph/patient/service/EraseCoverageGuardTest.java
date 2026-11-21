package com.pranicdoc.cph.patient.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fails the build when someone adds a table that leads back to a healer, patient, case or session (directly or
 * through another table) and forgets to teach the erase code about it. It reads the Flyway scripts as plain
 * text (no database needed) and compares the tables with {@link EraseCoverage}.
 */
class EraseCoverageGuardTest {

    @Test
    void everyTableHoldingHealerOrPatientDataIsErasedOrKeptOnPurpose() throws IOException {
        ErasureSchemaScanner.Schema schema = ErasureSchemaScanner.scan(readMigrations());
        Set<String> known = new TreeSet<>(EraseCoverage.ERASED);
        known.addAll(EraseCoverage.KEPT.keySet());

        Set<String> unclassified = new TreeSet<>(schema.ownedTables());
        unclassified.removeAll(known);
        assertThat(unclassified)
                .as("These tables lead back to a healer, patient, healing_case or healing_session (directly or "
                        + "through another table) but the erase code does not know them. Erase them in RecordPurger "
                        + "(patient/case data, and add them to PURGED_TABLES) or HealerEraseService (healer-owned rows, "
                        + "HEALER_TABLES), or add them to EraseCoverage.KEPT with the reason they must survive an erase.")
                .isEmpty();

        Set<String> vanished = new TreeSet<>(known);
        vanished.removeAll(schema.existingTables());
        assertThat(vanished)
                .as("EraseCoverage lists tables that no longer exist in the Flyway migrations; remove them from "
                        + "EraseCoverage (and from the erase code).")
                .isEmpty();

        Set<String> both = new TreeSet<>(EraseCoverage.ERASED);
        both.retainAll(EraseCoverage.KEPT.keySet());
        assertThat(both).as("A table cannot be both ERASED and KEPT").isEmpty();
        assertThat(EraseCoverage.KEPT.values()).as("every KEPT table needs a reason").allMatch(r -> !r.isBlank());
    }

    private static List<String> readMigrations() throws IOException {
        Resource[] scripts = new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/V*.sql");
        assertThat(scripts).as("Flyway scripts on the classpath").isNotEmpty();
        // Versions in numeric order so a later DROP TABLE wins over the earlier CREATE.
        Arrays.sort(scripts, Comparator.comparingInt(EraseCoverageGuardTest::version));
        List<String> texts = new ArrayList<>();
        for (Resource script : scripts) {
            texts.add(new String(script.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        }
        return texts;
    }

    private static int version(Resource script) {
        Matcher m = Pattern.compile("V(\\d+)(?:_\\d+)?__").matcher(String.valueOf(script.getFilename()));
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
    }
}
