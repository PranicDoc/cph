-- =============================================================================
-- V39__create_protocol_chakra_color_usage_table.sql
-- Phase (masterdata-import-redesign) — chakra/color enrichment for protocol
-- import (MDIMPORT-22).
--
-- Deliberately a NEW table, not a change to protocol_color_mapping: that
-- table's unique constraint (protocol_name, color_id, usage_type, healer_id)
-- has no notion of chakra, so the same protocol+color+usage-type applied to
-- two different chakras (a real, common shape in the source data) would
-- collide on it. protocol_color_mapping / its importer are untouched.
--
-- Mirrors prana_color_catalog's own @ElementCollection shape (V18/V37/V38)
-- for cleansing_color_ids/energizing_color_ids — two child tables, each with
-- its own Envers _aud shadow table, real FK to prana_color_catalog(id).
-- =============================================================================

CREATE TABLE protocol_chakra_color_usage (
    id              UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    version         BIGINT       NOT NULL DEFAULT 0,
    protocol_name   VARCHAR(255) NOT NULL,
    chakra_id       UUID         NOT NULL,
    healer_id       UUID,
    CONSTRAINT pk_protocol_chakra_color_usage PRIMARY KEY (id),
    CONSTRAINT fk_protocol_chakra_color_usage_chakra FOREIGN KEY (chakra_id) REFERENCES chakra_catalog(id),
    CONSTRAINT uq_protocol_chakra_color_usage UNIQUE (protocol_name, chakra_id, healer_id)
);

CREATE TABLE protocol_chakra_color_usage_aud (
    id              UUID,
    rev             BIGINT NOT NULL,
    revtype         SMALLINT,
    created_at      TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    is_deleted      BOOLEAN,
    version         BIGINT,
    protocol_name   VARCHAR(255),
    chakra_id       UUID,
    healer_id       UUID,
    CONSTRAINT pk_protocol_chakra_color_usage_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_protocol_chakra_color_usage_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE protocol_chakra_color_usage_cleansing_color_ids (
    protocol_chakra_color_usage_id  UUID NOT NULL,
    cleansing_color_ids             UUID,
    CONSTRAINT fk_pccu_cleansing_color_ids_parent FOREIGN KEY (protocol_chakra_color_usage_id) REFERENCES protocol_chakra_color_usage(id),
    CONSTRAINT fk_pccu_cleansing_color_ids_color  FOREIGN KEY (cleansing_color_ids) REFERENCES prana_color_catalog(id)
);

CREATE TABLE protocol_chakra_color_usage_cleansing_color_ids_aud (
    protocol_chakra_color_usage_id  UUID,
    rev                              BIGINT NOT NULL,
    revtype                          SMALLINT,
    cleansing_color_ids              UUID,
    CONSTRAINT pk_pccu_cleansing_color_ids_aud     PRIMARY KEY (protocol_chakra_color_usage_id, rev, cleansing_color_ids),
    CONSTRAINT fk_pccu_cleansing_color_ids_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE protocol_chakra_color_usage_energizing_color_ids (
    protocol_chakra_color_usage_id  UUID NOT NULL,
    energizing_color_ids            UUID,
    CONSTRAINT fk_pccu_energizing_color_ids_parent FOREIGN KEY (protocol_chakra_color_usage_id) REFERENCES protocol_chakra_color_usage(id),
    CONSTRAINT fk_pccu_energizing_color_ids_color  FOREIGN KEY (energizing_color_ids) REFERENCES prana_color_catalog(id)
);

CREATE TABLE protocol_chakra_color_usage_energizing_color_ids_aud (
    protocol_chakra_color_usage_id  UUID,
    rev                              BIGINT NOT NULL,
    revtype                          SMALLINT,
    energizing_color_ids             UUID,
    CONSTRAINT pk_pccu_energizing_color_ids_aud     PRIMARY KEY (protocol_chakra_color_usage_id, rev, energizing_color_ids),
    CONSTRAINT fk_pccu_energizing_color_ids_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);
