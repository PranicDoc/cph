-- One-time backfill, run MANUALLY after V44__prana_color_categories.sql is applied.
-- NOT a Flyway migration: marks every existing prana color (system + healer-custom,
-- including soft-deleted rows) ENERGISING. Recategorise individual colors from
-- Master Data -> Prana Colors afterwards. Safe to re-run: colors that already have
-- any category are skipped.

BEGIN;

INSERT INTO prana_color_catalog_categories (prana_color_catalog_id, categories)
SELECT c.id, 'ENERGISING'
FROM prana_color_catalog c
WHERE NOT EXISTS (
    SELECT 1 FROM prana_color_catalog_categories cc
    WHERE cc.prana_color_catalog_id = c.id
);

-- Verify: should return 0.
SELECT count(*) AS colors_without_category
FROM prana_color_catalog c
WHERE NOT EXISTS (
    SELECT 1 FROM prana_color_catalog_categories cc
    WHERE cc.prana_color_catalog_id = c.id
);

COMMIT;
