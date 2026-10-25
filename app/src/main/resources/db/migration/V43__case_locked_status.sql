-- A new case starts LOCKED and the healer unlocks it for one credit (it then becomes DRAFT).
-- Existing cases keep their status; credit_charged_at records when a case was unlocked.
ALTER TABLE healing_case
DROP CONSTRAINT ck_healing_case_status;

ALTER TABLE healing_case
ADD CONSTRAINT ck_healing_case_status
CHECK (
    status IN (
        'LOCKED',
        'DRAFT',
        'ACTIVE',
        'CLOSED',
        'CONTINUED'
    )
);

ALTER TABLE healing_case_aud
DROP CONSTRAINT ck_healing_case_aud_status;

ALTER TABLE healing_case_aud
ADD CONSTRAINT ck_healing_case_aud_status
CHECK (
    status IS NULL OR status IN (
        'LOCKED',
        'DRAFT',
        'ACTIVE',
        'CLOSED',
        'CONTINUED'
    )
);

ALTER TABLE healing_case
    ADD COLUMN IF NOT EXISTS credit_charged_at TIMESTAMPTZ;

-- Mirror into the audit table so Envers history stays consistent.
ALTER TABLE healing_case_aud
    ADD COLUMN IF NOT EXISTS credit_charged_at TIMESTAMPTZ;
