-- =============================================================================
-- V58__review_submission.sql
-- Case review: a healer submits a case's generated PDF to their instructor.
-- review_submission is one review thread per case and stage; review_revision is
-- each PDF version submitted into it (pinned to review/{submissionId}/r{n}.pdf).
-- At most one non-dismissed submission may exist per case and stage.
-- =============================================================================

CREATE TABLE review_submission (
    id                      UUID         NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    created_by              VARCHAR(255),
    updated_by              VARCHAR(255),
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    version                 BIGINT       NOT NULL DEFAULT 0,
    case_id                 UUID         NOT NULL,
    healer_id               UUID         NOT NULL,
    -- NULL once the instructor's account is erased; the submission waits for a newly linked reviewer.
    reviewer_id             UUID,
    stage                   VARCHAR(20)  NOT NULL,
    status                  VARCHAR(30)  NOT NULL,
    dismissed_from_approved BOOLEAN      NOT NULL DEFAULT FALSE,
    dismiss_reason          VARCHAR(500),
    submitted_via           VARCHAR(10)  NOT NULL,
    CONSTRAINT pk_review_submission PRIMARY KEY (id),
    CONSTRAINT fk_review_submission_case     FOREIGN KEY (case_id)     REFERENCES healing_case(id),
    CONSTRAINT fk_review_submission_healer   FOREIGN KEY (healer_id)   REFERENCES healer(id),
    CONSTRAINT fk_review_submission_reviewer FOREIGN KEY (reviewer_id) REFERENCES healer(id),
    CONSTRAINT ck_review_submission_stage  CHECK (stage IN ('REVIEWER', 'FEDERATION')),
    CONSTRAINT ck_review_submission_status CHECK (status IN ('PENDING_REVIEW', 'CHANGES_REQUESTED', 'APPROVED', 'DISMISSED')),
    CONSTRAINT ck_review_submission_via    CHECK (submitted_via IN ('MANUAL', 'AUTO'))
);

-- Double-click / concurrent submit: the second insert is rejected and the service returns the open one.
CREATE UNIQUE INDEX uq_review_submission_open
    ON review_submission (case_id, stage)
    WHERE status IN ('PENDING_REVIEW', 'CHANGES_REQUESTED', 'APPROVED') AND is_deleted = false;

CREATE INDEX idx_review_submission_healer   ON review_submission (healer_id);
CREATE INDEX idx_review_submission_reviewer ON review_submission (reviewer_id, status);
CREATE INDEX idx_review_submission_case     ON review_submission (case_id);

CREATE TABLE review_revision (
    id                UUID         NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    version           BIGINT       NOT NULL DEFAULT 0,
    submission_id     UUID         NOT NULL,
    revision_no       INTEGER      NOT NULL,
    pdf_job_id        UUID,
    file_url          VARCHAR(500) NOT NULL,
    review_file_url   VARCHAR(500),
    submitted_via     VARCHAR(10)  NOT NULL,
    submitted_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_review_revision PRIMARY KEY (id),
    CONSTRAINT fk_review_revision_submission FOREIGN KEY (submission_id) REFERENCES review_submission(id),
    CONSTRAINT uq_review_revision_no UNIQUE (submission_id, revision_no),
    CONSTRAINT ck_review_revision_via CHECK (submitted_via IN ('MANUAL', 'AUTO'))
);

-- Envers shadow tables (BaseEntity is @Audited).
CREATE TABLE review_submission_aud (
    id                      UUID,
    rev                     BIGINT NOT NULL,
    revtype                 SMALLINT,
    created_at              TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ,
    created_by              VARCHAR(255),
    updated_by              VARCHAR(255),
    is_deleted              BOOLEAN,
    version                 BIGINT,
    case_id                 UUID,
    healer_id               UUID,
    reviewer_id             UUID,
    stage                   VARCHAR(20),
    status                  VARCHAR(30),
    dismissed_from_approved BOOLEAN,
    dismiss_reason          VARCHAR(500),
    submitted_via           VARCHAR(10),
    CONSTRAINT pk_review_submission_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_review_submission_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE review_revision_aud (
    id                UUID,
    rev               BIGINT NOT NULL,
    revtype           SMALLINT,
    created_at        TIMESTAMPTZ,
    updated_at        TIMESTAMPTZ,
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    is_deleted        BOOLEAN,
    version           BIGINT,
    submission_id     UUID,
    revision_no       INTEGER,
    pdf_job_id        UUID,
    file_url          VARCHAR(500),
    review_file_url   VARCHAR(500),
    submitted_via     VARCHAR(10),
    submitted_at      TIMESTAMPTZ,
    CONSTRAINT pk_review_revision_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_review_revision_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);
