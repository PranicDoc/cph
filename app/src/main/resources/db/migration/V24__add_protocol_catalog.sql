-- =============================================================================
-- V24__add_protocol_catalog.sql
-- Adds protocol_catalog: master list of named pranic-healing protocols /
-- techniques (bulk-imported from the reference books via xlsx/csv), split
-- into PROTOCOL vs SUPPLEMENTARY. No changes to healing_case/healing_details
-- — the existing basic_book_pg/advanced_book_pg/psychotherapy_pg and
-- healing_details.protocol_name columns are reused as-is by the UI.
-- =============================================================================

CREATE TABLE protocol_catalog (
    id            UUID         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    version       BIGINT       NOT NULL DEFAULT 0,
    name          VARCHAR(255) NOT NULL,
    source_book   VARCHAR(255) NOT NULL,
    page_number   INTEGER      NOT NULL,
    category      VARCHAR(255),
    description   TEXT,
    chapter       VARCHAR(255),
    protocol_type VARCHAR(20)  NOT NULL,
    healer_id     UUID,                              -- NULL = system-level
    CONSTRAINT pk_protocol_catalog PRIMARY KEY (id),
    CONSTRAINT uq_protocol_catalog_name_book_page UNIQUE (name, source_book, page_number),
    CONSTRAINT ck_protocol_catalog_protocol_type CHECK (protocol_type IN ('PROTOCOL','SUPPLEMENTARY'))
);

CREATE INDEX idx_protocol_catalog_type ON protocol_catalog (protocol_type);
CREATE INDEX idx_protocol_catalog_healer ON protocol_catalog (healer_id);

-- Envers audit table
CREATE TABLE protocol_catalog_aud (
    id            UUID,
    rev           BIGINT      NOT NULL,
    revtype       SMALLINT,
    created_at    TIMESTAMPTZ,
    updated_at    TIMESTAMPTZ,
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    is_deleted    BOOLEAN,
    version       BIGINT,
    name          VARCHAR(255),
    source_book   VARCHAR(255),
    page_number   INTEGER,
    category      VARCHAR(255),
    description   TEXT,
    chapter       VARCHAR(255),
    protocol_type VARCHAR(20),
    healer_id     UUID,
    CONSTRAINT pk_protocol_catalog_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_protocol_catalog_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

ALTER TABLE protocol_catalog_aud
    ADD CONSTRAINT ck_protocol_catalog_aud_protocol_type
        CHECK (protocol_type IS NULL OR protocol_type IN ('PROTOCOL','SUPPLEMENTARY'));
