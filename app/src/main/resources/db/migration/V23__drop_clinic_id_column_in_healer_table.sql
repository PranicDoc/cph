-- =============================================================================
-- V23__drop_clinic_id_column_in_healer_table.sql
-- healer.clinic_id (base table column) remains — RETAINED for the Phase-27
-- credit boundary (see V21's banner). It is now an orphaned UUID column with
-- no FK target, since the clinic table it once conceptually pointed to no
-- longer exists.
-- =============================================================================

ALTER TABLE healer                         DROP COLUMN IF EXISTS clinic_id;
