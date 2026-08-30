-- =============================================================================
-- V35__shrink_role_enum_check_constraints.sql
-- Phase 41 (MIGRATE-01 / MIGRATE-05) — Static Role Enum Retirement
--
-- CONSTRAINT SWAP ONLY. This migration reads no row and writes no row (D-04):
-- it drops and re-adds CHECK constraints and does nothing else, so a reviewer
-- can see at a glance that no data changes and that rollback is a pure inverse
-- constraint change.
--
-- Context: in this same phase the code-side enum
-- com.pranicdoc.cph.common.enums.Role drops its three non-surviving values and
-- is reduced to exactly HEALER and SUPADM. V35 realigns the database backstop
-- (the CHECK constraints originally added by V7) to that two-value enum, so a
-- buggy @Enumerated(STRING) write, a direct SQL insert, or a tampered client
-- cannot land a retired role value.
--
-- D-03 PRE-FLIGHT — operator-run, read-only, recorded verbatim in the phase
-- verification evidence. Expected result: 0 for BOTH scans below. A non-zero
-- count is a HARD STOP (D-04): this migration must NOT ship, and a data-cleanup
-- follow-up plan is required first — because ADD CONSTRAINT validates every
-- existing row immediately and Postgres aborts with SQLSTATE 23514 if any live
-- row violates the new CHECK.
--   SELECT count(*) FROM healer WHERE role IN ('ADMIN','SENIOR_HEALER','RECEPTIONIST');
--
-- SECOND SCAN — the invite table (added post-close as CR-01 remediation):
--   SELECT count(*) FROM healer_invite_links
--    WHERE role IN ('ADMIN','SENIOR_HEALER','RECEPTIONIST');
-- healer_invite_links (created by V13, which runs AFTER V7) has NO role CHECK
-- constraint, so V35 correctly does not touch it. But healer_invite_links.role
-- is @Enumerated(STRING): a stranded retired-tier row throws on Hibernate
-- hydration once the enum shrinks, which 500s GET /api/admin/invites and
-- acceptInvite. V35 must be (re-)gated on BOTH scans returning 0 immediately
-- before apply — this is a documented gate, NOT an in-migration
-- ADD CONSTRAINT ... NOT VALID two-step (that would conflict with PD-41-04 and
-- the D-04 hard-stop design). V36 cleans up any healer_invite_links hits
-- idempotently, so on an already-audited environment the extended preflight is
-- purely defensive.
--
-- OUTAGE CONSEQUENCE OF A MIS-SCOPED OR STALE PREFLIGHT (WR-02): the
-- ADD CONSTRAINT ck_healer_role below validates every existing healer row at
-- apply time. A single violating row fails the statement with SQLSTATE 23514,
-- Flyway marks V35 failed, and the application will not boot. The only
-- mitigation is the mandatory re-run of BOTH preflight scans immediately before
-- apply.
--
-- healer_aud: ck_healer_aud_role is DROPPED with NO replacement (D-01). The
-- _aud table is an Envers append-only audit shadow — machine-written, never
-- deserialized back into the Role enum in normal operation — so a CHECK there
-- guards nothing the live-table constraint does not already catch, and a
-- historical revision holding a now-dead value must never be able to block
-- this migration.
--
-- SCOPE: touches the healer and healer_aud role CHECK constraints ONLY. No
-- other table, no other column.
--
-- The DROPs are unconditional (PD-41-04) — no existence guard. ck_healer_role
-- and ck_healer_aud_role were created by V7, which has already run in every
-- environment this migration will reach, so a bare DROP is guaranteed to
-- succeed. If it ever did not, failing loudly is the correct behaviour for a
-- defence-in-depth control.
--
-- ROLLBACK: a pure inverse constraint change — re-widen ck_healer_role to its
-- five original values, and restore ck_healer_aud_role as a nullable CHECK
-- (role IS NULL OR role IN (...)). No data to restore.
--
-- Applied migrations V0 and V7 are immutable historical record and are NOT
-- edited here; V35 supersedes them.
-- =============================================================================

ALTER TABLE healer
    DROP CONSTRAINT ck_healer_role,
    ADD CONSTRAINT ck_healer_role
        CHECK (role IN ('HEALER','SUPADM'));

ALTER TABLE healer_aud
    DROP CONSTRAINT ck_healer_aud_role;
