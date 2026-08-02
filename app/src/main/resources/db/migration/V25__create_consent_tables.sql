-- =============================================================================
-- V25__create_consent_tables.sql
-- Consent Core (Phase 30) — versioned consent capture + policy version registry.
--
-- NOTE: originally planned as V24, but V24__add_protocol_catalog.sql landed
-- first (a different, already-committed feature) — this migration is
-- renumbered to V25 to keep the additive, next-sequential-version convention.
-- =============================================================================

CREATE TABLE consent (
    id            UUID         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    version       BIGINT       NOT NULL DEFAULT 0,
    subject_type  VARCHAR(30)  NOT NULL,
    subject_id    UUID         NOT NULL,
    consent_type  VARCHAR(40)  NOT NULL,
    policy_version INTEGER     NOT NULL,
    accepted_at   TIMESTAMPTZ,
    accepted_by   UUID,
    withdrawn_at  TIMESTAMPTZ,
    method        VARCHAR(30),
    ip_address    VARCHAR(255),
    user_agent    VARCHAR(500),
    language      VARCHAR(10),
    CONSTRAINT pk_consent PRIMARY KEY (id)
);

CREATE INDEX idx_consent_subject ON consent (subject_id, consent_type);

CREATE TABLE policy_version (
    id              UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    version         BIGINT       NOT NULL DEFAULT 0,
    consent_type    VARCHAR(40)  NOT NULL,
    policy_version  INTEGER      NOT NULL,
    mandatory       BOOLEAN      NOT NULL DEFAULT TRUE,
    effective_date  TIMESTAMPTZ  NOT NULL,
    content         TEXT,
    CONSTRAINT pk_policy_version PRIMARY KEY (id),
    CONSTRAINT uq_policy_version_type_ver UNIQUE (consent_type, policy_version)
);

-- Envers audit-history tables — required because both entities are @Audited
-- (see chakra_catalog_aud in V0__baseline_schema.sql for the shape reference)
CREATE TABLE consent_aud (
    id            UUID,
    rev           BIGINT      NOT NULL,
    revtype       SMALLINT,
    created_at    TIMESTAMPTZ,
    updated_at    TIMESTAMPTZ,
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    is_deleted    BOOLEAN,
    version       BIGINT,
    subject_type  VARCHAR(30),
    subject_id    UUID,
    consent_type  VARCHAR(40),
    policy_version INTEGER,
    accepted_at   TIMESTAMPTZ,
    accepted_by   UUID,
    withdrawn_at  TIMESTAMPTZ,
    method        VARCHAR(30),
    ip_address    VARCHAR(255),
    user_agent    VARCHAR(500),
    language      VARCHAR(10),
    CONSTRAINT pk_consent_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_consent_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE policy_version_aud (
    id              UUID,
    rev             BIGINT      NOT NULL,
    revtype         SMALLINT,
    created_at      TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    is_deleted      BOOLEAN,
    version         BIGINT,
    consent_type    VARCHAR(40),
    policy_version  INTEGER,
    mandatory       BOOLEAN,
    effective_date  TIMESTAMPTZ,
    content         TEXT,
    CONSTRAINT pk_policy_version_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_policy_version_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);
