-- Optional body measurements (BMI is derived, never stored) and four patient-filled histories.
-- The living record is patient_medical_history; case_medical_snapshot mirrors it per case, so both get the columns.
-- The four histories are encrypted at rest like additional_notes, hence TEXT.
ALTER TABLE patient_medical_history
    ADD COLUMN height_cm            NUMERIC(5,1),
    ADD COLUMN weight_kg            NUMERIC(5,1),
    ADD COLUMN past_history         TEXT,
    ADD COLUMN surgical_history     TEXT,
    ADD COLUMN family_history       TEXT,
    ADD COLUMN psychosocial_history TEXT;

ALTER TABLE case_medical_snapshot
    ADD COLUMN height_cm            NUMERIC(5,1),
    ADD COLUMN weight_kg            NUMERIC(5,1),
    ADD COLUMN past_history         TEXT,
    ADD COLUMN surgical_history     TEXT,
    ADD COLUMN family_history       TEXT,
    ADD COLUMN psychosocial_history TEXT;

-- Mirror into the audit tables so Envers history stays consistent.
ALTER TABLE patient_medical_history_aud
    ADD COLUMN height_cm            NUMERIC(5,1),
    ADD COLUMN weight_kg            NUMERIC(5,1),
    ADD COLUMN past_history         TEXT,
    ADD COLUMN surgical_history     TEXT,
    ADD COLUMN family_history       TEXT,
    ADD COLUMN psychosocial_history TEXT;

ALTER TABLE case_medical_snapshot_aud
    ADD COLUMN height_cm            NUMERIC(5,1),
    ADD COLUMN weight_kg            NUMERIC(5,1),
    ADD COLUMN past_history         TEXT,
    ADD COLUMN surgical_history     TEXT,
    ADD COLUMN family_history       TEXT,
    ADD COLUMN psychosocial_history TEXT;
