-- =============================================================================
-- V22__drop_clinic_tables.sql
-- Phase 26 (clinic removal, application-layer cutover) plan 12: D-26-01's
-- final step. Drops the clinic and clinic_aud tables themselves, completing
-- the table teardown. V21 already removed the only FK constraint anywhere in
-- the schema that referenced clinic(id) (healer_invite_links.clinic_id), so
-- there is no ordering dependency between dropping clinic and clinic_aud —
-- both are dropped here, in either order.
--
-- healer.clinic_id (base table column) remains — RETAINED for the Phase-27
-- credit boundary (see V21's banner). It is now an orphaned UUID column with
-- no FK target, since the clinic table it once conceptually pointed to no
-- longer exists.
-- =============================================================================

DROP TABLE IF EXISTS clinic_aud;
DROP TABLE IF EXISTS clinic;
