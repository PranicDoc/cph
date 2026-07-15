-- =============================================================================
-- V17__add_healer_id_columns_and_backfill.sql
-- Phase 25 (clinic removal, wave 1): adds a nullable healer_id UUID column to
-- all 10 clinic-scoped base tables plus their 10 matching _aud (Envers) audit
-- tables, adds a per-base-table index, and backfills healer_id everywhere the
-- automated join chain can reach.
--
-- Backfill runs in strict FK-dependency order (healing_case is the root of
-- attribution; every other table joins to its own immediate parent, NOT to
-- healing_case directly, per 25-RESEARCH.md's verified FK graph):
--   1. healing_session  <- healing_case (+ created_by email-match fallback)
--   2. case_closure     <- healing_case                      [direct, parallel to step 1]
--   3. chakra_scan           <- healing_session               [1-hop]
--   4. protocol_entry        <- healing_session               [1-hop]
--   5. symptom_feedback      <- healing_session               [1-hop]
--   6. qualitative_feedback  <- healing_session               [1-hop]
--   7. chakra_reading        <- chakra_scan                   [2-hop, must follow step 3]
--   8. protocol_color        <- protocol_entry                [2-hop, must follow step 4]
--
-- NOT backfilled here: chakra_catalog, prana_color_catalog. Neither table has
-- any FK to healing_case/healing_session — they are global/clinic-custom
-- lookup tables with a nullable clinic_id. Their backfill (if any rows need
-- it) is a decision gated on a production audit, handled in plan 25-02's
-- V18 migration. Columns are added here for schema parity only.
--
-- _aud parity: V3 added healer_id to patient but forgot patient_aud, breaking
-- Envers on every patient write until V8 fixed it. This migration adds the
-- matching _aud column for all 10 tables in the SAME migration as the base
-- column to avoid repeating that regression. _aud columns use
-- ADD COLUMN IF NOT EXISTS, no FK, no index (historical snapshots only).
--
-- All columns are nullable in this phase (additive-first). NOT NULL
-- enforcement and the Hibernate/@Filter cutover are Phase 26's job.
--
-- Backfill DML is appended by a follow-up commit within this same plan (Task 2).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- DDL: base tables — nullable healer_id + named FK + index
-- -----------------------------------------------------------------------------

ALTER TABLE healing_session
    ADD COLUMN healer_id UUID,
    ADD CONSTRAINT fk_healing_session_healer FOREIGN KEY (healer_id) REFERENCES healer(id);

ALTER TABLE case_closure
    ADD COLUMN healer_id UUID,
    ADD CONSTRAINT fk_case_closure_healer FOREIGN KEY (healer_id) REFERENCES healer(id);

ALTER TABLE chakra_scan
    ADD COLUMN healer_id UUID,
    ADD CONSTRAINT fk_chakra_scan_healer FOREIGN KEY (healer_id) REFERENCES healer(id);

ALTER TABLE protocol_entry
    ADD COLUMN healer_id UUID,
    ADD CONSTRAINT fk_protocol_entry_healer FOREIGN KEY (healer_id) REFERENCES healer(id);

ALTER TABLE symptom_feedback
    ADD COLUMN healer_id UUID,
    ADD CONSTRAINT fk_symptom_feedback_healer FOREIGN KEY (healer_id) REFERENCES healer(id);

ALTER TABLE qualitative_feedback
    ADD COLUMN healer_id UUID,
    ADD CONSTRAINT fk_qualitative_feedback_healer FOREIGN KEY (healer_id) REFERENCES healer(id);

ALTER TABLE chakra_reading
    ADD COLUMN healer_id UUID,
    ADD CONSTRAINT fk_chakra_reading_healer FOREIGN KEY (healer_id) REFERENCES healer(id);

ALTER TABLE protocol_color
    ADD COLUMN healer_id UUID,
    ADD CONSTRAINT fk_protocol_color_healer FOREIGN KEY (healer_id) REFERENCES healer(id);

ALTER TABLE chakra_catalog
    ADD COLUMN healer_id UUID,
    ADD CONSTRAINT fk_chakra_catalog_healer FOREIGN KEY (healer_id) REFERENCES healer(id);

ALTER TABLE prana_color_catalog
    ADD COLUMN healer_id UUID,
    ADD CONSTRAINT fk_prana_color_catalog_healer FOREIGN KEY (healer_id) REFERENCES healer(id);

CREATE INDEX idx_healing_session_healer_id ON healing_session(healer_id);
CREATE INDEX idx_case_closure_healer_id ON case_closure(healer_id);
CREATE INDEX idx_chakra_scan_healer_id ON chakra_scan(healer_id);
CREATE INDEX idx_protocol_entry_healer_id ON protocol_entry(healer_id);
CREATE INDEX idx_symptom_feedback_healer_id ON symptom_feedback(healer_id);
CREATE INDEX idx_qualitative_feedback_healer_id ON qualitative_feedback(healer_id);
CREATE INDEX idx_chakra_reading_healer_id ON chakra_reading(healer_id);
CREATE INDEX idx_protocol_color_healer_id ON protocol_color(healer_id);
CREATE INDEX idx_chakra_catalog_healer_id ON chakra_catalog(healer_id);
CREATE INDEX idx_prana_color_catalog_healer_id ON prana_color_catalog(healer_id);

-- -----------------------------------------------------------------------------
-- DDL: _aud (Envers) counterpart tables — column only, no FK, no index
-- -----------------------------------------------------------------------------

ALTER TABLE healing_session_aud       ADD COLUMN IF NOT EXISTS healer_id UUID;
ALTER TABLE case_closure_aud          ADD COLUMN IF NOT EXISTS healer_id UUID;
ALTER TABLE chakra_scan_aud           ADD COLUMN IF NOT EXISTS healer_id UUID;
ALTER TABLE protocol_entry_aud        ADD COLUMN IF NOT EXISTS healer_id UUID;
ALTER TABLE symptom_feedback_aud      ADD COLUMN IF NOT EXISTS healer_id UUID;
ALTER TABLE qualitative_feedback_aud  ADD COLUMN IF NOT EXISTS healer_id UUID;
ALTER TABLE chakra_reading_aud        ADD COLUMN IF NOT EXISTS healer_id UUID;
ALTER TABLE protocol_color_aud        ADD COLUMN IF NOT EXISTS healer_id UUID;
ALTER TABLE chakra_catalog_aud        ADD COLUMN IF NOT EXISTS healer_id UUID;
ALTER TABLE prana_color_catalog_aud   ADD COLUMN IF NOT EXISTS healer_id UUID;
