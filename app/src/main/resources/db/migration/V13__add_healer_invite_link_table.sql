-- Admin healer onboarding (ADMIN-01): standalone invite-link table.
-- Deliberately NOT modeled on BaseEntity/TenantEntity (see HealerInviteLink.java) —
-- no Envers audit-history table is created here on purpose (Pitfall 1, RESEARCH.md).
CREATE TABLE healer_invite_links (
    id                    UUID         PRIMARY KEY,
    token                 VARCHAR(36)  NOT NULL UNIQUE,
    email                 VARCHAR(255) NOT NULL,
    role                  VARCHAR(20)  NOT NULL,
    clinic_id             UUID         NOT NULL REFERENCES clinic(id),
    invited_by_healer_id  UUID         NOT NULL REFERENCES healer(id),
    status                VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    expires_at            TIMESTAMPTZ  NOT NULL,
    accepted_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- SC-1: prevents two simultaneously-valid PENDING invites for the same (email, clinic_id).
-- The DB is the single source of truth here, closing a check-then-insert race a
-- Java-only guard would leave open.
CREATE UNIQUE INDEX uk_healer_invite_pending_email_clinic
    ON healer_invite_links (email, clinic_id)
    WHERE status = 'PENDING';

CREATE INDEX idx_healer_invite_clinic_status ON healer_invite_links (clinic_id, status);
