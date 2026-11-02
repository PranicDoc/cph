-- V48: the date a session's scan was done (printed on the PDF's scan sheet). Defaults to the
-- session date for existing sessions; healers can set it per session in the scan step.
ALTER TABLE healing_session ADD COLUMN scan_date DATE;
ALTER TABLE healing_session_aud ADD COLUMN scan_date DATE;
UPDATE healing_session SET scan_date = session_date WHERE scan_date IS NULL;
