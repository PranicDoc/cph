package com.pranicdoc.cph.patient.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Erases a patient's records for good: the patient, medical history, every case with its sessions,
 * scans, protocol, feedback, closure and PDF job rows, and the audit-history copies of those rows.
 *
 * <p>The tables are listed here (child to parent) rather than discovered, so a new table that holds
 * patient data is never erased by accident; add it here when one is created. Consent records and the
 * audit log are kept on purpose (legal evidence). Generated PDFs in object storage are not touched here.
 */
@Component
@RequiredArgsConstructor
public class RecordPurger {

    record Edge(String table, String column) {
    }

    /** parent table -> (child table, column that points at the parent's id). */
    static final Map<String, List<Edge>> CHILDREN = Map.of(
            "patient", List.of(
                    new Edge("patient_medical_history", "patient_id"),
                    new Edge("healing_case", "patient_id")),
            "healing_case", List.of(
                    new Edge("case_closure", "healing_case_id"),
                    new Edge("case_medical_snapshot", "healing_case_id"),
                    new Edge("case_medication", "healing_case_id"),
                    new Edge("case_symptom", "healing_case_id"),
                    new Edge("declaration", "healing_case_id"),
                    new Edge("feedback_link", "case_id"),
                    new Edge("healer_protocol_understanding", "healing_case_id"),
                    new Edge("healing_details", "healing_case_id"),
                    new Edge("healing_session", "healing_case_id"),
                    new Edge("pdf_job", "healing_case_id")),
            "healing_session", List.of(
                    new Edge("chakra_scan", "healing_session_id"),
                    new Edge("protocol_entry", "healing_session_id"),
                    new Edge("qualitative_feedback", "healing_session_id"),
                    new Edge("symptom_feedback", "healing_session_id")),
            "case_symptom", List.of(
                    new Edge("symptom_feedback", "case_symptom_id")),
            "chakra_scan", List.of(
                    new Edge("chakra_reading", "chakra_scan_id")),
            "protocol_entry", List.of(
                    new Edge("protocol_color", "protocol_entry_id")),
            "healing_details", List.of(
                    new Edge("treatment_step", "healing_details_id")),
            "treatment_step", List.of(
                    new Edge("treatment_step_targets", "treatment_step_id")));

    /** Tables that are only a list of values for their parent (no id of their own). */
    private static final java.util.Set<String> LEAF_TABLES = java.util.Set.of("treatment_step_targets", "feedback_link");

    private final JdbcTemplate jdbc;

    /** Erases one patient and everything of theirs. Runs in the caller's transaction, or its own. */
    @Transactional
    public void purgePatient(UUID patientId) {
        purge("patient", "SELECT id FROM patient WHERE id = ?", patientId);
    }

    /** Case ids of a patient (also the soft-deleted ones), for callers that need to clean up files. */
    public List<UUID> caseIdsOf(UUID patientId) {
        return jdbc.queryForList("SELECT id FROM healing_case WHERE patient_id = ?", UUID.class, patientId);
    }

    private void purge(String table, String idQuery, UUID arg) {
        for (Edge child : CHILDREN.getOrDefault(table, List.of())) {
            String childTable = child.table();
            String childQuery = "SELECT id FROM " + childTable + " WHERE " + child.column() + " IN (" + idQuery + ")";
            if (LEAF_TABLES.contains(childTable)) {
                jdbc.update("DELETE FROM " + childTable + " WHERE " + child.column() + " IN (" + idQuery + ")", arg);
            } else {
                purge(childTable, childQuery, arg);
            }
        }
        if (hasTable(table + "_aud")) {
            jdbc.update("DELETE FROM " + table + "_aud WHERE id IN (" + idQuery + ")", arg);
        }
        jdbc.update("DELETE FROM " + table + " WHERE id IN (" + idQuery + ")", arg);
    }

    private boolean hasTable(String name) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = ?",
                Integer.class, name);
        return n != null && n > 0;
    }
}
