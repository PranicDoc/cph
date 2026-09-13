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
