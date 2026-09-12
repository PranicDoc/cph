-- =============================================================================
-- V38__add_prana_color_applications_and_description.sql
-- Phase 02 (masterdata-import-redesign) — PranaColor schema gap fix (D-14/D-14b).
--
-- Adds `applications` (@ElementCollection, mirrors the existing `properties`
-- child table exactly — see V18__convert_prana_color_properties_to_element_
-- collection.sql) and `description` (nullable VARCHAR) to PranaColorCatalog,
-- so PranaColor import can ship complete against all 76 design-doc rows
-- (properties AND applications AND description).
--
-- D-14b: the new `description` column's column-config row is seeded here
-- (system-defined), consistent with how V37 seeded every other day-1 column
-- — not left for SUPADM to add manually via the Phase 1 API.
--
-- Both new columns/tables are purely additive — no back-fill needed.
-- =============================================================================

CREATE TABLE prana_color_catalog_applications (
    prana_color_catalog_id  UUID         NOT NULL,
    applications             VARCHAR(255),
    CONSTRAINT fk_prana_color_catalog_applications FOREIGN KEY (prana_color_catalog_id) REFERENCES prana_color_catalog(id)
);

-- Envers audit-history shadow table — required because PranaColorCatalog is
-- @Audited. Omitting it fails the first insert/update with
-- SQLGrammarException (same pitfall V18/V37 document for their own _aud
-- tables).
CREATE TABLE prana_color_catalog_applications_aud (
    prana_color_catalog_id  UUID,
    rev                      BIGINT      NOT NULL,
    revtype                  SMALLINT,
    applications             VARCHAR(255),
    CONSTRAINT pk_prana_color_catalog_applications_aud     PRIMARY KEY (prana_color_catalog_id, rev, applications),
    CONSTRAINT fk_prana_color_catalog_applications_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

ALTER TABLE prana_color_catalog     ADD COLUMN description VARCHAR(500);
ALTER TABLE prana_color_catalog_aud ADD COLUMN description VARCHAR(500);

-- D-14b: seed the system-defined column-config row for the new Description
-- column so it is importable at runtime without a code deploy. Next free
-- literal UUID after V37's sequence (...0001 through ...0019) is ...0020.
INSERT INTO masterdata_column_config
    (id, created_at, updated_at, is_deleted, version, masterdata_type, column_name, target_field, required, data_type, system_defined)
VALUES
    ('00000000-0000-7000-9000-000000000020', now(), now(), false, 0, 'PRANA_COLOR', 'Description', 'description', false, 'STRING', true);
