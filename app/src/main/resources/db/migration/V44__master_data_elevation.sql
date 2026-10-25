-- =============================================================================
-- V44__master_data_elevation.sql
-- A user whose role holds MASTER_DATA_*:ELEVATE can confirm their password to
-- edit the shared (system) rows of that master-data type for a limited time.
-- One row per unlock; it is active while expires_at is in the future.
-- =============================================================================

CREATE TABLE master_data_elevation (
    id          UUID         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    version     BIGINT       NOT NULL DEFAULT 0,
    healer_id   UUID         NOT NULL,
    resource    VARCHAR(40)  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_master_data_elevation PRIMARY KEY (id),
    CONSTRAINT fk_master_data_elevation_healer FOREIGN KEY (healer_id) REFERENCES healer(id),
    CONSTRAINT ck_master_data_elevation_resource
        CHECK (resource IN ('MASTER_DATA_CHAKRAS','MASTER_DATA_COLORS','MASTER_DATA_PROTOCOLS'))
);

CREATE INDEX idx_master_data_elevation_active ON master_data_elevation (healer_id, resource, expires_at);

-- Envers audit-history shadow table (see V37: required for any entity extending BaseEntity).
CREATE TABLE master_data_elevation_aud (
    id          UUID,
    rev         BIGINT NOT NULL,
    revtype     SMALLINT,
    created_at  TIMESTAMPTZ,
    updated_at  TIMESTAMPTZ,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN,
    version     BIGINT,
    healer_id   UUID,
    resource    VARCHAR(40),
    expires_at  TIMESTAMPTZ,
    CONSTRAINT pk_master_data_elevation_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_master_data_elevation_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);
