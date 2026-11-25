-- The chief complaint becomes problem/duration rows (up to 10 per case), and each row also carries the
-- patient's history of presenting illness for that problem (onset, location, character, aggravating and
-- relieving factors, timing). Free text is encrypted at rest like the rest of the clinical text.
CREATE TABLE case_chief_complaint (
    id                  UUID         NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    version             BIGINT       NOT NULL DEFAULT 0,
    healing_case_id     UUID         NOT NULL,
    sort_order          INTEGER      NOT NULL DEFAULT 0,
    problem             TEXT         NOT NULL,           -- AES-256 encrypted at rest
    -- Both null only on a row migrated from the old free-text complaint ("legacy"); the API requires both on save.
    duration_value      INTEGER,
    duration_unit       VARCHAR(10),
    onset               VARCHAR(10),
    location            TEXT,                            -- encrypted
    character_text      TEXT,                            -- encrypted
    aggravating_factor  TEXT,                            -- encrypted
    relieving_factor    TEXT,                            -- encrypted
    timing              VARCHAR(15),
    timing_pattern      TEXT,                            -- encrypted
    CONSTRAINT pk_case_chief_complaint      PRIMARY KEY (id),
    CONSTRAINT fk_case_chief_complaint_case FOREIGN KEY (healing_case_id) REFERENCES healing_case(id),
    CONSTRAINT ck_case_chief_complaint_duration_unit
        CHECK (duration_unit IS NULL OR duration_unit IN ('HOURS','DAYS','MONTHS','YEARS')),
    CONSTRAINT ck_case_chief_complaint_duration_value
        CHECK (duration_value IS NULL OR duration_value > 0),
    CONSTRAINT ck_case_chief_complaint_onset
        CHECK (onset IS NULL OR onset IN ('SUDDEN','GRADUAL')),
    CONSTRAINT ck_case_chief_complaint_timing
        CHECK (timing IS NULL OR timing IN ('CONSTANT','INTERMITTENT','PATTERN'))
);

CREATE INDEX idx_case_chief_complaint_case ON case_chief_complaint (healing_case_id);

CREATE TABLE case_chief_complaint_aud (
    id                  UUID,
    rev                 BIGINT      NOT NULL,
    revtype             SMALLINT,
    created_at          TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    is_deleted          BOOLEAN,
    version             BIGINT,
    healing_case_id     UUID,
    sort_order          INTEGER,
    problem             TEXT,
    duration_value      INTEGER,
    duration_unit       VARCHAR(10),
    onset               VARCHAR(10),
    location            TEXT,
    character_text      TEXT,
    aggravating_factor  TEXT,
    relieving_factor    TEXT,
    timing              VARCHAR(15),
    timing_pattern      TEXT,
    CONSTRAINT pk_case_chief_complaint_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_case_chief_complaint_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

-- Existing cases: the old free-text complaint becomes one legacy row without a duration. The text is copied
-- as stored (already encrypted with the same key), so it needs no re-encryption.
INSERT INTO case_chief_complaint (id, created_at, updated_at, is_deleted, version, healing_case_id, sort_order, problem)
SELECT gen_random_uuid(), now(), now(), FALSE, 0, id, 0, medical_condition
FROM healing_case
WHERE is_deleted = FALSE
  AND medical_condition IS NOT NULL
  AND btrim(medical_condition) <> '';
