-- Add the hash column
ALTER TABLE patient
ADD COLUMN contact_hash VARCHAR(64);

ALTER TABLE patient_aud
ADD COLUMN contact_hash VARCHAR(64);

-- Add the unique constraint
ALTER TABLE patient
ADD CONSTRAINT uk_patient_name_phone_clinic
UNIQUE (
    clinic_id,
    first_name,
    last_name,
    contact_hash
);