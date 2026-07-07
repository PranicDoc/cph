-- Solo healer self-registration (SELFREG-01): extends the ADMIN-01 invite-link
-- table so the same token infrastructure can carry self-signup rows alongside
-- admin-invite rows. Self-signup requests have no clinic_id/invited_by_healer_id
-- at request time (no clinic exists yet -- one is auto-created on accept), so
-- those columns must become nullable. The new `source` discriminator lets the
-- application branch between ADMIN_INVITE and SELF_SIGNUP behavior.
ALTER TABLE healer_invite_links ALTER COLUMN clinic_id DROP NOT NULL;
ALTER TABLE healer_invite_links ALTER COLUMN invited_by_healer_id DROP NOT NULL;

-- DEFAULT 'ADMIN_INVITE' backfills all existing admin-invite rows so no
-- existing behavior changes for them.
ALTER TABLE healer_invite_links ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'ADMIN_INVITE';

-- D-09: at most one PENDING self-signup token per email. This is the DB
-- backstop for the rotate-in-place guarantee (resubmitting /signup silently
-- rotates the existing pending token instead of creating a second one) --
-- analogous to V13's uk_healer_invite_pending_email_clinic index for admin
-- invites, but keyed on email alone since self-signup rows have no clinic_id
-- to key off of until accept time.
CREATE UNIQUE INDEX uk_healer_invite_pending_email_selfsignup
    ON healer_invite_links (email)
    WHERE status = 'PENDING' AND source = 'SELF_SIGNUP';
