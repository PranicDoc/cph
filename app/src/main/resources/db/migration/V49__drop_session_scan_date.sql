-- V49: the scan sheet prints the session date, so the separate scan date added in V48 is not used.
ALTER TABLE healing_session DROP COLUMN IF EXISTS scan_date;
ALTER TABLE healing_session_aud DROP COLUMN IF EXISTS scan_date;
