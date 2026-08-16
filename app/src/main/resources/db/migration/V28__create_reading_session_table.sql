-- =============================================================================
-- V28__create_reading_session_table.sql
-- Phase 33 (READ-01/READ-02) — mandatory reading-flow session tracking.
-- Records when a practitioner opened/closed a given (consentType,
-- policyVersion) document and how long that took, so the frontend scroll-gate
-- + "I Agree" flow has server-authoritative timestamps to enforce against.
--
-- reading_session_aud exists because ReadingSession extends the @Audited
-- BaseEntity — omitting it reproduces the exact SQLGrammarException V27's own
-- header comment documents (Pitfall 5): the very first insert/update on
-- reading_session would fail immediately.
-- =============================================================================

CREATE TABLE reading_session (
    id                UUID         NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    version           BIGINT       NOT NULL DEFAULT 0,
    subject_id        UUID         NOT NULL,
    consent_type      VARCHAR(60)  NOT NULL,
    policy_version    INTEGER      NOT NULL,
    opened_at         TIMESTAMPTZ  NOT NULL,
    closed_at         TIMESTAMPTZ,
    duration_seconds  BIGINT,
    scroll_completed  BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_reading_session PRIMARY KEY (id)
    -- Deliberately NO foreign key on subject_id or consent_type — matches
    -- V25/V27's documented non-FK-by-design denormalization; existence of
    -- the referenced consent_type key is enforced in the application layer,
    -- not the database.
);

CREATE INDEX idx_reading_session_subject ON reading_session (subject_id, consent_type, policy_version);

-- Envers audit-history shadow table — required because BaseEntity is
-- @Audited at the mapped-superclass level, so ReadingSession (which extends
-- it) is audited whether or not this table exists.
CREATE TABLE reading_session_aud (
    id                UUID,
    rev               BIGINT NOT NULL,
    revtype           SMALLINT,
    created_at        TIMESTAMPTZ,
    updated_at        TIMESTAMPTZ,
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    is_deleted        BOOLEAN,
    version           BIGINT,
    subject_id        UUID,
    consent_type      VARCHAR(60),
    policy_version    INTEGER,
    opened_at         TIMESTAMPTZ,
    closed_at         TIMESTAMPTZ,
    duration_seconds  BIGINT,
    scroll_completed  BOOLEAN,
    CONSTRAINT pk_reading_session_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_reading_session_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);
