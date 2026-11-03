-- Patient delete = move to a trash for a retention window (app.patient.retention-days, default 45),
-- then purged for good. Each patient also gets a short immutable code (PT-000001) that support can quote.

ALTER TABLE patient ADD COLUMN patient_code VARCHAR(12);
ALTER TABLE patient ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE patient_aud ADD COLUMN patient_code VARCHAR(12);
ALTER TABLE patient_aud ADD COLUMN deleted_at TIMESTAMPTZ;

-- Existing patients (deleted ones included) get sequential codes in registration order.
UPDATE patient p
   SET patient_code = 'PT-' || lpad(n.rn::text, 6, '0')
  FROM (SELECT id, row_number() OVER (ORDER BY created_at, id) AS rn FROM patient) n
 WHERE p.id = n.id;

-- Patients already soft-deleted before this migration start their retention window now.
UPDATE patient SET deleted_at = now() WHERE is_deleted AND deleted_at IS NULL;

ALTER TABLE patient ALTER COLUMN patient_code SET NOT NULL;
CREATE UNIQUE INDEX uq_patient_code ON patient (patient_code);
CREATE INDEX idx_patient_deleted_at ON patient (deleted_at) WHERE is_deleted;

-- Cases removed together with their patient come back with the patient; a case the healer deleted
-- on its own stays deleted.
ALTER TABLE healing_case ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE healing_case ADD COLUMN deleted_with_patient BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE healing_case_aud ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE healing_case_aud ADD COLUMN deleted_with_patient BOOLEAN;
