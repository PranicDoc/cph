-- Feedback now records which healing it is about (healing_date) and how it arrived
-- (source): WHATSAPP = the patient submitted it through the feedback link, MANUAL = the
-- healer entered it from the Patients list. Existing rows all came through the link, and
-- their healing date is the date of the session they are attached to.

ALTER TABLE symptom_feedback
    ADD COLUMN healing_date DATE,
    ADD COLUMN source       VARCHAR(10) NOT NULL DEFAULT 'WHATSAPP',
    ADD CONSTRAINT ck_symptom_feedback_source CHECK (source IN ('WHATSAPP', 'MANUAL'));

ALTER TABLE qualitative_feedback
    ADD COLUMN healing_date DATE,
    ADD COLUMN source       VARCHAR(10) NOT NULL DEFAULT 'WHATSAPP',
    ADD CONSTRAINT ck_qualitative_feedback_source CHECK (source IN ('WHATSAPP', 'MANUAL'));

ALTER TABLE symptom_feedback_aud
    ADD COLUMN healing_date DATE,
    ADD COLUMN source       VARCHAR(10);

ALTER TABLE qualitative_feedback_aud
    ADD COLUMN healing_date DATE,
    ADD COLUMN source       VARCHAR(10);

UPDATE symptom_feedback sf
SET healing_date = hs.session_date
FROM healing_session hs
WHERE hs.id = sf.healing_session_id;

UPDATE qualitative_feedback qf
SET healing_date = hs.session_date
FROM healing_session hs
WHERE hs.id = qf.healing_session_id;
