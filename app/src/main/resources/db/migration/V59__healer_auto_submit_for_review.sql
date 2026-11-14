-- =============================================================================
-- V59__healer_auto_submit_for_review.sql
-- Case review: a healer can opt in to having every newly generated case PDF sent
-- to their instructor automatically. Off by default.
-- =============================================================================

ALTER TABLE healer ADD COLUMN auto_submit_for_review BOOLEAN NOT NULL DEFAULT FALSE;

-- Envers shadow table (Healer is @Audited); nullable like every other _aud column.
ALTER TABLE healer_aud ADD COLUMN auto_submit_for_review BOOLEAN;
