-- Adds DIVORCED and WIDOWED to the MaritalStatus CHECK constraints (V7 allowed
-- only SINGLE, MARRIED, OTHERS). The registration form already offers both.

ALTER TABLE patient
DROP CONSTRAINT ck_patient_marital_status;

ALTER TABLE patient
ADD CONSTRAINT ck_patient_marital_status
CHECK (
    marital_status IS NULL OR marital_status IN (
        'SINGLE',
        'MARRIED',
        'DIVORCED',
        'WIDOWED',
        'OTHERS'
    )
);

ALTER TABLE patient_aud
DROP CONSTRAINT ck_patient_aud_marital_status;

ALTER TABLE patient_aud
ADD CONSTRAINT ck_patient_aud_marital_status
CHECK (
    marital_status IS NULL OR marital_status IN (
        'SINGLE',
        'MARRIED',
        'DIVORCED',
        'WIDOWED',
        'OTHERS'
    )
);
