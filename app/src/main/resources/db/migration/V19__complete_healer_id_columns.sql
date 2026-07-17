-- =============================================================================
-- V19__complete_healer_id_columns.sql
-- Phase 26 (clinic removal, application-layer cutover): completes the two
-- healer_id-column gaps 26-RESEARCH.md found (Key Finding 6) that
-- V17__add_healer_id_columns_and_backfill.sql did not cover:
--   1. patient_medical_history  — TenantEntity subclass, clinic_id NOT NULL,
--      had NO healer_id column before this migration.
--   2. protocol_color_mapping   — catalog table, clinic_id NULLABLE, also
--      missed by V17.
--
-- This migration is ADDITIVE ONLY: no drops, no NOT NULL tightening. All
-- clinic_id drops (base tables, _aud tables, and the clinic/clinic_aud
-- tables themselves) are deferred to a later migration (26-12), following
-- the additive-first staged-constraint pattern established by V17 (25-01)
-- and Phases 7/25.
--
-- _aud parity: both base tables' _aud counterparts get healer_id in THIS
-- SAME migration via ADD COLUMN IF NOT EXISTS — the exact discipline
-- V8__fix_patient_aud_healer_id.sql exists to enforce (V3 added healer_id
-- to patient but forgot patient_aud, breaking Envers writes until V8).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- DDL: patient_medical_history — nullable healer_id + named FK + index
-- -----------------------------------------------------------------------------

ALTER TABLE patient_medical_history
    ADD COLUMN healer_id UUID,
    ADD CONSTRAINT fk_patient_medical_history_healer FOREIGN KEY (healer_id) REFERENCES healer(id);

CREATE INDEX idx_patient_medical_history_healer_id ON patient_medical_history(healer_id);

ALTER TABLE patient_medical_history_aud ADD COLUMN IF NOT EXISTS healer_id UUID;

-- -----------------------------------------------------------------------------
-- DDL: protocol_color_mapping — nullable healer_id (catalog pattern, mirrors
-- its nullable clinic_id) + named FK + new parallel healer-keyed UNIQUE.
-- The existing clinic_id-keyed UNIQUE constraint is left untouched here — it
-- coexists with the new one through the transition and is dropped in 26-12.
-- -----------------------------------------------------------------------------

ALTER TABLE protocol_color_mapping
    ADD COLUMN healer_id UUID,
    ADD CONSTRAINT fk_protocol_color_mapping_healer FOREIGN KEY (healer_id) REFERENCES healer(id);

ALTER TABLE protocol_color_mapping
    ADD CONSTRAINT uq_pcm_protocol_color_usage_healer UNIQUE (protocol_name, color_id, usage_type, healer_id);

ALTER TABLE protocol_color_mapping_aud ADD COLUMN IF NOT EXISTS healer_id UUID;

-- -----------------------------------------------------------------------------
-- Backfill: patient_medical_history.healer_id <- patient.healer_id (single-hop
-- join via patient_id; patient.healer_id was already backfilled by V3).
-- Idempotency-guarded so this UPDATE is safely re-runnable.
-- -----------------------------------------------------------------------------

-- Join hop: patient_medical_history.patient_id -> patient.id
UPDATE patient_medical_history pmh
    SET healer_id = p.healer_id
    FROM patient p
    WHERE pmh.patient_id = p.id
      AND pmh.healer_id IS NULL;

-- -----------------------------------------------------------------------------
-- protocol_color_mapping receives NO backfill. It follows the nullable
-- system-vs-custom catalog pattern (NULL healer_id = system row) exactly as
-- chakra_catalog/prana_color_catalog did in 25-02's recorded "catalog = none"
-- decision — the column exists here for _aud/UNIQUE parity and future custom
-- rows, not for a data migration. healer_id remains NULL on this table after
-- this migration — expected.
-- -----------------------------------------------------------------------------
