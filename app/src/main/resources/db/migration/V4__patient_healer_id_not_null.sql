-- STAGED MIGRATION -- do not apply until orphan patient rows (healer_id IS NULL) have been manually resolved
-- Run: SELECT COUNT(*) FROM patient WHERE healer_id IS NULL; to verify zero orphans before enabling
-- Re-enable by moving this file into the migration directory and restarting the application

ALTER TABLE patient ALTER COLUMN healer_id SET NOT NULL;
