-- =============================================================================
-- V8__fix_patient_aud_healer_id.sql
-- V3 added healer_id to patient but omitted the matching patient_aud column.
-- Envers fails on every patient INSERT/UPDATE until this column exists.
-- IF NOT EXISTS makes this safe for future clean installs (V0→V8 in order).
-- =============================================================================

ALTER TABLE patient_aud
    ADD COLUMN IF NOT EXISTS healer_id UUID;
