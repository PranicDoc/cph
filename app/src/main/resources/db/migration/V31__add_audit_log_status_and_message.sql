-- =============================================================================
-- V31__add_audit_log_status_and_message.sql
-- Fixes a gap where AuditAspect only wrote a row on successful completion of an
-- @Auditable method — any exception (including what surfaces to the caller as a
-- 500) skipped the audit trail entirely. AuditAspect now writes a row on both
-- outcomes; these columns record which outcome and (on failure) why.
--
-- AuditStatus: SUCCESS, FAILURE
-- =============================================================================

ALTER TABLE audit_log
    ADD COLUMN status  VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    ADD COLUMN message TEXT;

ALTER TABLE audit_log
    ADD CONSTRAINT ck_audit_log_status
        CHECK (status IN ('SUCCESS','FAILURE'));

CREATE INDEX idx_audit_log_status ON audit_log(status);

ALTER TABLE audit_log_aud
    ADD COLUMN status  VARCHAR(20),
    ADD COLUMN message TEXT;

ALTER TABLE audit_log_aud
    ADD CONSTRAINT ck_audit_log_aud_status
        CHECK (status IS NULL OR status IN ('SUCCESS','FAILURE'));
