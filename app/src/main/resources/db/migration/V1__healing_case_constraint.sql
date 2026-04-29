CREATE UNIQUE INDEX ux_patient_clinic_draft
ON healing_case (patient_id, clinic_id)
WHERE status = 'DRAFT';