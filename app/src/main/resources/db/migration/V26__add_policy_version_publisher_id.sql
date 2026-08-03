-- =============================================================================
-- V26__add_policy_version_publisher_id.sql
-- Phase 31 (Mandatory Legal Documents) — adds publisher attribution to
-- policy_version.
--
-- D-04: denormalized publisher attribution — records WHO published a given
--       policy version, directly on the row, rather than requiring a join
--       through the AuditLog trail for every read.
-- D-05: this column complements the AuditLog audit trail (AUDIT-01/02); it
--       does NOT replace it. The AuditLog "PUBLISH_POLICY_VERSION" entry
--       remains the authoritative event record.
-- D-06: publisher_id is denormalized display/audit metadata ONLY. It does
--       NOT scope which practitioners are permitted to see a given policy
--       version — visibility of active policy versions is global to all
--       authenticated healers, unaffected by this column.
--
-- Both policy_version and its Envers shadow table policy_version_aud must be
-- altered together: PolicyVersion is @Audited, so Hibernate Envers writes a
-- revision row into policy_version_aud on every insert/update. If the shadow
-- table lacked publisher_id, the very first publish call would fail with a
-- SQLGrammarException referencing policy_version_aud.
--
-- No FK to healer(id) — matches V25's own consent.accepted_by precedent,
-- a plain UUID column with no REFERENCES clause. No index — this column is
-- display/audit metadata, never a query predicate in this phase (D-06 defers
-- per-publisher scoping entirely).
-- =============================================================================

ALTER TABLE policy_version ADD COLUMN publisher_id UUID;
ALTER TABLE policy_version_aud ADD COLUMN publisher_id UUID;
