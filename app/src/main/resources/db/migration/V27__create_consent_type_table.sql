-- =============================================================================
-- V27__create_consent_type_table.sql
-- Phase 31.1 (Dynamic Consent Type Management) — replaces the hardcoded
-- ConsentType enum (4 compile-time constants) with an admin-manageable
-- consent_type registry table.
--
-- D-01: mandatory defaults FALSE for new rows. This column DEFAULT is
--       defense-in-depth only — the real enforcement of "new types are
--       opt-in, not opt-out" lives in ConsentTypeService (31.1-03), not here.
-- D-05: key is server-derived (slugified from the admin-supplied label) and
--       UNIQUE — never client-supplied directly.
-- Seeded rows reuse the exact former ConsentType enum constant names
-- (TERMS_AND_CONDITIONS, PRIVACY_POLICY, MEDICAL_DISCLAIMER,
-- PRACTITIONER_AGREEMENT) as their key, so every existing
-- consent.consent_type / policy_version.consent_type value resolves
-- unchanged against this new table with zero backfill required.
-- =============================================================================

CREATE TABLE consent_type (
    id             UUID         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    created_by     VARCHAR(255),
    updated_by     VARCHAR(255),
    is_deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    version        BIGINT       NOT NULL DEFAULT 0,
    key            VARCHAR(60)  NOT NULL,
    label          VARCHAR(120) NOT NULL,
    mandatory      BOOLEAN      NOT NULL DEFAULT FALSE,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    system_defined BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_consent_type PRIMARY KEY (id),
    CONSTRAINT uq_consent_type_key UNIQUE (key)
);

-- Envers audit-history shadow table — required because BaseEntity is
-- @Audited at the mapped-superclass level, so ConsentType (which extends it)
-- is audited whether or not this table exists. Omitting it reproduces the
-- exact SQLGrammarException V26's own comment documents (Pitfall 5): the
-- very first insert/update on consent_type would fail immediately.
CREATE TABLE consent_type_aud (
    id             UUID,
    rev            BIGINT NOT NULL,
    revtype        SMALLINT,
    created_at     TIMESTAMPTZ,
    updated_at     TIMESTAMPTZ,
    created_by     VARCHAR(255),
    updated_by     VARCHAR(255),
    is_deleted     BOOLEAN,
    version        BIGINT,
    key            VARCHAR(60),
    label          VARCHAR(120),
    mandatory      BOOLEAN,
    active         BOOLEAN,
    system_defined BOOLEAN,
    CONSTRAINT pk_consent_type_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_consent_type_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

-- Widen the existing denormalized consent_type key columns (base + Envers
-- shadow tables) from VARCHAR(40) to VARCHAR(60) to match consent_type.key.
ALTER TABLE consent            ALTER COLUMN consent_type TYPE VARCHAR(60);
ALTER TABLE consent_aud        ALTER COLUMN consent_type TYPE VARCHAR(60);
ALTER TABLE policy_version     ALTER COLUMN consent_type TYPE VARCHAR(60);
ALTER TABLE policy_version_aud ALTER COLUMN consent_type TYPE VARCHAR(60);

-- Deliberately NO FK from consent.consent_type / policy_version.consent_type
-- to consent_type.key — the denormalized non-FK string is an intentional
-- design choice matching V26's publisher_id precedent. Existence of the key
-- is enforced in the application layer (ConsentService.publish()'s
-- findByKeyAndActiveTrue guard), not the database.

-- Seed the 4 original system-defined types with explicit literal UUIDs
-- (deliberately not a random-UUID generator function) so they sort first
-- under ORDER BY id — BaseEntity assigns time-ordered UUIDv7 to every
-- admin-created row via UuidCreator.getTimeOrderedEpoch(), which always
-- sorts after these fixed low values — and so this migration has no
-- dependency on pgcrypto availability for random UUID generation.
INSERT INTO consent_type (id, created_at, updated_at, is_deleted, version, key, label, mandatory, active, system_defined)
VALUES
    ('00000000-0000-7000-8000-000000000001', now(), now(), false, 0, 'TERMS_AND_CONDITIONS',   'Terms & Conditions',     true, true, true),
    ('00000000-0000-7000-8000-000000000002', now(), now(), false, 0, 'PRIVACY_POLICY',         'Privacy Policy',         true, true, true),
    ('00000000-0000-7000-8000-000000000003', now(), now(), false, 0, 'MEDICAL_DISCLAIMER',     'Medical Disclaimer',     true, true, true),
    ('00000000-0000-7000-8000-000000000004', now(), now(), false, 0, 'PRACTITIONER_AGREEMENT', 'Practitioner Agreement', true, true, true);
