-- A prana color is CLEANSING or ENERGISING. A color used both ways is two rows (one per
-- category, each with its own properties), so the same name may appear once per category.
-- Existing colors are NOT backfilled here: the one-time "mark every existing color
-- ENERGISING" update is run manually outside Flyway. Until then category is NULL, which the
-- unique index ignores (NULLs are distinct).

ALTER TABLE prana_color_catalog
    ADD COLUMN category VARCHAR(20),
    ADD CONSTRAINT ck_prana_color_catalog_category CHECK (category IN ('CLEANSING', 'ENERGISING'));

ALTER TABLE prana_color_catalog_aud
    ADD COLUMN category VARCHAR(20);

-- One live color per name (case-insensitive) and category; soft-deleted rows do not block re-adding.
CREATE UNIQUE INDEX uq_prana_color_catalog_name_category
    ON prana_color_catalog (lower(name), category)
    WHERE is_deleted = false;
