-- Request tracing: ties audit rows and PDF jobs to the application's log lines.
-- requestId identifies one HTTP request; correlationId the whole flow across services.
-- Nullable: rows from before this migration, and rows written off a request, have none.

ALTER TABLE audit_log
    ADD COLUMN request_id     VARCHAR(64),
    ADD COLUMN correlation_id VARCHAR(64);

ALTER TABLE audit_log_aud
    ADD COLUMN request_id     VARCHAR(64),
    ADD COLUMN correlation_id VARCHAR(64);

CREATE INDEX idx_audit_log_correlation_id ON audit_log (correlation_id);

-- pdfWorker reads this when it loads a job, whether the job reached it by Kafka or the DB poller.
ALTER TABLE pdf_job ADD COLUMN correlation_id VARCHAR(64);
ALTER TABLE pdf_job_aud ADD COLUMN correlation_id VARCHAR(64);
