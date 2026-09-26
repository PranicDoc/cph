-- =============================================================================
-- V37__add_protocol_chakra_color_relationships.sql
-- Protocol-Chakra-Color master data relationships.
--
-- Models:
--   ProtocolCatalog <-> ChakraCatalog        (many-to-many via protocol_chakra)
--   (ProtocolCatalog + ChakraCatalog) <-> ColorCatalog, tagged CLEANSING or
--   ENERGISING (via protocol_chakra_color, keyed off protocol_chakra not the
--   bare protocol/chakra pair, since a color's usage is specific to one
--   protocol+chakra combination).
--
-- Deliberately NOT built on protocol_color_mapping: that table's unique
-- constraint is (protocol_name, color_id, usage_type, healer_id) — free-text
-- protocol_name, no chakra_id at all — so the same protocol+color+usage-type
-- applied to two different chakras would collide on it. protocol_color_mapping
-- and its service methods are untouched; this is a separate, real-FK-backed
-- normalized structure for the chakra-scoped shape of data.
--
-- ChakraCatalog and PranaColorCatalog gain no reverse mapping — both stay
-- independent catalogs, referenced unidirectionally via real @ManyToOne FKs
-- from the two new junction tables.
-- =============================================================================

CREATE TABLE protocol_chakra (
    id            UUID         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    version       BIGINT       NOT NULL DEFAULT 0,
    protocol_id   UUID         NOT NULL,
    chakra_id     UUID         NOT NULL,
    CONSTRAINT pk_protocol_chakra PRIMARY KEY (id),
    CONSTRAINT uq_protocol_chakra UNIQUE (protocol_id, chakra_id),
    CONSTRAINT fk_protocol_chakra_protocol FOREIGN KEY (protocol_id) REFERENCES protocol_catalog(id),
    CONSTRAINT fk_protocol_chakra_chakra   FOREIGN KEY (chakra_id) REFERENCES chakra_catalog(id)
);

CREATE INDEX idx_protocol_chakra_protocol ON protocol_chakra (protocol_id);
CREATE INDEX idx_protocol_chakra_chakra   ON protocol_chakra (chakra_id);

CREATE TABLE protocol_chakra_color (
    id                 UUID         NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    created_by         VARCHAR(255),
    updated_by         VARCHAR(255),
    is_deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    version            BIGINT       NOT NULL DEFAULT 0,
    protocol_chakra_id UUID         NOT NULL,
    color_id           UUID         NOT NULL,
    usage_type         VARCHAR(15)  NOT NULL,
    CONSTRAINT pk_protocol_chakra_color PRIMARY KEY (id),
    CONSTRAINT uq_protocol_chakra_color UNIQUE (protocol_chakra_id, color_id, usage_type),
    CONSTRAINT fk_protocol_chakra_color_link  FOREIGN KEY (protocol_chakra_id) REFERENCES protocol_chakra(id),
    CONSTRAINT fk_protocol_chakra_color_color FOREIGN KEY (color_id) REFERENCES prana_color_catalog(id),
    CONSTRAINT ck_protocol_chakra_color_usage_type CHECK (usage_type IN ('CLEANSING','ENERGISING'))
);

CREATE INDEX idx_protocol_chakra_color_link  ON protocol_chakra_color (protocol_chakra_id);
CREATE INDEX idx_protocol_chakra_color_color ON protocol_chakra_color (color_id);

-- =============================================================================
-- Envers audit-history shadow tables — required because BaseEntity is
-- @Audited at the mapped-superclass level, so both entities above are
-- audited whether or not this table exists. Omitting a shadow table fails
-- the first insert/update with SQLGrammarException (documented pitfall,
-- see V27/V28/V39-style comments elsewhere in this migration history).
-- =============================================================================

CREATE TABLE protocol_chakra_aud (
    id            UUID,
    rev           BIGINT NOT NULL,
    revtype       SMALLINT,
    created_at    TIMESTAMPTZ,
    updated_at    TIMESTAMPTZ,
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    is_deleted    BOOLEAN,
    version       BIGINT,
    protocol_id   UUID,
    chakra_id     UUID,
    CONSTRAINT pk_protocol_chakra_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_protocol_chakra_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE protocol_chakra_color_aud (
    id                 UUID,
    rev                BIGINT NOT NULL,
    revtype            SMALLINT,
    created_at         TIMESTAMPTZ,
    updated_at         TIMESTAMPTZ,
    created_by         VARCHAR(255),
    updated_by         VARCHAR(255),
    is_deleted         BOOLEAN,
    version            BIGINT,
    protocol_chakra_id UUID,
    color_id           UUID,
    usage_type         VARCHAR(15),
    CONSTRAINT pk_protocol_chakra_color_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_protocol_chakra_color_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

ALTER TABLE protocol_chakra_color_aud
    ADD CONSTRAINT ck_protocol_chakra_color_aud_usage_type
        CHECK (usage_type IS NULL OR usage_type IN ('CLEANSING','ENERGISING'));
