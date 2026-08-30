-- =============================================================================
-- V36__cleanup_healer_invite_links_retired_roles.sql
-- Phase 41 — CR-01 remediation (applied after phase close, from code review).
--
-- WHY THIS EXISTS
-- Phase 41 shrank com.pranicdoc.cph.common.enums.Role to exactly HEALER and
-- SUPADM, and V35 realigned the CHECK constraints on the healer / healer_aud
-- tables. The healer_invite_links table (created by V13, which runs AFTER V7 and
-- was therefore never covered by V7's role CHECK) has NO CHECK constraint on its
-- role column at all, so V35 correctly leaves it untouched — there is no
-- constraint there to swap. This is a Hibernate enum-hydration fix, not a
-- constraint fix: V36 is pure DML, no DDL.
--
-- THE GAP CR-01 FOUND
-- healer_invite_links.role is @Enumerated(EnumType.STRING). Before this phase the
-- invite API let a caller mint invites for the now-retired non-HEALER tiers, so a
-- legacy environment can still hold healer_invite_links rows whose role string is
-- no longer an enum constant. After the enum shrink Hibernate cannot hydrate such
-- a row: it throws IllegalArgumentException ("No enum constant ... Role.<value>")
-- during entity materialization. A single stranded PENDING or accepted row then
-- 500s GET /api/admin/invites (the whole page fails to hydrate), resend/revoke on
-- that row, and acceptInvite at the findByToken hydration step — upstream of, and
-- defeating, the InviteService HEALER-only invariant.
--
-- WHAT THIS MIGRATION DOES
--   1. Revoke still-pending invites minted with a now-retired role tier. They
--      must not silently resolve to HEALER on the next accept; a pending invite
--      for a tier that no longer exists is void.
--   2. Relabel every remaining retired-tier row (any status) to HEALER so
--      @Enumerated(STRING) hydration of historical healer_invite_links rows
--      succeeds under the shrunk enum.
--
-- 'REVOKED' is a valid value of HealerInviteLink.Status
-- ({ PENDING, ACCEPTED, REVOKED }); it is the model's terminal void status and is
-- exactly what InviteService.revoke() already writes.
--
-- DEFENSIVE / IDEMPOTENT
-- The extended D-03 preflight (see V35 header and 41-VERIFICATION-EVIDENCE.md,
-- MIGRATE-05 / SC2) now scans healer_invite_links as well as healer, and both
-- scans must return 0 immediately before V35 is applied. The operator's
-- 2026-08-30 `healer` audit returned 0. On an environment where both preflight
-- scans return 0 this migration matches no rows and is a no-op.
-- =============================================================================

UPDATE healer_invite_links
   SET status = 'REVOKED'
 WHERE status = 'PENDING'
   AND role IN ('ADMIN', 'SENIOR_HEALER', 'RECEPTIONIST');

UPDATE healer_invite_links
   SET role = 'HEALER'
 WHERE role IN ('ADMIN', 'SENIOR_HEALER', 'RECEPTIONIST');
