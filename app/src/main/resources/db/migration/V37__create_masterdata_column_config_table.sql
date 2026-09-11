-- =============================================================================
-- V37__create_masterdata_column_config_table.sql
-- Phase 01 (masterdata-import-redesign) — masterdata column-config registry.
--
-- Replaces each MasterDataImporter's hardcoded column constants with an
-- admin-manageable masterdata_column_config table, one row per importable
-- column per masterdata type. Mirrors V27__create_consent_type_table.sql's
-- shape (base table + Envers _aud shadow table + literal-UUID system-seed
-- INSERT).
--
-- D-05: schema stores column/header name, target field, required flag, and
--       data type only — deliberately NO transform column.
-- D-06: strictly column-level. No grouping/aggregation/row-level business
--       logic column of any kind (e.g. PranaColor's group-by-Code, dedup of
--       PROPERTY/APPLICATION) — that stays hardcoded in Phase 02's importers.
-- D-08: seeds all 4 masterdata types (PROTOCOL, PRANA_COLOR, CHAKRA,
--       PROTOCOL_COLOR_MAPPING), not just the two the original design doc
--       covered.
-- D-10: MASTER_DATA_COLUMN_CONFIG's RBAC allow-list (see Resource.java) is a
--       Java-only concern — this migration has no bearing on it.
--
-- No CHECK constraint on masterdata_type/data_type: superseded V7 anti-pattern.
-- Validity of both enum-backed columns is enforced by Enum.valueOf at the
-- JPA layer (matching V33's precedent), not a DB constraint.
--
-- Removal of a row is real (BaseEntity's inherited soft-delete, is_deleted)
-- — NOT a dedicated active flag like ConsentType (Pitfall 1 / MDIMPORT-03).
-- =============================================================================

CREATE TABLE masterdata_column_config (
    id              UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    version         BIGINT       NOT NULL DEFAULT 0,
    masterdata_type VARCHAR(40)  NOT NULL,
    column_name     VARCHAR(120) NOT NULL,
    target_field    VARCHAR(60)  NOT NULL,
    required        BOOLEAN      NOT NULL DEFAULT FALSE,
    data_type       VARCHAR(20)  NOT NULL,
    system_defined  BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_masterdata_column_config PRIMARY KEY (id),
    CONSTRAINT uq_masterdata_column_config_type_column UNIQUE (masterdata_type, column_name)
);

-- Envers audit-history shadow table — required because BaseEntity is
-- @Audited at the mapped-superclass level, so MasterdataColumnConfig (which
-- extends it) is audited whether or not this table exists. Omitting it
-- fails the very first insert/update with SQLGrammarException (Pitfall 4).
-- All columns nullable, no DEFAULT, no unique constraint — _aud accumulates
-- one row per revision per entity.
CREATE TABLE masterdata_column_config_aud (
    id              UUID,
    rev             BIGINT NOT NULL,
    revtype         SMALLINT,
    created_at      TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    is_deleted      BOOLEAN,
    version         BIGINT,
    masterdata_type VARCHAR(40),
    column_name     VARCHAR(120),
    target_field    VARCHAR(60),
    required        BOOLEAN,
    data_type       VARCHAR(20),
    system_defined  BOOLEAN,
    CONSTRAINT pk_masterdata_column_config_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_masterdata_column_config_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

-- Seed system-defined defaults for all 4 masterdata types (fixed low literal
-- UUIDs, matching V27's convention — sort first under any ORDER BY id; no
-- pgcrypto dependency; BaseEntity's UUIDv7 generator always sorts after
-- these). Field lists read directly from ProtocolCatalog / PranaColorCatalog
-- / ChakraCatalog / ProtocolColorMapping entity source (01-RESEARCH.md Seed
-- Data Reference, Pitfall 5 — Chakra/ProtocolColorMapping have no prior
-- importer to copy from, so these exact lists are load-bearing).
INSERT INTO masterdata_column_config
    (id, created_at, updated_at, is_deleted, version, masterdata_type, column_name, target_field, required, data_type, system_defined)
VALUES
    -- PROTOCOL (7) — from ProtocolImportService's COL_NAME..COL_TYPE constants
    ('00000000-0000-7000-9000-000000000001', now(), now(), false, 0, 'PROTOCOL', 'Protocol/Technique Name', 'name', true, 'STRING', true),
    ('00000000-0000-7000-9000-000000000002', now(), now(), false, 0, 'PROTOCOL', 'Book', 'sourceBook', true, 'STRING', true),
    ('00000000-0000-7000-9000-000000000003', now(), now(), false, 0, 'PROTOCOL', 'Page', 'pageNumber', true, 'NUMBER', true),
    ('00000000-0000-7000-9000-000000000004', now(), now(), false, 0, 'PROTOCOL', 'Category', 'category', false, 'STRING', true),
    ('00000000-0000-7000-9000-000000000005', now(), now(), false, 0, 'PROTOCOL', 'Description', 'description', false, 'STRING', true),
    ('00000000-0000-7000-9000-000000000006', now(), now(), false, 0, 'PROTOCOL', 'Chapter', 'chapter', false, 'STRING', true),
    ('00000000-0000-7000-9000-000000000007', now(), now(), false, 0, 'PROTOCOL', 'Type', 'protocolType', true, 'ENUM', true),

    -- PRANA_COLOR (4) — no Type column-config row seeded; PROPERTY vs
    -- APPLICATION is row-level importer logic per D-06 / Open Question 1,
    -- not a column-level config concern.
    ('00000000-0000-7000-9000-000000000008', now(), now(), false, 0, 'PRANA_COLOR', 'Code', 'code', true, 'STRING', true),
    ('00000000-0000-7000-9000-000000000009', now(), now(), false, 0, 'PRANA_COLOR', 'Name', 'name', true, 'STRING', true),
    ('00000000-0000-7000-9000-000000000010', now(), now(), false, 0, 'PRANA_COLOR', 'Color Hex', 'colorHex', false, 'STRING', true),
    ('00000000-0000-7000-9000-000000000011', now(), now(), false, 0, 'PRANA_COLOR', 'Value', 'properties', false, 'STRING', true),

    -- CHAKRA (4) — no prior importer to copy from; field list read directly
    -- from ChakraCatalog.java (Pitfall 5).
    ('00000000-0000-7000-9000-000000000012', now(), now(), false, 0, 'CHAKRA', 'Code', 'code', true, 'STRING', true),
    ('00000000-0000-7000-9000-000000000013', now(), now(), false, 0, 'CHAKRA', 'Name', 'name', true, 'STRING', true),
    ('00000000-0000-7000-9000-000000000014', now(), now(), false, 0, 'CHAKRA', 'Category', 'category', false, 'STRING', true),
    ('00000000-0000-7000-9000-000000000015', now(), now(), false, 0, 'CHAKRA', 'Location', 'location', false, 'STRING', true),

    -- PROTOCOL_COLOR_MAPPING (4) — no prior importer to copy from; field
    -- list read directly from ProtocolColorMapping.java (Pitfall 5).
    -- colorId is resolved from a human-typed "Color Code" by the Phase 2
    -- importer, not stored as a raw UUID in the spreadsheet (D-06).
    ('00000000-0000-7000-9000-000000000016', now(), now(), false, 0, 'PROTOCOL_COLOR_MAPPING', 'Protocol Name', 'protocolName', true, 'STRING', true),
    ('00000000-0000-7000-9000-000000000017', now(), now(), false, 0, 'PROTOCOL_COLOR_MAPPING', 'Color Code', 'colorId', true, 'STRING', true),
    ('00000000-0000-7000-9000-000000000018', now(), now(), false, 0, 'PROTOCOL_COLOR_MAPPING', 'Usage Type', 'usageType', true, 'ENUM', true),
    ('00000000-0000-7000-9000-000000000019', now(), now(), false, 0, 'PROTOCOL_COLOR_MAPPING', 'Chakra Name', 'chakraName', false, 'STRING', true);
