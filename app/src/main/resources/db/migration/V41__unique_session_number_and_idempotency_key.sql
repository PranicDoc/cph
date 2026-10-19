-- =============================================================================
-- V41: make session creation safe to retry
--
-- 2026-09-21: POST /sessions/case returned 504 (nginx 30 s) although the backend
-- had committed; the healer resubmitted and a second session with the same
-- session_number was created for the case. Nothing in the schema stopped it.
--
--   1. Renumber live sessions in the cases that have a duplicate number, so the
--      unique index below can be built. Only those cases are touched; their sessions
--      are renumbered 1..n in (session_number, created_at, id) order. No row is
--      deleted. Whether the duplicate session itself should be removed is a data
--      decision made separately.
--   2. Unique (healing_case_id, session_number) over live (is_deleted = false) rows.
--   3. client_request_id: the client's Idempotency-Key; a retried submit with the
--      same key returns the original session. Unique when present. Not added to
--      healing_session_aud (the field is @NotAudited).
-- =============================================================================

-- 1. Renumber cases with duplicate live session numbers
WITH dup_cases AS (
    SELECT healing_case_id
    FROM healing_session
    WHERE is_deleted = false AND session_number IS NOT NULL
    GROUP BY healing_case_id, session_number
    HAVING COUNT(*) > 1
),
ranked AS (
    SELECT s.id,
           ROW_NUMBER() OVER (
               PARTITION BY s.healing_case_id
               ORDER BY s.session_number NULLS LAST, s.created_at, s.id
           ) AS rn
    FROM healing_session s
    WHERE s.is_deleted = false
      AND s.healing_case_id IN (SELECT healing_case_id FROM dup_cases)
)
UPDATE healing_session s
SET session_number = r.rn
FROM ranked r
WHERE s.id = r.id
  AND s.session_number IS DISTINCT FROM r.rn;

-- 2. One live session per number per case
CREATE UNIQUE INDEX IF NOT EXISTS uq_healing_session_case_number
    ON healing_session (healing_case_id, session_number)
    WHERE is_deleted = false;

-- 3. Idempotency key
ALTER TABLE healing_session ADD COLUMN IF NOT EXISTS client_request_id UUID;

CREATE UNIQUE INDEX IF NOT EXISTS uq_healing_session_client_request_id
    ON healing_session (client_request_id)
    WHERE client_request_id IS NOT NULL;
