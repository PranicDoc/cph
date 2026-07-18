-- =============================================================================
-- V21__drop_clinic_id_columns_and_fks.sql
-- Phase 26 (clinic removal, application-layer cutover) plan 12: D-26-01's DROP
-- half. This is the codebase's FIRST DROP migration (V0-V20 are all additive).
--
-- Drops every clinic_id FK + column EXCEPT healer.clinic_id (base table only,
-- NOT healer_aud), which is intentionally RETAINED for the Phase-27 credit
-- boundary — 26-07's creditService deduct contract still reads
-- Healer.getClinicId() (finding #8 / A1) until Phase 27 renegotiates the
-- cross-service contract and reconciles the owner model. Drop only its FK to
-- clinic (there is none in this schema — see STEP 1 note below); the column
-- itself stays as an orphaned UUID on the base `healer` table.
--
-- PRECONDITION (Task 1 of 26-12-PLAN.md, re-verified this session): a full
-- grep of cph/app/src/main/java confirms NO Java reads or writes clinic_id
-- anywhere EXCEPT Healer.clinicId (the retained bridge) and the 26-07
-- CROSS-SERVICE BOUNDARY (Phase 27)-marked payments cluster. The full suite
-- is at the 120-run/32-failed baseline (CUTOVER-07).
--
-- SESSION FOLLOW-UP (not in 26-RESEARCH.md's original table inventory):
-- AuditLog.clinicId / audit_log.clinic_id / audit_log_aud.clinic_id was
-- discovered mid-phase as a live JPA-mapped field with zero writers (26-08
-- removed the only write call, AuditAspect.setClinicId(...)) and zero
-- readers anywhere in app/src/main or app/src/test. It carried no
-- CROSS-SERVICE BOUNDARY marker and was not the documented Healer.clinicId
-- bridge, so it fell through the cracks between 26-08 and this plan's own
-- drop-order inventory (26-RESEARCH.md never listed audit_log/audit_log_aud).
-- Confirmed user-approved: dropped here (STEP 3B), same DROP COLUMN pattern
-- as every other table in this migration, alongside the Java-layer field/
-- @Column removal from AuditLog.java (companion commit, same session).
--
-- STEP 1 GREP-VERIFIED DEVIATION FROM 26-RESEARCH.md's ASSUMPTION: exhaustive
-- grep of V0__baseline_schema.sql and every later migration (V1-V20) for
-- `FOREIGN KEY (clinic_id)`, `REFERENCES clinic(`, and `fk_.*clinic` found
-- that NONE of the 13 base tables (healer, patient, patient_medical_history,
-- healing_case, case_closure, healing_session, chakra_scan, chakra_reading,
-- protocol_entry, protocol_color, symptom_feedback, qualitative_feedback,
-- healer_protocol_understanding) nor feedback_link ever carried a named (or
-- unnamed) FK constraint on clinic_id — those columns are plain
-- `UUID NOT NULL` with no REFERENCES clause; clinic scoping for them was
-- enforced at the Hibernate/application layer only, never at the DB layer.
-- The ONLY real FK constraint referencing clinic(id) anywhere in the schema
-- is `healer_invite_links.clinic_id UUID NOT NULL REFERENCES clinic(id)`
-- (V13, unnamed inline FK — Postgres auto-generates its name). STEP 1 below
-- therefore drops exactly one FK, found by dynamic lookup rather than a
-- guessed name, per this task's "do NOT guess names" instruction.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- STEP 1: Drop the FK referencing clinic(id) (healer_invite_links.clinic_id —
-- the only such FK in the schema; looked up dynamically, name not guessed).
-- -----------------------------------------------------------------------------

DO $$
DECLARE
    v_constraint_name text;
BEGIN
    SELECT tc.constraint_name INTO v_constraint_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
        ON tc.constraint_name = kcu.constraint_name
       AND tc.table_schema = kcu.table_schema
    JOIN information_schema.constraint_column_usage ccu
        ON tc.constraint_name = ccu.constraint_name
       AND tc.table_schema = ccu.table_schema
    WHERE tc.table_schema = current_schema()
      AND tc.table_name = 'healer_invite_links'
      AND tc.constraint_type = 'FOREIGN KEY'
      AND kcu.column_name = 'clinic_id'
      AND ccu.table_name = 'clinic';

    IF v_constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE healer_invite_links DROP CONSTRAINT %I', v_constraint_name);
    END IF;
END $$;

-- -----------------------------------------------------------------------------
-- STEP 2: Drop clinic_id from the 12 base tables that are TenantEntity
-- subclasses EXCEPT healer, plus their _aud counterparts (DROP COLUMN
-- IF EXISTS on _aud, matching this phase's established _aud-parity
-- discipline). healer.clinic_id (base) is NOT touched here — see banner.
-- -----------------------------------------------------------------------------

ALTER TABLE patient                            DROP COLUMN clinic_id;
ALTER TABLE patient_aud                        DROP COLUMN IF EXISTS clinic_id;

ALTER TABLE patient_medical_history            DROP COLUMN clinic_id;
ALTER TABLE patient_medical_history_aud        DROP COLUMN IF EXISTS clinic_id;

ALTER TABLE healing_case                       DROP COLUMN clinic_id;
ALTER TABLE healing_case_aud                   DROP COLUMN IF EXISTS clinic_id;

ALTER TABLE case_closure                       DROP COLUMN clinic_id;
ALTER TABLE case_closure_aud                   DROP COLUMN IF EXISTS clinic_id;

ALTER TABLE healing_session                    DROP COLUMN clinic_id;
ALTER TABLE healing_session_aud                DROP COLUMN IF EXISTS clinic_id;

ALTER TABLE chakra_scan                        DROP COLUMN clinic_id;
ALTER TABLE chakra_scan_aud                    DROP COLUMN IF EXISTS clinic_id;

ALTER TABLE chakra_reading                     DROP COLUMN clinic_id;
ALTER TABLE chakra_reading_aud                 DROP COLUMN IF EXISTS clinic_id;

ALTER TABLE protocol_entry                     DROP COLUMN clinic_id;
ALTER TABLE protocol_entry_aud                 DROP COLUMN IF EXISTS clinic_id;

ALTER TABLE protocol_color                     DROP COLUMN clinic_id;
ALTER TABLE protocol_color_aud                 DROP COLUMN IF EXISTS clinic_id;

ALTER TABLE symptom_feedback                   DROP COLUMN clinic_id;
ALTER TABLE symptom_feedback_aud               DROP COLUMN IF EXISTS clinic_id;

ALTER TABLE qualitative_feedback               DROP COLUMN clinic_id;
ALTER TABLE qualitative_feedback_aud           DROP COLUMN IF EXISTS clinic_id;

ALTER TABLE healer_protocol_understanding      DROP COLUMN clinic_id;
ALTER TABLE healer_protocol_understanding_aud  DROP COLUMN IF EXISTS clinic_id;

-- healer.clinic_id (BASE TABLE) IS INTENTIONALLY RETAINED — DO NOT DROP.
-- healer_aud.clinic_id, however, has no live read path (Envers history only,
-- never queried by the credit boundary or anything else) and is dropped for
-- consistency with every other _aud table's clinic_id removal.
ALTER TABLE healer_aud                         DROP COLUMN IF EXISTS clinic_id;

-- -----------------------------------------------------------------------------
-- STEP 3: Drop clinic_id from the nullable catalog / standalone tables, plus
-- protocol_color_mapping's old clinic_id-keyed UNIQUE constraint (superseded
-- by V19's uq_pcm_protocol_color_usage_healer). The old UNIQUE is unnamed in
-- its origin DDL (V4), so its actual (Postgres auto-generated) name is looked
-- up dynamically rather than guessed.
-- -----------------------------------------------------------------------------

DO $$
DECLARE
    v_constraint_name text;
BEGIN
    SELECT tc.constraint_name INTO v_constraint_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
        ON tc.constraint_name = kcu.constraint_name
       AND tc.table_schema = kcu.table_schema
    WHERE tc.table_schema = current_schema()
      AND tc.table_name = 'protocol_color_mapping'
      AND tc.constraint_type = 'UNIQUE'
      AND kcu.column_name = 'clinic_id';

    IF v_constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE protocol_color_mapping DROP CONSTRAINT %I', v_constraint_name);
    END IF;
END $$;

ALTER TABLE chakra_catalog                     DROP COLUMN clinic_id;
ALTER TABLE chakra_catalog_aud                 DROP COLUMN IF EXISTS clinic_id;

ALTER TABLE prana_color_catalog                DROP COLUMN clinic_id;
ALTER TABLE prana_color_catalog_aud            DROP COLUMN IF EXISTS clinic_id;

ALTER TABLE protocol_color_mapping             DROP COLUMN clinic_id;
ALTER TABLE protocol_color_mapping_aud         DROP COLUMN IF EXISTS clinic_id;

-- feedback_link: standalone entity, no _aud counterpart (V20's own comment:
-- "not @Audited ... no feedback_link_aud table exists").
ALTER TABLE feedback_link                      DROP COLUMN clinic_id;

-- healer_invite_links: standalone entity, no _aud counterpart (V13's own
-- comment: "deliberately NOT modeled on BaseEntity/TenantEntity ... no Envers
-- audit-history table is created here on purpose"). Its FK to clinic(id) was
-- already dropped in STEP 1 above.
ALTER TABLE healer_invite_links                DROP COLUMN clinic_id;

-- -----------------------------------------------------------------------------
-- STEP 3B (SESSION FOLLOW-UP — see banner): audit_log / audit_log_aud
-- clinic_id. No FK exists on this column (grep-confirmed against
-- V0__baseline_schema.sql — a plain `UUID` column, same as every other
-- non-FK clinic_id column above), so a plain DROP COLUMN suffices.
-- -----------------------------------------------------------------------------

ALTER TABLE audit_log                          DROP COLUMN clinic_id;
ALTER TABLE audit_log_aud                      DROP COLUMN IF EXISTS clinic_id;
