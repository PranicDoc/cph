package com.pranicdoc.cph.review.erasure;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * Raw-SQL seed rows for the erasure tests. The H2 schema is generated from the entities, which keep ids as
 * plain UUID columns, so {@link #addProductionForeignKeys()} adds the foreign keys the Flyway migrations
 * (V57, V58, V60) declare in Postgres: without them an erase that forgets a table would still pass here.
 */
public class ReviewErasureFixtures {

    private final JdbcTemplate jdbc;

    public ReviewErasureFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Adds the production foreign keys, and pdf_job, which Hibernate cannot create on H2 (its jsonb column). */
    public void addProductionForeignKeys() {
        // pdf_job is not audited by Hibernate, but production has the (empty) shadow table and the purge clears it.
        jdbc.execute("CREATE TABLE IF NOT EXISTS pdf_job_aud (id UUID NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS pdf_job (id UUID PRIMARY KEY, created_at TIMESTAMP WITH TIME ZONE,"
                + " updated_at TIMESTAMP WITH TIME ZONE, created_by VARCHAR(255), updated_by VARCHAR(255),"
                + " is_deleted BOOLEAN NOT NULL DEFAULT FALSE, version BIGINT NOT NULL DEFAULT 0, job_id VARCHAR(255),"
                + " healing_case_id UUID, healing_case_data CLOB, retry_count INT, document_version INT,"
                + " status VARCHAR(30), file_url VARCHAR(500), correlation_id VARCHAR(255))");
        fk("review_submission", "fk_rs_case", "case_id", "healing_case");
        fk("review_submission", "fk_rs_healer", "healer_id", "healer");
        fk("review_submission", "fk_rs_reviewer", "reviewer_id", "healer");
        fk("review_revision", "fk_rr_submission", "submission_id", "review_submission");
        fk("review_comment", "fk_rc_submission", "submission_id", "review_submission");
        fk("review_comment", "fk_rc_revision", "revision_id", "review_revision");
        fk("review_comment", "fk_rc_parent", "parent_id", "review_comment");
        fk("review_comment", "fk_rc_author", "author_id", "healer");
        fk("reviewer_assignment", "fk_ra_healer", "healer_id", "healer");
        fk("reviewer_assignment", "fk_ra_reviewer", "reviewer_id", "healer");
    }

    private void fk(String table, String name, String column, String target) {
        jdbc.execute("ALTER TABLE " + table + " ADD CONSTRAINT IF NOT EXISTS " + name
                + " FOREIGN KEY (" + column + ") REFERENCES " + target + "(id)");
    }

    private static final String BASE_COLUMNS = "id, created_at, updated_at, is_deleted, version";
    private static final String BASE_VALUES = "?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE, 0";

    public UUID healer(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO healer (" + BASE_COLUMNS + ", full_name, email, auth_provider, role, active,"
                        + " ui_version, auto_submit_for_review, name_change_count)"
                        + " VALUES (" + BASE_VALUES + ", ?, ?, 'LOCAL', ?, TRUE, 'CLASSIC', FALSE, 0)",
                id, "Healer " + id, id + "@example.com", role);
        return id;
    }

    public UUID patient(UUID healerId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO patient (" + BASE_COLUMNS + ", healer_id, patient_code, first_name, last_name)"
                + " VALUES (" + BASE_VALUES + ", ?, ?, 'Pat', 'Ient')", id, healerId, id.toString().substring(0, 12));
        return id;
    }

    public UUID healingCase(UUID patientId, UUID healerId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO healing_case (" + BASE_COLUMNS + ", healer_id, patient_id, status)"
                + " VALUES (" + BASE_VALUES + ", ?, ?, 'DRAFT')", id, healerId, patientId);
        return id;
    }

    public UUID pdfJob(UUID caseId, String fileUrl) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO pdf_job (" + BASE_COLUMNS + ", job_id, healing_case_id, healing_case_data,"
                + " retry_count, document_version, status, file_url)"
                + " VALUES (" + BASE_VALUES + ", ?, ?, '{}', 0, 1, 'COMPLETED', ?)",
                id, id.toString(), caseId, fileUrl);
        return id;
    }

    /** {@code reviewerId} may be null (an erased instructor). */
    public UUID submission(UUID caseId, UUID healerId, UUID reviewerId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO review_submission (" + BASE_COLUMNS + ", case_id, healer_id, reviewer_id, stage,"
                + " status, dismissed_from_approved, submitted_via)"
                + " VALUES (" + BASE_VALUES + ", ?, ?, ?, 'REVIEWER', 'PENDING_REVIEW', FALSE, 'MANUAL')",
                id, caseId, healerId, reviewerId);
        return id;
    }

    public UUID revision(UUID submissionId, int no, String fileUrl) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO review_revision (" + BASE_COLUMNS + ", submission_id, revision_no, file_url,"
                + " submitted_via, submitted_at)"
                + " VALUES (" + BASE_VALUES + ", ?, ?, ?, 'MANUAL', CURRENT_TIMESTAMP)",
                id, submissionId, no, fileUrl);
        return id;
    }

    public UUID comment(UUID submissionId, UUID revisionId, UUID parentId, UUID authorId, String body) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO review_comment (" + BASE_COLUMNS + ", submission_id, revision_id, parent_id,"
                + " author_id, author_side, body)"
                + " VALUES (" + BASE_VALUES + ", ?, ?, ?, ?, 'REVIEWER', ?)",
                id, submissionId, revisionId, parentId, authorId, body);
        return id;
    }

    public UUID assignment(UUID healerId, UUID reviewerId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO reviewer_assignment (" + BASE_COLUMNS + ", healer_id, reviewer_id)"
                + " VALUES (" + BASE_VALUES + ", ?, ?)", id, healerId, reviewerId);
        return id;
    }

    public void stamp(String table, UUID id, String email) {
        jdbc.update("UPDATE " + table + " SET created_by = ?, updated_by = ? WHERE id = ?", email, email, id);
    }

    public void reviewFileUrl(UUID revisionId, String key) {
        jdbc.update("UPDATE review_revision SET review_file_url = ? WHERE id = ?", key, revisionId);
    }

    /** Envers shadow rows need a revinfo row. */
    public long revinfo() {
        long rev = Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L) + 1;
        jdbc.update("INSERT INTO revinfo (rev, revtstmp) VALUES (?, 0)", rev);
        return rev;
    }

    public void submissionAud(UUID id, long rev, String email, UUID reviewerId) {
        jdbc.update("INSERT INTO review_submission_aud (id, rev, revtype, created_by, updated_by, reviewer_id)"
                + " VALUES (?, ?, 1, ?, ?, ?)", id, rev, email, email, reviewerId);
    }

    public void commentAud(UUID id, long rev, String email, UUID authorId) {
        jdbc.update("INSERT INTO review_comment_aud (id, rev, revtype, created_by, updated_by, author_id)"
                + " VALUES (?, ?, 1, ?, ?, ?)", id, rev, email, email, authorId);
    }

    public void assignmentAud(UUID id, long rev, UUID healerId, UUID reviewerId) {
        jdbc.update("INSERT INTO reviewer_assignment_aud (id, rev, revtype, healer_id, reviewer_id)"
                + " VALUES (?, ?, 1, ?, ?)", id, rev, healerId, reviewerId);
    }

    /** Rows of the table (or its history table) whose created_by or updated_by is this e-mail. */
    public int stampedWith(String table, String email) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM " + table
                + " WHERE lower(created_by) = lower(?) OR lower(updated_by) = lower(?)", Integer.class, email, email);
        return n == null ? 0 : n;
    }

    public int count(String table, String column, UUID value) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class, value);
        return n == null ? 0 : n;
    }
}
