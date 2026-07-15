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

-- -----------------------------------------------------------------------------
-- Backfill: dependency-ordered UPDATEs. Every UPDATE joins the table's actual
-- immediate-parent FK column (per 25-RESEARCH.md's verified FK graph) and is
-- guarded with "AND <alias>.healer_id IS NULL" for idempotency/re-runnability.
-- -----------------------------------------------------------------------------

-- Step 1: healing_session <- healing_case (direct FK: healing_case_id)
UPDATE healing_session s
    SET healer_id = hc.healer_id
    FROM healing_case hc
    WHERE s.healing_case_id = hc.id
      AND s.healer_id IS NULL;

-- Step 1b (SC-3 fallback): healing_session <- created_by email match, mirrors
-- V3__add_patient_healer_id.sql verbatim. Runs after step 1 so it only fills
-- rows the case-join left NULL (safety net; step 1 should cover ~100% since
-- healing_case_id and healing_case.healer_id are both NOT NULL by schema).
UPDATE healing_session s
    SET healer_id = h.id
    FROM healer h
    WHERE LOWER(s.created_by) = LOWER(h.email)
      AND s.healer_id IS NULL;

-- Step 2: case_closure <- healing_case (direct FK: healing_case_id; independent of step 1)
UPDATE case_closure cc
    SET healer_id = hc.healer_id
    FROM healing_case hc
    WHERE cc.healing_case_id = hc.id
      AND cc.healer_id IS NULL;

-- Step 3: chakra_scan <- healing_session (1-hop, FK: healing_session_id; requires step 1 done)
UPDATE chakra_scan cs
    SET healer_id = hs.healer_id
    FROM healing_session hs
    WHERE cs.healing_session_id = hs.id
      AND cs.healer_id IS NULL;

-- Step 4: protocol_entry <- healing_session (1-hop, FK: healing_session_id; requires step 1 done)
UPDATE protocol_entry pe
    SET healer_id = hs.healer_id
    FROM healing_session hs
    WHERE pe.healing_session_id = hs.id
      AND pe.healer_id IS NULL;

-- Step 5: symptom_feedback <- healing_session (1-hop, FK: healing_session_id; requires step 1 done)
UPDATE symptom_feedback sf
    SET healer_id = hs.healer_id
    FROM healing_session hs
    WHERE sf.healing_session_id = hs.id
      AND sf.healer_id IS NULL;

-- Step 6: qualitative_feedback <- healing_session (1-hop, FK: healing_session_id; requires step 1 done)
UPDATE qualitative_feedback qf
    SET healer_id = hs.healer_id
    FROM healing_session hs
    WHERE qf.healing_session_id = hs.id
      AND qf.healer_id IS NULL;

-- Step 7: chakra_reading <- chakra_scan (2-hop, FK: chakra_scan_id; requires step 3 done)
UPDATE chakra_reading cr
    SET healer_id = cs.healer_id
    FROM chakra_scan cs
    WHERE cr.chakra_scan_id = cs.id
      AND cr.healer_id IS NULL;

-- Step 8: protocol_color <- protocol_entry (2-hop, FK: protocol_entry_id; requires step 4 done)
UPDATE protocol_color pc
    SET healer_id = pe.healer_id
    FROM protocol_entry pe
    WHERE pc.protocol_entry_id = pe.id
      AND pc.healer_id IS NULL;

-- -----------------------------------------------------------------------------
-- Deferred: chakra_catalog and prana_color_catalog have no case/session FK to
-- join through (clinic_id is nullable, NULL = system-level, non-null =
-- clinic-custom). Their backfill is gated on a production audit of whether
-- any clinic-custom rows exist and, if so, whether any belong to a
-- multi-healer clinic. This is handled in plan 25-02's V18 migration.
-- healer_id remains NULL on these two tables after this migration — expected.
-- -----------------------------------------------------------------------------
