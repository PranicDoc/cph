ALTER TABLE case_medication DROP COLUMN IF EXISTS frequency;
ALTER TABLE case_medication ADD COLUMN IF NOT EXISTS morning_dose   BOOLEAN DEFAULT FALSE;
ALTER TABLE case_medication ADD COLUMN IF NOT EXISTS afternoon_dose BOOLEAN DEFAULT FALSE;
ALTER TABLE case_medication ADD COLUMN IF NOT EXISTS night_dose     BOOLEAN DEFAULT FALSE;

UPDATE case_medication SET morning_dose   = FALSE WHERE morning_dose   IS NULL;
UPDATE case_medication SET afternoon_dose = FALSE WHERE afternoon_dose IS NULL;
UPDATE case_medication SET night_dose     = FALSE WHERE night_dose     IS NULL;

ALTER TABLE case_medication_aud DROP COLUMN IF EXISTS frequency;
ALTER TABLE case_medication_aud ADD COLUMN IF NOT EXISTS morning_dose   BOOLEAN;
ALTER TABLE case_medication_aud ADD COLUMN IF NOT EXISTS afternoon_dose BOOLEAN;
ALTER TABLE case_medication_aud ADD COLUMN IF NOT EXISTS night_dose     BOOLEAN;
