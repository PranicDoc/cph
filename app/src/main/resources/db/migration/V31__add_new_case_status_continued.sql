ALTER TABLE healing_case
DROP CONSTRAINT ck_healing_case_status;

ALTER TABLE healing_case
ADD CONSTRAINT ck_healing_case_status
CHECK (
    status IN (
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
        'DRAFT',
        'ACTIVE',
        'CLOSED',
        'CONTINUED'
    )
);
