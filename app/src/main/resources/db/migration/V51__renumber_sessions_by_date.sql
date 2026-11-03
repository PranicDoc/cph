-- =============================================================================
-- V51: session numbers follow the session date
--
-- A session may now be given an earlier date than the sessions already saved; the
-- earlier the date, the lower the number. Existing cases are renumbered 1..n by
-- (session_date, created_at, id). Only cases whose numbers are out of order are
-- touched. Two passes (via negative numbers) because (healing_case_id, session_number)
-- is unique and rows are checked one at a time.
-- =============================================================================

CREATE TEMP TABLE session_renumber ON COMMIT DROP AS
WITH ranked AS (
    SELECT s.id,
           s.healing_case_id,
           s.session_number,
           ROW_NUMBER() OVER (
               PARTITION BY s.healing_case_id
               ORDER BY s.session_date NULLS LAST, s.created_at, s.id
           ) AS rn
    FROM healing_session s
    WHERE s.is_deleted = false
)
SELECT id, rn
FROM ranked
WHERE healing_case_id IN (
    SELECT healing_case_id FROM ranked WHERE session_number IS DISTINCT FROM rn
);

UPDATE healing_session s
SET session_number = -r.rn
FROM session_renumber r
WHERE s.id = r.id;

UPDATE healing_session s
SET session_number = r.rn
FROM session_renumber r
WHERE s.id = r.id;
