-- =============================================================================
-- V0__baseline_schema.sql
-- Full baseline schema — replaces ddl-auto=update for fresh installs.
-- Represents the database state BEFORE any incremental migrations (V1+).
--
-- Usage:
--   Fresh DB  : Flyway runs V0 → V1 → V2 → … → V6 in order.
--   Existing DB: baseline-on-migrate=true with baseline-version=1 skips V0
--               (the schema already exists from ddl-auto=update + V1-V5 manual).
-- =============================================================================

-- =============================================================================
-- ENVERS REVISION INFRASTRUCTURE
-- Default Hibernate Envers revision entity (DefaultRevisionEntity).
-- revtype: 0=INSERT, 1=UPDATE, 2=DELETE
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS revinfo_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE revinfo (
    rev       BIGINT NOT NULL DEFAULT nextval('revinfo_seq'),
    revtstmp  BIGINT NOT NULL,
    CONSTRAINT pk_revinfo PRIMARY KEY (rev)
);

-- =============================================================================
-- MASTER DATA — no tenant scoping, no soft-delete awareness needed at DB level
-- =============================================================================

-- Chakra catalog: system-level (clinic_id IS NULL) or clinic-custom (clinic_id set)
CREATE TABLE chakra_catalog (
    id          UUID         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    version     BIGINT       NOT NULL DEFAULT 0,
    code        VARCHAR(30)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    category    VARCHAR(255),
    location    VARCHAR(255),
    clinic_id   UUID,                              -- NULL = system-level
    CONSTRAINT pk_chakra_catalog      PRIMARY KEY (id),
    CONSTRAINT uq_chakra_catalog_code UNIQUE (code)
);

-- Prana color catalog: system-level or clinic-custom
CREATE TABLE prana_color_catalog (
    id          UUID         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    version     BIGINT       NOT NULL DEFAULT 0,
    code        VARCHAR(30)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    color_hex   VARCHAR(7),
    properties  TEXT,
    clinic_id   UUID,                              -- NULL = system-level
    CONSTRAINT pk_prana_color_catalog      PRIMARY KEY (id),
    CONSTRAINT uq_prana_color_catalog_code UNIQUE (code)
);

-- =============================================================================
-- CLINIC — the tenant root; extends BaseEntity (not TenantEntity)
-- =============================================================================

CREATE TABLE clinic (
    id            UUID         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    version       BIGINT       NOT NULL DEFAULT 0,
    code          VARCHAR(20)  NOT NULL,
    name          VARCHAR(255) NOT NULL,
    phone         VARCHAR(255),
    email         VARCHAR(255),
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city          VARCHAR(255),
    state         VARCHAR(255),
    zip           VARCHAR(10),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_clinic      PRIMARY KEY (id),
    CONSTRAINT uq_clinic_code UNIQUE (code)
);

-- =============================================================================
-- HEALER — tenant-scoped practitioner
-- Role values    : HEALER, SENIOR_HEALER, RECEPTIONIST, ADMIN, SUPADM
-- HealingLevel   : BASIC, ADVANCED, PSYCHOTHERAPY
-- NOTE: auth_provider column is added by V6.
-- =============================================================================

CREATE TABLE healer (
    id            UUID         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    version       BIGINT       NOT NULL DEFAULT 0,
    clinic_id     UUID         NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    phone         VARCHAR(255),
    password_hash VARCHAR(255),
    role          VARCHAR(20)  NOT NULL,
    healing_level VARCHAR(20),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_healer PRIMARY KEY (id)
);

-- =============================================================================
-- PATIENT — tenant-scoped; encrypted PII fields stored as VARCHAR/TEXT
-- Gender values        : MALE, FEMALE, OTHER
-- MaritalStatus values : SINGLE, MARRIED, DIVORCED, WIDOWED
-- NOTE: healer_id column is added by V3.
-- =============================================================================

CREATE TABLE patient (
    id                UUID         NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    version           BIGINT       NOT NULL DEFAULT 0,
    clinic_id         UUID         NOT NULL,
    first_name        VARCHAR(255) NOT NULL,
    last_name         VARCHAR(255) NOT NULL,
    dob               DATE,
    gender            VARCHAR(10),
    marital_status    VARCHAR(10),
    email             VARCHAR(255),               -- AES-256 encrypted at rest
    contact_whatsapp  VARCHAR(255),               -- AES-256 encrypted at rest
    occupation        VARCHAR(255),
    address_line1     VARCHAR(255),               -- AES-256 encrypted at rest
    address_line2     VARCHAR(255),               -- AES-256 encrypted at rest
    city              VARCHAR(255),
    state             VARCHAR(255),
    zip               VARCHAR(10),
    CONSTRAINT pk_patient PRIMARY KEY (id)
);

-- Patient medical history — living master record; 1:1 with patient
CREATE TABLE patient_medical_history (
    id                         UUID         NOT NULL,
    created_at                 TIMESTAMPTZ  NOT NULL,
    updated_at                 TIMESTAMPTZ  NOT NULL,
    created_by                 VARCHAR(255),
    updated_by                 VARCHAR(255),
    is_deleted                 BOOLEAN      NOT NULL DEFAULT FALSE,
    version                    BIGINT       NOT NULL DEFAULT 0,
    clinic_id                  UUID         NOT NULL,
    patient_id                 UUID         NOT NULL,
    general_health             VARCHAR(255),
    diet                       VARCHAR(255),
    exercise                   VARCHAR(255),
    exercise_frequency         VARCHAR(255),
    hypertension               BOOLEAN,
    gi_issues                  BOOLEAN,
    stress_depression_anxiety  BOOLEAN,
    pregnant                   BOOLEAN,
    cancer                     BOOLEAN,
    cancer_location            VARCHAR(255),        -- AES-256 encrypted at rest
    cancer_details             VARCHAR(255),        -- AES-256 encrypted at rest
    additional_notes           TEXT,                -- AES-256 encrypted at rest
    CONSTRAINT pk_patient_medical_history     PRIMARY KEY (id),
    CONSTRAINT uq_patient_medical_history_pid UNIQUE (patient_id),
    CONSTRAINT fk_patient_medical_history_pid FOREIGN KEY (patient_id) REFERENCES patient(id)
);

-- Registration link — one-time patient onboarding URL token
-- Status values: ACTIVE, IN_PROGRESS, USED
CREATE TABLE registration_link (
    uuid        VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ,
    status      VARCHAR(255),
    CONSTRAINT pk_registration_link PRIMARY KEY (uuid)
);

-- =============================================================================
-- HEALING CASE — aggregate root for the CPH form
-- CaseStatus values: DRAFT, ACTIVE, CLOSED
-- =============================================================================

CREATE TABLE healing_case (
    id                        UUID         NOT NULL,
    created_at                TIMESTAMPTZ  NOT NULL,
    updated_at                TIMESTAMPTZ  NOT NULL,
    created_by                VARCHAR(255),
    updated_by                VARCHAR(255),
    is_deleted                BOOLEAN      NOT NULL DEFAULT FALSE,
    version                   BIGINT       NOT NULL DEFAULT 0,
    clinic_id                 UUID         NOT NULL,
    patient_id                UUID         NOT NULL,
    healer_id                 UUID         NOT NULL,
    medical_condition         TEXT,                -- AES-256 encrypted at rest
    lab_findings              TEXT,                -- AES-256 encrypted at rest
    complementary_treatments  BOOLEAN,
    complementary_details     TEXT,                -- AES-256 encrypted at rest
    infectious_illness        BOOLEAN,
    infectious_details        TEXT,                -- AES-256 encrypted at rest
    basic_book_pg             VARCHAR(255),
    advanced_book_pg          VARCHAR(255),
    psychotherapy_pg          VARCHAR(255),
    status                    VARCHAR(10)  NOT NULL DEFAULT 'DRAFT',
    CONSTRAINT pk_healing_case PRIMARY KEY (id)
);

-- Declaration — patient consent record; required before accessing session data
CREATE TABLE declaration (
    id                  UUID         NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    version             BIGINT       NOT NULL DEFAULT 0,
    healing_case_id     UUID         NOT NULL,
    patient_consent     BOOLEAN      NOT NULL,
    healer_declaration  BOOLEAN      NOT NULL,
    signed_at           TIMESTAMPTZ,
    patient_signature   VARCHAR(255),
    healer_signature    VARCHAR(255),
    CONSTRAINT pk_declaration      PRIMARY KEY (id),
    CONSTRAINT uq_declaration_case UNIQUE (healing_case_id),
    CONSTRAINT fk_declaration_case FOREIGN KEY (healing_case_id) REFERENCES healing_case(id)
);

-- Case medical snapshot — frozen copy of patient history at case creation time
CREATE TABLE case_medical_snapshot (
    id                         UUID         NOT NULL,
    created_at                 TIMESTAMPTZ  NOT NULL,
    updated_at                 TIMESTAMPTZ  NOT NULL,
    created_by                 VARCHAR(255),
    updated_by                 VARCHAR(255),
    is_deleted                 BOOLEAN      NOT NULL DEFAULT FALSE,
    version                    BIGINT       NOT NULL DEFAULT 0,
    healing_case_id            UUID         NOT NULL,
    general_health             VARCHAR(255),
    diet                       VARCHAR(255),
    exercise                   VARCHAR(255),
    exercise_frequency         VARCHAR(255),
    hypertension               BOOLEAN,
    gi_issues                  BOOLEAN,
    stress_depression_anxiety  BOOLEAN,
    pregnant                   BOOLEAN,
    cancer                     BOOLEAN,
    cancer_location            VARCHAR(255),        -- AES-256 encrypted at rest
    cancer_details             VARCHAR(255),        -- AES-256 encrypted at rest
    additional_notes           TEXT,                -- AES-256 encrypted at rest
    CONSTRAINT pk_case_medical_snapshot      PRIMARY KEY (id),
    CONSTRAINT uq_case_medical_snapshot_case UNIQUE (healing_case_id),
    CONSTRAINT fk_case_medical_snapshot_case FOREIGN KEY (healing_case_id) REFERENCES healing_case(id)
);

-- Case symptom — evolving symptom list per case (add/resolve mid-case)
CREATE TABLE case_symptom (
    id              UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    version         BIGINT       NOT NULL DEFAULT 0,
    healing_case_id UUID         NOT NULL,
    symptom_name    VARCHAR(255) NOT NULL,
    description     VARCHAR(255),
    severity        INTEGER,
    added_date      DATE,
    resolved_date   DATE,
    resolved        BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_case_symptom      PRIMARY KEY (id),
    CONSTRAINT fk_case_symptom_case FOREIGN KEY (healing_case_id) REFERENCES healing_case(id)
);

-- Case medication — medications active during the case
-- NOTE: V5 drops `frequency` and replaces it with morning_dose/afternoon_dose/night_dose.
CREATE TABLE case_medication (
    id               UUID         NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    version          BIGINT       NOT NULL DEFAULT 0,
    healing_case_id  UUID         NOT NULL,
    medication_name  VARCHAR(255) NOT NULL,
    dosage           VARCHAR(255),
    frequency        VARCHAR(255),                  -- replaced by dosing booleans in V5
    notes            VARCHAR(255),
    CONSTRAINT pk_case_medication      PRIMARY KEY (id),
    CONSTRAINT fk_case_medication_case FOREIGN KEY (healing_case_id) REFERENCES healing_case(id)
);

-- Healing details — protocol and step breakdown for a case
CREATE TABLE healing_details (
    id                UUID         NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    version           BIGINT       NOT NULL DEFAULT 0,
    healing_case_id   UUID         NOT NULL,
    protocol_name     VARCHAR(255),
    book_reference    VARCHAR(255),
    detailed_protocol TEXT,
    CONSTRAINT pk_healing_details      PRIMARY KEY (id),
    CONSTRAINT uq_healing_details_case UNIQUE (healing_case_id),
    CONSTRAINT fk_healing_details_case FOREIGN KEY (healing_case_id) REFERENCES healing_case(id)
);

-- Case closure — created only when a case is formally closed
CREATE TABLE case_closure (
    id                    UUID         NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    created_by            VARCHAR(255),
    updated_by            VARCHAR(255),
    is_deleted            BOOLEAN      NOT NULL DEFAULT FALSE,
    version               BIGINT       NOT NULL DEFAULT 0,
    clinic_id             UUID         NOT NULL,
    healing_case_id       UUID         NOT NULL,
    closure_date          DATE,
    additional_comments   TEXT,
    healing_count         INTEGER,
    is_healing_continued  BOOLEAN,
    last_feedback_date    DATE,
    healer_signature      VARCHAR(255),
    closed_by             UUID,
    CONSTRAINT pk_case_closure      PRIMARY KEY (id),
    CONSTRAINT uq_case_closure_case UNIQUE (healing_case_id),
    CONSTRAINT fk_case_closure_case FOREIGN KEY (healing_case_id) REFERENCES healing_case(id)
);

-- =============================================================================
-- HEALING SESSION AND CHILDREN
-- SessionStatus values: DRAFT, COMPLETED
-- =============================================================================

CREATE TABLE healing_session (
    id               UUID         NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    version          BIGINT       NOT NULL DEFAULT 0,
    clinic_id        UUID         NOT NULL,
    healing_case_id  UUID         NOT NULL,
    session_number   INTEGER,
    session_date     DATE,
    start_time       TIME,
    end_time         TIME,
    status           VARCHAR(10)  NOT NULL DEFAULT 'DRAFT',
    CONSTRAINT pk_healing_session      PRIMARY KEY (id),
    CONSTRAINT fk_healing_session_case FOREIGN KEY (healing_case_id) REFERENCES healing_case(id)
);

-- Chakra scan — PRE or POST scan per session (max 2 per session)
-- ScanType values: PRE, POST
CREATE TABLE chakra_scan (
    id                  UUID         NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    version             BIGINT       NOT NULL DEFAULT 0,
    clinic_id           UUID         NOT NULL,
    healing_session_id  UUID         NOT NULL,
    scan_type           VARCHAR(10)  NOT NULL,
    notes               TEXT,
    CONSTRAINT pk_chakra_scan         PRIMARY KEY (id),
    CONSTRAINT fk_chakra_scan_session FOREIGN KEY (healing_session_id) REFERENCES healing_session(id)
);

-- Chakra reading — one measurement per chakra per scan; unique(scan, chakra)
CREATE TABLE chakra_reading (
    id               UUID         NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    version          BIGINT       NOT NULL DEFAULT 0,
    clinic_id        UUID         NOT NULL,
    chakra_scan_id   UUID         NOT NULL,
    chakra_id        UUID         NOT NULL,          -- logical FK → chakra_catalog(id)
    chakra_name      VARCHAR(255),
    energy_level     VARCHAR(255),
    activation_level VARCHAR(255),
    notes            VARCHAR(255),
    CONSTRAINT pk_chakra_reading             PRIMARY KEY (id),
    CONSTRAINT uq_chakra_reading_scan_chakra UNIQUE (chakra_scan_id, chakra_id),
    CONSTRAINT fk_chakra_reading_scan        FOREIGN KEY (chakra_scan_id) REFERENCES chakra_scan(id)
);

-- Treatment step — individual step within a healing_details protocol
CREATE TABLE treatment_step (
    id                  UUID         NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    version             BIGINT       NOT NULL DEFAULT 0,
    healing_details_id  UUID,
    tag                 VARCHAR(255) NOT NULL,
    sweep_type          VARCHAR(255),
    notes               VARCHAR(255),
    cycles              INTEGER      NOT NULL DEFAULT 0,
    step                INTEGER      NOT NULL,
    color               VARCHAR(255),
    CONSTRAINT pk_treatment_step         PRIMARY KEY (id),
    CONSTRAINT fk_treatment_step_details FOREIGN KEY (healing_details_id) REFERENCES healing_details(id)
);

-- Treatment step targets — @ElementCollection List<String> on TreatmentStep
CREATE TABLE treatment_step_targets (
    treatment_step_id  UUID         NOT NULL,
    targets            VARCHAR(255),
    CONSTRAINT fk_treatment_step_targets FOREIGN KEY (treatment_step_id) REFERENCES treatment_step(id)
);

-- Protocol entry — per-chakra/organ protocol entry per session (@Deprecated but kept)
CREATE TABLE protocol_entry (
    id                  UUID         NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    version             BIGINT       NOT NULL DEFAULT 0,
    clinic_id           UUID         NOT NULL,
    healing_session_id  UUID         NOT NULL,
    chakra_id           UUID,                        -- logical FK → chakra_catalog(id)
    target_area         VARCHAR(255),
    technique           VARCHAR(255),
    notes               VARCHAR(255),
    protocol_name       VARCHAR(255),
    reference_book      VARCHAR(255),
    detailed_protocol   TEXT,
    CONSTRAINT pk_protocol_entry         PRIMARY KEY (id),
    CONSTRAINT fk_protocol_entry_session FOREIGN KEY (healing_session_id) REFERENCES healing_session(id)
);

-- Protocol color — prana color used in a protocol entry
-- ColorUsageType values: CLEANSING, ENERGISING
CREATE TABLE protocol_color (
    id                UUID         NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    version           BIGINT       NOT NULL DEFAULT 0,
    clinic_id         UUID         NOT NULL,
    protocol_entry_id UUID         NOT NULL,
    color_id          UUID         NOT NULL,          -- logical FK → prana_color_catalog(id)
    usage_type        VARCHAR(15)  NOT NULL,
    CONSTRAINT pk_protocol_color                  PRIMARY KEY (id),
    CONSTRAINT uq_protocol_color_entry_color_type UNIQUE (protocol_entry_id, color_id, usage_type),
    CONSTRAINT fk_protocol_color_entry            FOREIGN KEY (protocol_entry_id) REFERENCES protocol_entry(id)
);

-- =============================================================================
-- FEEDBACK ENTITIES
-- FeedbackType values: INTERIM, FINAL
-- =============================================================================

-- Symptom feedback — patient's per-session severity rating (0-10) per symptom
CREATE TABLE symptom_feedback (
    id                  UUID     NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    is_deleted          BOOLEAN  NOT NULL DEFAULT FALSE,
    version             BIGINT   NOT NULL DEFAULT 0,
    clinic_id           UUID     NOT NULL,
    healing_session_id  UUID     NOT NULL,
    case_symptom_id     UUID     NOT NULL,           -- logical FK → case_symptom(id)
    rating              INTEGER  NOT NULL,
    notes               VARCHAR(255),
    CONSTRAINT pk_symptom_feedback                 PRIMARY KEY (id),
    CONSTRAINT uq_symptom_feedback_session_symptom UNIQUE (healing_session_id, case_symptom_id),
    CONSTRAINT fk_symptom_feedback_session         FOREIGN KEY (healing_session_id) REFERENCES healing_session(id)
);

-- Qualitative feedback — free-form interim or final feedback per session
CREATE TABLE qualitative_feedback (
    id                  UUID         NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    version             BIGINT       NOT NULL DEFAULT 0,
    clinic_id           UUID         NOT NULL,
    healing_session_id  UUID         NOT NULL,
    feedback_type       VARCHAR(10)  NOT NULL,
    feedback_text       TEXT,
    patient_remarks     TEXT,
    CONSTRAINT pk_qualitative_feedback         PRIMARY KEY (id),
    CONSTRAINT fk_qualitative_feedback_session FOREIGN KEY (healing_session_id) REFERENCES healing_session(id)
);

-- =============================================================================
-- SUPPORT TABLES
-- =============================================================================

-- PDF job — tracks async PDF generation jobs
-- JobStatus values: DRAFT, PROCESSING, STARTED, COMPLETED, FAILED
CREATE TABLE pdf_job (
    id                 UUID         NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    created_by         VARCHAR(255),
    updated_by         VARCHAR(255),
    is_deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    version            BIGINT       NOT NULL DEFAULT 0,
    job_id             VARCHAR(255),
    healing_case_id    UUID,
    healing_case_data  JSONB        NOT NULL,
    retry_count        INTEGER      NOT NULL DEFAULT 0,
    document_version   INTEGER      NOT NULL DEFAULT 1,
    status             VARCHAR(255),
    file_url           VARCHAR(255),
    CONSTRAINT pk_pdf_job PRIMARY KEY (id)
);

-- Audit log — action-level audit trail (field-level history is via Envers)
CREATE TABLE audit_log (
    id            UUID         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    version       BIGINT       NOT NULL DEFAULT 0,
    healer_id     UUID,
    clinic_id     UUID,
    action        VARCHAR(255) NOT NULL,
    entity_type   VARCHAR(255),
    entity_id     UUID,
    payload       TEXT,
    ip_address    VARCHAR(255),
    performed_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_audit_log PRIMARY KEY (id)
);

-- Notification outbox — transactional outbox for WhatsApp dispatch
-- NotificationStatus values: PENDING, PROCESSING, PROCESSED, FAILED, DLQ
CREATE TABLE notification_outbox (
    id            UUID         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    version       BIGINT       NOT NULL DEFAULT 0,
    phone_number  VARCHAR(20)  NOT NULL,
    payload       TEXT         NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count   INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_notification_outbox PRIMARY KEY (id)
);

-- =============================================================================
-- DOMAIN INDEXES
-- =============================================================================

CREATE INDEX idx_healer_clinic_id              ON healer(clinic_id);
CREATE INDEX idx_patient_clinic_id             ON patient(clinic_id);
CREATE INDEX idx_healing_case_clinic_id        ON healing_case(clinic_id);
CREATE INDEX idx_healing_case_patient_id       ON healing_case(patient_id);
CREATE INDEX idx_healing_case_healer_id        ON healing_case(healer_id);
CREATE INDEX idx_healing_session_case_id       ON healing_session(healing_case_id);
CREATE INDEX idx_chakra_scan_session_id        ON chakra_scan(healing_session_id);
CREATE INDEX idx_protocol_entry_session_id     ON protocol_entry(healing_session_id);
CREATE INDEX idx_symptom_feedback_session_id   ON symptom_feedback(healing_session_id);
CREATE INDEX idx_qualitative_feedback_sess_id  ON qualitative_feedback(healing_session_id);
CREATE INDEX idx_notification_status_created   ON notification_outbox(status, created_at);
CREATE INDEX idx_audit_log_healer_id           ON audit_log(healer_id);
CREATE INDEX idx_audit_log_entity              ON audit_log(entity_type, entity_id);

-- =============================================================================
-- ENVERS AUDIT TABLES
-- One _aud table per @Audited entity.
-- All entity columns are nullable here — DELETE revisions carry null values.
-- BaseEntity fields (created_at … version) appear in every _aud table.
-- TenantEntity adds clinic_id.
-- =============================================================================

CREATE TABLE clinic_aud (
    id            UUID,
    rev           BIGINT      NOT NULL,
    revtype       SMALLINT,
    created_at    TIMESTAMPTZ,
    updated_at    TIMESTAMPTZ,
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    is_deleted    BOOLEAN,
    version       BIGINT,
    code          VARCHAR(20),
    name          VARCHAR(255),
    phone         VARCHAR(255),
    email         VARCHAR(255),
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city          VARCHAR(255),
    state         VARCHAR(255),
    zip           VARCHAR(10),
    active        BOOLEAN,
    CONSTRAINT pk_clinic_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_clinic_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

-- NOTE: auth_provider column added to healer_aud by V6.
CREATE TABLE healer_aud (
    id            UUID,
    rev           BIGINT      NOT NULL,
    revtype       SMALLINT,
    created_at    TIMESTAMPTZ,
    updated_at    TIMESTAMPTZ,
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    is_deleted    BOOLEAN,
    version       BIGINT,
    clinic_id     UUID,
    full_name     VARCHAR(255),
    email         VARCHAR(255),
    phone         VARCHAR(255),
    password_hash VARCHAR(255),
    role          VARCHAR(20),
    healing_level VARCHAR(20),
    active        BOOLEAN,
    CONSTRAINT pk_healer_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_healer_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

-- NOTE: healer_id column added to patient_aud by V3.
CREATE TABLE patient_aud (
    id                UUID,
    rev               BIGINT      NOT NULL,
    revtype           SMALLINT,
    created_at        TIMESTAMPTZ,
    updated_at        TIMESTAMPTZ,
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    is_deleted        BOOLEAN,
    version           BIGINT,
    clinic_id         UUID,
    first_name        VARCHAR(255),
    last_name         VARCHAR(255),
    dob               DATE,
    gender            VARCHAR(10),
    marital_status    VARCHAR(10),
    email             VARCHAR(255),
    contact_whatsapp  VARCHAR(255),
    occupation        VARCHAR(255),
    address_line1     VARCHAR(255),
    address_line2     VARCHAR(255),
    city              VARCHAR(255),
    state             VARCHAR(255),
    zip               VARCHAR(10),
    CONSTRAINT pk_patient_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_patient_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE patient_medical_history_aud (
    id                         UUID,
    rev                        BIGINT      NOT NULL,
    revtype                    SMALLINT,
    created_at                 TIMESTAMPTZ,
    updated_at                 TIMESTAMPTZ,
    created_by                 VARCHAR(255),
    updated_by                 VARCHAR(255),
    is_deleted                 BOOLEAN,
    version                    BIGINT,
    clinic_id                  UUID,
    patient_id                 UUID,
    general_health             VARCHAR(255),
    diet                       VARCHAR(255),
    exercise                   VARCHAR(255),
    exercise_frequency         VARCHAR(255),
    hypertension               BOOLEAN,
    gi_issues                  BOOLEAN,
    stress_depression_anxiety  BOOLEAN,
    pregnant                   BOOLEAN,
    cancer                     BOOLEAN,
    cancer_location            VARCHAR(255),
    cancer_details             VARCHAR(255),
    additional_notes           TEXT,
    CONSTRAINT pk_patient_medical_history_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_patient_medical_history_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE healing_case_aud (
    id                        UUID,
    rev                       BIGINT      NOT NULL,
    revtype                   SMALLINT,
    created_at                TIMESTAMPTZ,
    updated_at                TIMESTAMPTZ,
    created_by                VARCHAR(255),
    updated_by                VARCHAR(255),
    is_deleted                BOOLEAN,
    version                   BIGINT,
    clinic_id                 UUID,
    patient_id                UUID,
    healer_id                 UUID,
    medical_condition         TEXT,
    lab_findings              TEXT,
    complementary_treatments  BOOLEAN,
    complementary_details     TEXT,
    infectious_illness        BOOLEAN,
    infectious_details        TEXT,
    basic_book_pg             VARCHAR(255),
    advanced_book_pg          VARCHAR(255),
    psychotherapy_pg          VARCHAR(255),
    status                    VARCHAR(10),
    CONSTRAINT pk_healing_case_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_healing_case_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE declaration_aud (
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
    patient_consent     BOOLEAN,
    healer_declaration  BOOLEAN,
    signed_at           TIMESTAMPTZ,
    patient_signature   VARCHAR(255),
    healer_signature    VARCHAR(255),
    CONSTRAINT pk_declaration_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_declaration_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE case_medical_snapshot_aud (
    id                         UUID,
    rev                        BIGINT      NOT NULL,
    revtype                    SMALLINT,
    created_at                 TIMESTAMPTZ,
    updated_at                 TIMESTAMPTZ,
    created_by                 VARCHAR(255),
    updated_by                 VARCHAR(255),
    is_deleted                 BOOLEAN,
    version                    BIGINT,
    healing_case_id            UUID,
    general_health             VARCHAR(255),
    diet                       VARCHAR(255),
    exercise                   VARCHAR(255),
    exercise_frequency         VARCHAR(255),
    hypertension               BOOLEAN,
    gi_issues                  BOOLEAN,
    stress_depression_anxiety  BOOLEAN,
    pregnant                   BOOLEAN,
    cancer                     BOOLEAN,
    cancer_location            VARCHAR(255),
    cancer_details             VARCHAR(255),
    additional_notes           TEXT,
    CONSTRAINT pk_case_medical_snapshot_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_case_medical_snapshot_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE case_symptom_aud (
    id              UUID,
    rev             BIGINT      NOT NULL,
    revtype         SMALLINT,
    created_at      TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    is_deleted      BOOLEAN,
    version         BIGINT,
    healing_case_id UUID,
    symptom_name    VARCHAR(255),
    description     VARCHAR(255),
    severity        INTEGER,
    added_date      DATE,
    resolved_date   DATE,
    resolved        BOOLEAN,
    CONSTRAINT pk_case_symptom_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_case_symptom_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

-- NOTE: V5 drops frequency and adds morning_dose/afternoon_dose/night_dose to both
--       case_medication and case_medication_aud.
CREATE TABLE case_medication_aud (
    id               UUID,
    rev              BIGINT      NOT NULL,
    revtype          SMALLINT,
    created_at       TIMESTAMPTZ,
    updated_at       TIMESTAMPTZ,
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    is_deleted       BOOLEAN,
    version          BIGINT,
    healing_case_id  UUID,
    medication_name  VARCHAR(255),
    dosage           VARCHAR(255),
    frequency        VARCHAR(255),                  -- dropped by V5
    notes            VARCHAR(255),
    CONSTRAINT pk_case_medication_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_case_medication_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE healing_details_aud (
    id                UUID,
    rev               BIGINT      NOT NULL,
    revtype           SMALLINT,
    created_at        TIMESTAMPTZ,
    updated_at        TIMESTAMPTZ,
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    is_deleted        BOOLEAN,
    version           BIGINT,
    healing_case_id   UUID,
    protocol_name     VARCHAR(255),
    book_reference    VARCHAR(255),
    detailed_protocol TEXT,
    CONSTRAINT pk_healing_details_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_healing_details_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE case_closure_aud (
    id                    UUID,
    rev                   BIGINT      NOT NULL,
    revtype               SMALLINT,
    created_at            TIMESTAMPTZ,
    updated_at            TIMESTAMPTZ,
    created_by            VARCHAR(255),
    updated_by            VARCHAR(255),
    is_deleted            BOOLEAN,
    version               BIGINT,
    clinic_id             UUID,
    healing_case_id       UUID,
    closure_date          DATE,
    additional_comments   TEXT,
    healing_count         INTEGER,
    is_healing_continued  BOOLEAN,
    last_feedback_date    DATE,
    healer_signature      VARCHAR(255),
    closed_by             UUID,
    CONSTRAINT pk_case_closure_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_case_closure_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE healing_session_aud (
    id               UUID,
    rev              BIGINT      NOT NULL,
    revtype          SMALLINT,
    created_at       TIMESTAMPTZ,
    updated_at       TIMESTAMPTZ,
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    is_deleted       BOOLEAN,
    version          BIGINT,
    clinic_id        UUID,
    healing_case_id  UUID,
    session_number   INTEGER,
    session_date     DATE,
    start_time       TIME,
    end_time         TIME,
    status           VARCHAR(10),
    CONSTRAINT pk_healing_session_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_healing_session_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE chakra_scan_aud (
    id                  UUID,
    rev                 BIGINT      NOT NULL,
    revtype             SMALLINT,
    created_at          TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    is_deleted          BOOLEAN,
    version             BIGINT,
    clinic_id           UUID,
    healing_session_id  UUID,
    scan_type           VARCHAR(10),
    notes               TEXT,
    CONSTRAINT pk_chakra_scan_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_chakra_scan_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE chakra_reading_aud (
    id               UUID,
    rev              BIGINT      NOT NULL,
    revtype          SMALLINT,
    created_at       TIMESTAMPTZ,
    updated_at       TIMESTAMPTZ,
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    is_deleted       BOOLEAN,
    version          BIGINT,
    clinic_id        UUID,
    chakra_scan_id   UUID,
    chakra_id        UUID,
    chakra_name      VARCHAR(255),
    energy_level     VARCHAR(255),
    activation_level VARCHAR(255),
    notes            VARCHAR(255),
    CONSTRAINT pk_chakra_reading_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_chakra_reading_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE treatment_step_aud (
    id                  UUID,
    rev                 BIGINT      NOT NULL,
    revtype             SMALLINT,
    created_at          TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    is_deleted          BOOLEAN,
    version             BIGINT,
    healing_details_id  UUID,
    tag                 VARCHAR(255),
    sweep_type          VARCHAR(255),
    notes               VARCHAR(255),
    cycles              INTEGER,
    step                INTEGER,
    color               VARCHAR(255),
    CONSTRAINT pk_treatment_step_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_treatment_step_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

-- Envers audit table for the @ElementCollection treatment_step.targets
CREATE TABLE treatment_step_targets_aud (
    treatment_step_id  UUID,
    rev                BIGINT      NOT NULL,
    revtype            SMALLINT,
    targets            VARCHAR(255),
    CONSTRAINT pk_treatment_step_targets_aud     PRIMARY KEY (treatment_step_id, rev, targets),
    CONSTRAINT fk_treatment_step_targets_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE protocol_entry_aud (
    id                  UUID,
    rev                 BIGINT      NOT NULL,
    revtype             SMALLINT,
    created_at          TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    is_deleted          BOOLEAN,
    version             BIGINT,
    clinic_id           UUID,
    healing_session_id  UUID,
    chakra_id           UUID,
    target_area         VARCHAR(255),
    technique           VARCHAR(255),
    notes               VARCHAR(255),
    protocol_name       VARCHAR(255),
    reference_book      VARCHAR(255),
    detailed_protocol   TEXT,
    CONSTRAINT pk_protocol_entry_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_protocol_entry_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE protocol_color_aud (
    id                UUID,
    rev               BIGINT      NOT NULL,
    revtype           SMALLINT,
    created_at        TIMESTAMPTZ,
    updated_at        TIMESTAMPTZ,
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    is_deleted        BOOLEAN,
    version           BIGINT,
    clinic_id         UUID,
    protocol_entry_id UUID,
    color_id          UUID,
    usage_type        VARCHAR(15),
    CONSTRAINT pk_protocol_color_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_protocol_color_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE symptom_feedback_aud (
    id                  UUID,
    rev                 BIGINT      NOT NULL,
    revtype             SMALLINT,
    created_at          TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    is_deleted          BOOLEAN,
    version             BIGINT,
    clinic_id           UUID,
    healing_session_id  UUID,
    case_symptom_id     UUID,
    rating              INTEGER,
    notes               VARCHAR(255),
    CONSTRAINT pk_symptom_feedback_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_symptom_feedback_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE qualitative_feedback_aud (
    id                  UUID,
    rev                 BIGINT      NOT NULL,
    revtype             SMALLINT,
    created_at          TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    is_deleted          BOOLEAN,
    version             BIGINT,
    clinic_id           UUID,
    healing_session_id  UUID,
    feedback_type       VARCHAR(10),
    feedback_text       TEXT,
    patient_remarks     TEXT,
    CONSTRAINT pk_qualitative_feedback_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_qualitative_feedback_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE chakra_catalog_aud (
    id          UUID,
    rev         BIGINT      NOT NULL,
    revtype     SMALLINT,
    created_at  TIMESTAMPTZ,
    updated_at  TIMESTAMPTZ,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN,
    version     BIGINT,
    code        VARCHAR(30),
    name        VARCHAR(255),
    category    VARCHAR(255),
    location    VARCHAR(255),
    clinic_id   UUID,
    CONSTRAINT pk_chakra_catalog_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_chakra_catalog_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE prana_color_catalog_aud (
    id          UUID,
    rev         BIGINT      NOT NULL,
    revtype     SMALLINT,
    created_at  TIMESTAMPTZ,
    updated_at  TIMESTAMPTZ,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN,
    version     BIGINT,
    code        VARCHAR(30),
    name        VARCHAR(255),
    color_hex   VARCHAR(7),
    properties  TEXT,
    clinic_id   UUID,
    CONSTRAINT pk_prana_color_catalog_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_prana_color_catalog_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

-- pdf_job and notification_outbox extend BaseEntity (@Audited via MappedSuperclass)
-- so Envers creates audit tables for them as well.

CREATE TABLE pdf_job_aud (
    id                UUID,
    rev               BIGINT      NOT NULL,
    revtype           SMALLINT,
    created_at        TIMESTAMPTZ,
    updated_at        TIMESTAMPTZ,
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    is_deleted        BOOLEAN,
    version           BIGINT,
    job_id            VARCHAR(255),
    healing_case_id   UUID,
    healing_case_data TEXT,                         -- stored as text in audit
    retry_count       INTEGER,
    document_version  INTEGER,
    status            VARCHAR(255),
    file_url          VARCHAR(255),
    CONSTRAINT pk_pdf_job_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_pdf_job_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE notification_outbox_aud (
    id            UUID,
    rev           BIGINT      NOT NULL,
    revtype       SMALLINT,
    created_at    TIMESTAMPTZ,
    updated_at    TIMESTAMPTZ,
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    is_deleted    BOOLEAN,
    version       BIGINT,
    phone_number  VARCHAR(20),
    payload       TEXT,
    status        VARCHAR(20),
    retry_count   INTEGER,
    CONSTRAINT pk_notification_outbox_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_notification_outbox_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

-- audit_log extends BaseEntity (@Audited MappedSuperclass), but uses
-- @AuditOverride(forClass=BaseEntity.class, isAudited=false) to suppress
-- the inherited BaseEntity fields from the audit table.
-- Only AuditLog's own columns appear here.
CREATE TABLE audit_log_aud (
    id            UUID,
    rev           BIGINT      NOT NULL,
    revtype       SMALLINT,
    healer_id     UUID,
    clinic_id     UUID,
    action        VARCHAR(255),
    entity_type   VARCHAR(255),
    entity_id     UUID,
    payload       TEXT,
    ip_address    VARCHAR(255),
    performed_at  TIMESTAMPTZ,
    CONSTRAINT pk_audit_log_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_audit_log_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);
