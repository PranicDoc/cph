-- =============================================================================
-- V7__add_enum_check_constraints.sql
-- Adds CHECK constraints for every @Enumerated(STRING) column so the DB
-- rejects values that the Java enums don't know about.
--
-- Convention: ck_{table}_{column}
-- Nullable columns use IS NULL OR ... so NULL inserts are still allowed.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- healer
-- Role         : HEALER, SENIOR_HEALER, RECEPTIONIST, ADMIN, SUPADM
-- HealingLevel : BASIC, ADVANCED, PSYCHOTHERAPY
-- AuthProvider : LOCAL, GOOGLE   (column added by V6)
-- ---------------------------------------------------------------------------
ALTER TABLE healer
    ADD CONSTRAINT ck_healer_role
        CHECK (role IN ('HEALER','SENIOR_HEALER','RECEPTIONIST','ADMIN','SUPADM')),
    ADD CONSTRAINT ck_healer_healing_level
        CHECK (healing_level IS NULL OR healing_level IN ('BASIC','ADVANCED','PSYCHOTHERAPY')),
    ADD CONSTRAINT ck_healer_auth_provider
        CHECK (auth_provider IN ('LOCAL','GOOGLE'));

ALTER TABLE healer_aud
    ADD CONSTRAINT ck_healer_aud_role
        CHECK (role IS NULL OR role IN ('HEALER','SENIOR_HEALER','RECEPTIONIST','ADMIN','SUPADM')),
    ADD CONSTRAINT ck_healer_aud_healing_level
        CHECK (healing_level IS NULL OR healing_level IN ('BASIC','ADVANCED','PSYCHOTHERAPY')),
    ADD CONSTRAINT ck_healer_aud_auth_provider
        CHECK (auth_provider IS NULL OR auth_provider IN ('LOCAL','GOOGLE'));

-- ---------------------------------------------------------------------------
-- patient
-- Gender        : M, F, OTHER
-- MaritalStatus : SINGLE, MARRIED, OTHERS
-- ---------------------------------------------------------------------------
ALTER TABLE patient
    ADD CONSTRAINT ck_patient_gender
        CHECK (gender IS NULL OR gender IN ('M','F','OTHER')),
    ADD CONSTRAINT ck_patient_marital_status
        CHECK (marital_status IS NULL OR marital_status IN ('SINGLE','MARRIED','OTHERS'));

ALTER TABLE patient_aud
    ADD CONSTRAINT ck_patient_aud_gender
        CHECK (gender IS NULL OR gender IN ('M','F','OTHER')),
    ADD CONSTRAINT ck_patient_aud_marital_status
        CHECK (marital_status IS NULL OR marital_status IN ('SINGLE','MARRIED','OTHERS'));

-- ---------------------------------------------------------------------------
-- registration_link
-- Status: ACTIVE, IN_PROGRESS, USED
-- ---------------------------------------------------------------------------
ALTER TABLE registration_link
    ADD CONSTRAINT ck_registration_link_status
        CHECK (status IS NULL OR status IN ('ACTIVE','IN_PROGRESS','USED'));

-- ---------------------------------------------------------------------------
-- healing_case
-- CaseStatus: DRAFT, ACTIVE, CLOSED
-- ---------------------------------------------------------------------------
ALTER TABLE healing_case
    ADD CONSTRAINT ck_healing_case_status
        CHECK (status IN ('DRAFT','ACTIVE','CLOSED'));

ALTER TABLE healing_case_aud
    ADD CONSTRAINT ck_healing_case_aud_status
        CHECK (status IS NULL OR status IN ('DRAFT','ACTIVE','CLOSED'));

-- ---------------------------------------------------------------------------
-- healing_session
-- SessionStatus: DRAFT, COMPLETED
-- ---------------------------------------------------------------------------
ALTER TABLE healing_session
    ADD CONSTRAINT ck_healing_session_status
        CHECK (status IN ('DRAFT','COMPLETED'));

ALTER TABLE healing_session_aud
    ADD CONSTRAINT ck_healing_session_aud_status
        CHECK (status IS NULL OR status IN ('DRAFT','COMPLETED'));

-- ---------------------------------------------------------------------------
-- chakra_scan
-- ScanType: PRE, POST
-- ---------------------------------------------------------------------------
ALTER TABLE chakra_scan
    ADD CONSTRAINT ck_chakra_scan_scan_type
        CHECK (scan_type IN ('PRE','POST'));

ALTER TABLE chakra_scan_aud
    ADD CONSTRAINT ck_chakra_scan_aud_scan_type
        CHECK (scan_type IS NULL OR scan_type IN ('PRE','POST'));

-- ---------------------------------------------------------------------------
-- protocol_color
-- ColorUsageType: CLEANSING, ENERGISING
-- ---------------------------------------------------------------------------
ALTER TABLE protocol_color
    ADD CONSTRAINT ck_protocol_color_usage_type
        CHECK (usage_type IN ('CLEANSING','ENERGISING'));

ALTER TABLE protocol_color_aud
    ADD CONSTRAINT ck_protocol_color_aud_usage_type
        CHECK (usage_type IS NULL OR usage_type IN ('CLEANSING','ENERGISING'));

-- ---------------------------------------------------------------------------
-- protocol_color_mapping  (created by V4)
-- ColorUsageType: CLEANSING, ENERGISING
-- ---------------------------------------------------------------------------
ALTER TABLE protocol_color_mapping
    ADD CONSTRAINT ck_protocol_color_mapping_usage_type
        CHECK (usage_type IN ('CLEANSING','ENERGISING'));

ALTER TABLE protocol_color_mapping_aud
    ADD CONSTRAINT ck_protocol_color_mapping_aud_usage_type
        CHECK (usage_type IS NULL OR usage_type IN ('CLEANSING','ENERGISING'));

-- ---------------------------------------------------------------------------
-- qualitative_feedback
-- FeedbackType: INTERIM, FINAL
-- ---------------------------------------------------------------------------
ALTER TABLE qualitative_feedback
    ADD CONSTRAINT ck_qualitative_feedback_feedback_type
        CHECK (feedback_type IN ('INTERIM','FINAL'));

ALTER TABLE qualitative_feedback_aud
    ADD CONSTRAINT ck_qualitative_feedback_aud_feedback_type
        CHECK (feedback_type IS NULL OR feedback_type IN ('INTERIM','FINAL'));

-- ---------------------------------------------------------------------------
-- pdf_job
-- JobStatus: DRAFT, PROCESSING, STARTED, COMPLETED, FAILED
-- ---------------------------------------------------------------------------
ALTER TABLE pdf_job
    ADD CONSTRAINT ck_pdf_job_status
        CHECK (status IS NULL OR status IN ('DRAFT','PROCESSING','STARTED','COMPLETED','FAILED'));

ALTER TABLE pdf_job_aud
    ADD CONSTRAINT ck_pdf_job_aud_status
        CHECK (status IS NULL OR status IN ('DRAFT','PROCESSING','STARTED','COMPLETED','FAILED'));

-- ---------------------------------------------------------------------------
-- notification_outbox
-- NotificationStatus: PENDING, PROCESSING, PROCESSED, FAILED, DLQ
-- ---------------------------------------------------------------------------
ALTER TABLE notification_outbox
    ADD CONSTRAINT ck_notification_outbox_status
        CHECK (status IN ('PENDING','PROCESSING','PROCESSED','FAILED','DLQ'));

ALTER TABLE notification_outbox_aud
    ADD CONSTRAINT ck_notification_outbox_aud_status
        CHECK (status IS NULL OR status IN ('PENDING','PROCESSING','PROCESSED','FAILED','DLQ'));

-- ---------------------------------------------------------------------------
-- feedback_link  (created by V2)
-- LinkStatus: ACTIVE, USED
-- ---------------------------------------------------------------------------
ALTER TABLE feedback_link
    ADD CONSTRAINT ck_feedback_link_status
        CHECK (status IN ('ACTIVE','USED'));
