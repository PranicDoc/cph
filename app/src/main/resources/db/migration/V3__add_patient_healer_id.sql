ALTER TABLE patient
    ADD COLUMN healer_id UUID,
    ADD CONSTRAINT fk_patient_healer FOREIGN KEY (healer_id) REFERENCES healer(id);

UPDATE patient p
    SET healer_id = h.id
    FROM healer h
    WHERE LOWER(p.created_by) = LOWER(h.email)
      AND p.healer_id IS NULL;

CREATE INDEX idx_patient_healer_id ON patient(healer_id);
