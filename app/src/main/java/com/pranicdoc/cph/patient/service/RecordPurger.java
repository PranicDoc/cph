package com.pranicdoc.cph.patient.service;

import com.pranicdoc.storage.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Erases a patient's records for good: the patient, medical history, every case with its sessions,
 * scans, protocol, feedback, closure, PDF job and case-review rows (submissions, revisions, comments),
 * and the audit-history copies of those rows.
 *
 * <p>The tables are listed here (child to parent) rather than discovered, so a new table that holds
 * patient data is never erased by accident; add it here when one is created. Consent records and the
 * audit log are kept on purpose (legal evidence).
 *
 * <p>The PDFs those rows point at in object storage (the job PDFs and the review copies) are deleted
 * once the surrounding transaction has committed, on a background executor so the erase never waits for
 * storage, best effort: a storage failure (or a rejected task) is logged and never affects the erase, and
 * nothing is deleted if the erase rolls back.
 */
@Component
@Slf4j
public class RecordPurger {

    record Edge(String table, String column) {
    }

    /** parent table -> (child table, column that points at the parent's id). */
    static final Map<String, List<Edge>> CHILDREN = Map.ofEntries(
            Map.entry("patient", List.of(
                    new Edge("patient_medical_history", "patient_id"),
                    new Edge("healing_case", "patient_id"))),
            Map.entry("healing_case", List.of(
                    new Edge("case_closure", "healing_case_id"),
                    new Edge("case_medical_snapshot", "healing_case_id"),
                    new Edge("case_medication", "healing_case_id"),
                    new Edge("case_symptom", "healing_case_id"),
                    new Edge("declaration", "healing_case_id"),
                    new Edge("feedback_link", "case_id"),
                    new Edge("healer_protocol_understanding", "healing_case_id"),
                    new Edge("healing_details", "healing_case_id"),
                    new Edge("healing_session", "healing_case_id"),
                    new Edge("pdf_job", "healing_case_id"),
                    new Edge("review_submission", "case_id"))),
            Map.entry("healing_session", List.of(
                    new Edge("chakra_scan", "healing_session_id"),
                    new Edge("protocol_entry", "healing_session_id"),
                    new Edge("qualitative_feedback", "healing_session_id"),
                    new Edge("symptom_feedback", "healing_session_id"))),
            Map.entry("case_symptom", List.of(
                    new Edge("symptom_feedback", "case_symptom_id"))),
            Map.entry("chakra_scan", List.of(
                    new Edge("chakra_reading", "chakra_scan_id"))),
            Map.entry("protocol_entry", List.of(
                    new Edge("protocol_color", "protocol_entry_id"))),
            Map.entry("healing_details", List.of(
                    new Edge("treatment_step", "healing_details_id"))),
            Map.entry("treatment_step", List.of(
                    new Edge("treatment_step_targets", "treatment_step_id"))),
            // Comments point at both their submission and their revision, so they go before either.
            Map.entry("review_submission", List.of(
                    new Edge("review_comment", "submission_id"),
                    new Edge("review_revision", "submission_id"))),
            Map.entry("review_revision", List.of(
                    new Edge("review_comment", "revision_id"))));

    /** Tables that are only a list of values for their parent (no id of their own). */
    private static final java.util.Set<String> LEAF_TABLES = java.util.Set.of("treatment_step_targets", "feedback_link");

    private static final String CASES_OF_PATIENT = "SELECT id FROM healing_case WHERE patient_id = ?";

    private final JdbcTemplate jdbc;
    private final StorageService storage;
    private final Executor executor;

    public RecordPurger(JdbcTemplate jdbc, StorageService storage, @Qualifier("notificationExecutor") Executor executor) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.executor = executor;
    }

    /** Erases one patient and everything of theirs. Runs in the caller's transaction, or its own. */
    @Transactional
    public void purgePatient(UUID patientId) {
        List<String> files = new java.util.ArrayList<>(jdbc.queryForList(
                "SELECT file_url FROM pdf_job WHERE file_url IS NOT NULL AND healing_case_id IN ("
                        + CASES_OF_PATIENT + ")", String.class, patientId));
        files.addAll(jdbc.queryForList(
                "SELECT file_url FROM review_revision WHERE file_url IS NOT NULL AND submission_id IN ("
                        + "SELECT id FROM review_submission WHERE case_id IN (" + CASES_OF_PATIENT + "))",
                String.class, patientId));
        files.addAll(jdbc.queryForList(
                "SELECT review_file_url FROM review_revision WHERE review_file_url IS NOT NULL AND submission_id IN ("
                        + "SELECT id FROM review_submission WHERE case_id IN (" + CASES_OF_PATIENT + "))",
                String.class, patientId));
        purge("patient", "SELECT id FROM patient WHERE id = ?", patientId);
        deleteStoredFilesAfterCommit(files);
    }

    /**
     * Erases every review submission the healer opened, with its revisions, comments and review copies.
     * Their patients' purge already removes these; this catches any that point at the healer through
     * another case. Runs in the caller's transaction, or its own.
     */
    @Transactional
    public void purgeReviewSubmissionsOfHealer(UUID healerId) {
        List<String> files = new java.util.ArrayList<>(jdbc.queryForList(
                "SELECT file_url FROM review_revision WHERE file_url IS NOT NULL AND submission_id IN ("
                        + "SELECT id FROM review_submission WHERE healer_id = ?)", String.class, healerId));
        files.addAll(jdbc.queryForList(
                "SELECT review_file_url FROM review_revision WHERE review_file_url IS NOT NULL AND submission_id IN ("
                        + "SELECT id FROM review_submission WHERE healer_id = ?)", String.class, healerId));
        purge("review_submission", "SELECT id FROM review_submission WHERE healer_id = ?", healerId);
        deleteStoredFilesAfterCommit(files);
    }

    /** Case ids of a patient (also the soft-deleted ones), for callers that need to clean up files. */
    public List<UUID> caseIdsOf(UUID patientId) {
        return jdbc.queryForList(CASES_OF_PATIENT, UUID.class, patientId);
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
        if ("review_comment".equals(table)) {
            // A reply points at its parent comment; unlink first so deleting a thread never depends on row order.
            jdbc.update("UPDATE review_comment SET parent_id = NULL WHERE id IN (" + idQuery + ")", arg);
        }
        if (hasTable(table + "_aud")) {
            jdbc.update("DELETE FROM " + table + "_aud WHERE id IN (" + idQuery + ")", arg);
        }
        jdbc.update("DELETE FROM " + table + " WHERE id IN (" + idQuery + ")", arg);
    }

    private boolean hasTable(String name) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = current_schema()"
                        + " AND lower(table_name) = lower(?)",
                Integer.class, name);
        return n != null && n > 0;
    }

    /** After the surrounding transaction commits; at once when there is none. */
    private void deleteStoredFilesAfterCommit(Collection<String> keys) {
        LinkedHashSet<String> unique = new LinkedHashSet<>(keys);
        if (unique.isEmpty()) {
            return;
        }
        DeleteStoredFilesAfterCommit deletion = new DeleteStoredFilesAfterCommit(storage, executor, List.copyOf(unique));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(deletion);
        } else {
            deletion.submit();
        }
    }

    /** Hands the stored files to the executor once the erase has committed. Never throws: the erase is already done. */
    public static final class DeleteStoredFilesAfterCommit implements TransactionSynchronization {

        private final StorageService storage;
        private final Executor executor;
        private final List<String> keys;

        DeleteStoredFilesAfterCommit(StorageService storage, Executor executor, List<String> keys) {
            this.storage = storage;
            this.executor = executor;
            this.keys = keys;
        }

        @Override
        public void afterCommit() {
            submit();
        }

        /** One task for the whole key list; a rejected task is logged, never thrown. */
        void submit() {
            try {
                executor.execute(this::deleteAll);
            } catch (Exception e) {
                log.warn("Could not queue the deletion of {} stored file(s) after an erase; remove them by hand: {}",
                        keys.size(), e.toString());
            }
        }

        void deleteAll() {
            try {
                for (String key : keys) {
                    try {
                        storage.delete(key);
                    } catch (Exception e) {
                        log.warn("Could not delete stored file {} after an erase; remove it by hand: {}", key, e.toString());
                    }
                }
            } catch (Exception e) {
                log.warn("Stored file deletion after an erase stopped early: {}", e.toString());
            }
        }
    }
}
