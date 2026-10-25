-- One-time backfill, run MANUALLY right after V44__prana_color_categories.sql is applied
-- (before any protocol import, which creates categorised colors).
-- NOT a Flyway migration: sets category = ENERGISING on every existing prana color
-- (system + healer-custom, including soft-deleted rows) that has none yet. Recategorise
-- or add the CLEANSING copies from Master Data -> Prana Colors afterwards.
--
-- Live colors are unique per (lower(name), category), so a live uncategorised color is
-- skipped when its name already exists in ENERGISING or repeats another uncategorised
-- live color; the last query lists those for manual review. Safe to re-run.

BEGIN;

-- Soft-deleted rows are outside the unique index: always safe.
UPDATE prana_color_catalog
SET category = 'ENERGISING'
WHERE category IS NULL
  AND is_deleted = true;

WITH ranked AS (
    SELECT id,
           row_number() OVER (PARTITION BY lower(name) ORDER BY created_at, id) AS rn
    FROM prana_color_catalog
    WHERE category IS NULL
      AND is_deleted = false
)
UPDATE prana_color_catalog c
SET category = 'ENERGISING'
FROM ranked r
WHERE c.id = r.id
  AND r.rn = 1
  AND NOT EXISTS (
      SELECT 1 FROM prana_color_catalog e
      WHERE e.is_deleted = false
        AND e.category = 'ENERGISING'
        AND lower(e.name) = lower(c.name)
  );

-- Review: live colors still without a category (should be empty; otherwise rename,
-- delete or categorise them by hand).
SELECT id, code, name, healer_id
FROM prana_color_catalog
WHERE category IS NULL
  AND is_deleted = false
ORDER BY lower(name), created_at;

COMMIT;
