CREATE TABLE protocol_color_mapping (
    id                UUID         NOT NULL PRIMARY KEY,
    protocol_name     VARCHAR(255) NOT NULL,
    color_id          UUID         NOT NULL REFERENCES prana_color_catalog(id),
    usage_type        VARCHAR(20)  NOT NULL,
    clinic_id         UUID,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    version           BIGINT       NOT NULL DEFAULT 0,
    UNIQUE (protocol_name, color_id, usage_type, clinic_id)
);

CREATE TABLE healer_protocol_understanding (
    id                       UUID         NOT NULL PRIMARY KEY,
    clinic_id                UUID         NOT NULL,
    healing_case_id          UUID         NOT NULL UNIQUE,
    healer_id                UUID         NOT NULL,
    completion_date             DATE,
    protocol_name            VARCHAR(255),
    cleansing_colors_json    TEXT,
    energising_colors_json   TEXT,
    chakra_readings_json     TEXT,
    created_at               TIMESTAMP,
    updated_at               TIMESTAMP,
    created_by               VARCHAR(255),
    updated_by               VARCHAR(255),
    is_deleted               BOOLEAN      NOT NULL DEFAULT FALSE,
    version                  BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_hpu_healing_case    ON healer_protocol_understanding(healing_case_id);
CREATE INDEX idx_pcm_protocol_name   ON protocol_color_mapping(protocol_name);

-- Envers audit tables for the two new @Audited entities introduced in this migration.
-- Required for fresh installs where V0 does not yet include these tables.

CREATE TABLE protocol_color_mapping_aud (
    id             UUID,
    rev            BIGINT      NOT NULL,
    revtype        SMALLINT,
    created_at     TIMESTAMPTZ,
    updated_at     TIMESTAMPTZ,
    created_by     VARCHAR(255),
    updated_by     VARCHAR(255),
    is_deleted     BOOLEAN,
    version        BIGINT,
    protocol_name  VARCHAR(255),
    color_id       UUID,
    usage_type     VARCHAR(20),
    clinic_id      UUID,
    CONSTRAINT pk_protocol_color_mapping_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_protocol_color_mapping_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE healer_protocol_understanding_aud (
    id                      UUID,
    rev                     BIGINT      NOT NULL,
    revtype                 SMALLINT,
    created_at              TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ,
    created_by              VARCHAR(255),
    updated_by              VARCHAR(255),
    is_deleted              BOOLEAN,
    version                 BIGINT,
    clinic_id               UUID,
    healing_case_id         UUID,
    healer_id               UUID,
    completion_date         DATE,
    protocol_name           VARCHAR(255),
    cleansing_colors_json   TEXT,
    energising_colors_json  TEXT,
    chakra_readings_json    TEXT,
    CONSTRAINT pk_healer_protocol_understanding_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_healer_protocol_understanding_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);
