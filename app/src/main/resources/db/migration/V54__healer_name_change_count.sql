-- How many times a healer has changed their full name; drives the escalating credit cost
-- (first change free, then 25 doubling). Existing healers start at 0, so their next change is free.
ALTER TABLE healer ADD COLUMN name_change_count INTEGER NOT NULL DEFAULT 0;

-- Mirror into the audit table so Envers history stays consistent.
ALTER TABLE healer_aud ADD COLUMN name_change_count INTEGER;
