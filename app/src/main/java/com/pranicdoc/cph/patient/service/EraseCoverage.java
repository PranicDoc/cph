package com.pranicdoc.cph.patient.service;

import java.util.Map;
import java.util.Set;

/**
 * The explicit list of tables the erase code knows about, so a new table holding patient or healer data
 * is never erased (or forgotten) by accident. {@code EraseCoverageGuardTest} compares these two lists with
 * the Flyway migrations and fails the build when a table that points at a healer, patient, case or session
 * is in neither of them.
 *
 * <p>Adding a table: either erase it in {@link RecordPurger} (patient and case data) or
 * {@link HealerEraseService} (healer-owned rows) and add it to {@link #ERASED}, or add it to
 * {@link #KEPT} with the reason it must survive an erase. {@code _aud} shadow tables follow their parent.
 */
public final class EraseCoverage {

    /** Tables the erase plan deletes from, or whose rows it re-points or hands over. */
    public static final Set<String> ERASED = Set.of(
            // RecordPurger (patient, case and session data)
            "patient", "patient_medical_history", "healing_case", "case_closure", "case_medical_snapshot",
            "case_medication", "case_symptom", "declaration", "feedback_link", "healer_protocol_understanding",
            "healing_details", "treatment_step", "treatment_step_targets", "healing_session", "chakra_scan",
            "chakra_reading", "protocol_entry", "protocol_color", "qualitative_feedback", "symptom_feedback",
            "pdf_job", "review_submission", "review_revision", "review_comment",
            // HealerEraseService (healer-owned rows)
            "healer", "password_reset_token", "protocol_import_job", "master_data_elevation", "user_role",
            "reviewer_assignment",
            // re-pointed or handed to another super admin rather than deleted
            "healer_invite_links", "chakra_catalog", "prana_color_catalog", "protocol_catalog", "protocol_chakra",
            "protocol_color_mapping");

    /** Tables that point at a healer, patient, case or session and are deliberately left alone, with why (some hold the ids without a foreign key). */
    public static final Map<String, String> KEPT = Map.of(
            "consent", "legal evidence of what the patient agreed to; kept on purpose",
            "audit_log", "legal evidence of who did what; kept on purpose");

    private EraseCoverage() {
    }
}
