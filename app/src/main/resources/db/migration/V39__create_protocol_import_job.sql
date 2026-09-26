-- =============================================================================
-- V39__create_protocol_import_job.sql
-- Tracks each bulk protocol xlsx/csv import as an async job so a healer can
-- submit one (or several) and see their status/results without blocking on
-- the HTTP request. Always healer-owned (NOT NULL healer_id) -- unlike the
-- masterdata catalog tables, there is no "system" import job.
-- =============================================================================

CREATE TABLE protocol_import_job (
    id                UUID         NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    version           BIGINT       NOT NULL DEFAULT 0,
    healer_id         UUID         NOT NULL,
    original_filename VARCHAR(255),
    status            VARCHAR(20)  NOT NULL,
    total_rows        INTEGER,
    created_count     INTEGER,
    updated_count     INTEGER,
    skipped_count     INTEGER,
    errors_json       TEXT,
    failure_message   TEXT,
    started_at        TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    CONSTRAINT pk_protocol_import_job PRIMARY KEY (id),
    CONSTRAINT fk_protocol_import_job_healer FOREIGN KEY (healer_id) REFERENCES healer(id),
    CONSTRAINT ck_protocol_import_job_status CHECK (status IN ('QUEUED','PROCESSING','COMPLETED','FAILED'))
);

CREATE INDEX idx_protocol_import_job_healer ON protocol_import_job (healer_id, created_at DESC);

-- Envers audit-history shadow table (see V37 for why this is required for any
-- entity extending the @Audited BaseEntity).
CREATE TABLE protocol_import_job_aud (
    id                UUID,
    rev               BIGINT NOT NULL,
    revtype           SMALLINT,
    created_at        TIMESTAMPTZ,
    updated_at        TIMESTAMPTZ,
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    is_deleted        BOOLEAN,
    version           BIGINT,
    healer_id         UUID,
    original_filename VARCHAR(255),
    status            VARCHAR(20),
    total_rows        INTEGER,
    created_count     INTEGER,
    updated_count     INTEGER,
    skipped_count     INTEGER,
    errors_json       TEXT,
    failure_message   TEXT,
    started_at        TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    CONSTRAINT pk_protocol_import_job_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_protocol_import_job_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

ALTER TABLE protocol_import_job_aud
    ADD CONSTRAINT ck_protocol_import_job_aud_status
        CHECK (status IS NULL OR status IN ('QUEUED','PROCESSING','COMPLETED','FAILED'));
