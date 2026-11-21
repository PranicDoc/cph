package com.pranicdoc.cph.patient.service;

import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.util.TransactionService;
import com.pranicdoc.cph.review.erasure.ReviewErasureFixtures;
import com.pranicdoc.storage.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seeds one row (and its history copy) in EVERY table the purger declares, with the production foreign keys,
 * then purges the patient on real SQL: every one of the native erase queries must run, in an order the keys
 * accept, and leave nothing behind. Tables kept on purpose must be untouched.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({RecordPurger.class, TransactionService.class, RecordPurgerFullPlanIntegrationTest.SameThreadExecutor.class})
class RecordPurgerFullPlanIntegrationTest {

    @TestConfiguration
    static class SameThreadExecutor {
        @Bean(name = "notificationExecutor")
        Executor notificationExecutor() {
            return Runnable::run;
        }
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private RecordPurger purger;
    @MockBean private SecurityContextUtil securityContextUtil;
    @MockBean private StorageService storage;

    private ReviewErasureFixtures fx;
    private UUID healer;
    private UUID patient;
    /** table -> the column that identifies its seeded row, and the value. */
    private final Map<String, Object[]> seeded = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        fx = new ReviewErasureFixtures(jdbc);
        fx.addProductionForeignKeys();
    }

    @Test
    void purgingAPatientEmptiesEveryDeclaredTable_andLeavesKeptTablesAlone() {
        healer = fx.healer("HEALER");
        UUID reviewer = fx.healer("HEALER");
        patient = fx.patient(healer);
        UUID caseId = fx.healingCase(patient, healer);
        track("patient", "id", patient);
        track("healing_case", "id", caseId);
        UUID submission = fx.submission(caseId, healer, reviewer);
        track("review_submission", "id", submission);
        UUID revision = fx.revision(submission, 1, "review/r1.pdf");
        track("review_revision", "id", revision);
        track("review_comment", "id", fx.comment(submission, revision, null, healer, "hello"));
        track("pdf_job", "id", fx.pdfJob(caseId, "pdf/a.pdf"));

        seed("patient_medical_history", "patient_id", patient);
        UUID symptom = seed("case_symptom", "healing_case_id", caseId);
        UUID details = seed("healing_details", "healing_case_id", caseId);
        UUID step = seed("treatment_step", "healing_details_id", details);
        seedValueRow("treatment_step_targets", "treatment_step_id", step);
        for (String t : List.of("case_closure", "case_medical_snapshot", "case_medication", "declaration",
                "healer_protocol_understanding")) {
            seed(t, "healing_case_id", caseId);
        }
        seedValueRow("feedback_link", "case_id", caseId); // keyed by its token, no id column
        UUID session = seed("healing_session", "healing_case_id", caseId);
        UUID scan = seed("chakra_scan", "healing_session_id", session);
        seed("chakra_reading", "chakra_scan_id", scan);
        UUID entry = seed("protocol_entry", "healing_session_id", session);
        seed("protocol_color", "protocol_entry_id", entry);
        seed("qualitative_feedback", "healing_session_id", session);
        seed("symptom_feedback", "healing_session_id", session, "case_symptom_id", symptom);

        assertThat(new TreeSet<>(seeded.keySet()))
                .as("the test must seed exactly the tables RecordPurger declares")
                .isEqualTo(new TreeSet<>(RecordPurger.PURGED_TABLES));

        Map<String, Integer> kept = new LinkedHashMap<>();
        for (String table : EraseCoverage.KEPT.keySet()) {
            if (!tableExists(table) || table.endsWith("_catalog_properties") || table.equals("protocol_chakra_color")) {
                continue;
            }
            insertGeneric(table, Map.of());
            kept.put(table, count(table));
        }

        purger.purgePatient(patient);

        List<String> left = new ArrayList<>();
        seeded.forEach((table, key) -> {
            if (jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + key[0] + " = ?", Integer.class, key[1]) > 0) {
                left.add(table);
            }
            if (key[0].equals("id") && tableExists(table + "_aud") && jdbc.queryForObject(
                    "SELECT count(*) FROM " + table + "_aud WHERE id = ?", Integer.class, key[1]) > 0) {
                left.add(table + "_aud");
            }
        });
        assertThat(left).as("rows left behind after the purge").isEmpty();
        kept.forEach((table, n) -> assertThat(count(table)).as(table + " must be untouched").isEqualTo(n));
    }

    private void track(String table, String column, UUID id) {
        seeded.put(table, new Object[] {column, id});
        seedAud(table, id);
    }

    /** Inserts a row identified by its id with the given foreign key(s); returns its id. */
    private UUID seed(String table, String fkColumn, UUID fk, Object... more) {
        UUID id = UUID.randomUUID();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", id);
        values.put(fkColumn, fk);
        for (int i = 0; i < more.length; i += 2) {
            values.put((String) more[i], more[i + 1]);
        }
        insertGeneric(table, values);
        seeded.put(table, new Object[] {"id", id});
        seedAud(table, id);
        return id;
    }

    /** A table of plain values with no id of its own. */
    private void seedValueRow(String table, String fkColumn, UUID fk) {
        insertGeneric(table, Map.of(fkColumn, fk));
        seeded.put(table, new Object[] {fkColumn, fk});
    }

    private void seedAud(String table, UUID id) {
        if (tableExists(table + "_aud")) {
            boolean hasRev = jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE lower(table_name) = ?"
                    + " AND lower(column_name) = 'rev'", Integer.class, table + "_aud") > 0;
            insertGeneric(table + "_aud", hasRev ? Map.of("id", id, "rev", fx.revinfo()) : Map.of("id", id));
        }
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private boolean tableExists(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE lower(table_name) = ?",
                Integer.class, table.toLowerCase()) > 0;
    }

    /** Inserts one row: the given values, healer_id as the seeded healer, and a filler for every other NOT NULL column. */
    private void insertGeneric(String table, Map<String, Object> given) {
        Map<String, Object> values = new LinkedHashMap<>(given);
        jdbc.query("SELECT column_name, data_type, dtd_identifier FROM information_schema.columns WHERE lower(table_name) = ?"
                + " AND is_nullable = 'NO' AND column_default IS NULL", rs -> {
            String column = rs.getString(1).toLowerCase();
            if (values.containsKey(column)) {
                return;
            }
            String type = rs.getString(2).toUpperCase();
            values.put(column, column.equals("healer_id") ? healer
                    : type.equals("ENUM") ? firstEnumValue(table, rs.getString(3)) : filler(type));
        }, table.toLowerCase());
        List<String> cols = new ArrayList<>(values.keySet());
        String sql = "INSERT INTO " + table + " (" + String.join(", ", cols) + ") VALUES ("
                + String.join(", ", cols.stream().map(c -> "?").toList()) + ")";
        jdbc.update(sql, values.values().toArray());
    }

    /** H2 enum columns (Hibernate maps Java enums to them) accept only their declared names. */
    private String firstEnumValue(String table, String identifier) {
        return jdbc.queryForObject("SELECT value_name FROM information_schema.enum_values WHERE lower(object_name) = ?"
                + " AND enum_identifier = ? ORDER BY value_ordinal LIMIT 1", String.class, table.toLowerCase(), identifier);
    }

    private static Object filler(String type) {
        if (type.equals("UUID")) {
            return UUID.randomUUID();
        }
        if (type.startsWith("TIMESTAMP")) {
            return java.sql.Timestamp.from(java.time.Instant.now());
        }
        if (type.equals("DATE")) {
            return java.sql.Date.valueOf(java.time.LocalDate.now());
        }
        if (type.equals("BOOLEAN")) {
            return Boolean.FALSE;
        }
        if (type.startsWith("CHARACTER") || type.equals("JSON") || type.contains("CLOB")) {
            return "A";
        }
        return 0;
    }
}
