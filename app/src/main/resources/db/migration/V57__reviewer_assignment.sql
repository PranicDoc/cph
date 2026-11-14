-- =============================================================================
-- V57__reviewer_assignment.sql
-- Case review: a SUPADM links each healer to the one instructor (reviewer) who
-- reviews their cases. One reviewer per healer; a reviewer may have many healers.
-- Whether the reviewer holds CASE:REVIEW is checked by the service, not the DB.
-- =============================================================================

CREATE TABLE reviewer_assignment (
    id            UUID         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    version       BIGINT       NOT NULL DEFAULT 0,
    healer_id     UUID         NOT NULL,
    reviewer_id   UUID         NOT NULL,
    CONSTRAINT pk_reviewer_assignment PRIMARY KEY (id),
    CONSTRAINT uq_reviewer_assignment_healer UNIQUE (healer_id),
    CONSTRAINT fk_reviewer_assignment_healer   FOREIGN KEY (healer_id)   REFERENCES healer(id),
    CONSTRAINT fk_reviewer_assignment_reviewer FOREIGN KEY (reviewer_id) REFERENCES healer(id),
    CONSTRAINT ck_reviewer_assignment_not_self CHECK (healer_id <> reviewer_id)
);

CREATE INDEX idx_reviewer_assignment_reviewer ON reviewer_assignment (reviewer_id);

-- Envers shadow table (BaseEntity is @Audited).
CREATE TABLE reviewer_assignment_aud (
    id            UUID,
    rev           BIGINT NOT NULL,
    revtype       SMALLINT,
    created_at    TIMESTAMPTZ,
    updated_at    TIMESTAMPTZ,
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    is_deleted    BOOLEAN,
    version       BIGINT,
    healer_id     UUID,
    reviewer_id   UUID,
    CONSTRAINT pk_reviewer_assignment_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_reviewer_assignment_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);
