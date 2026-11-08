-- A color used both ways is two rows (CLEANSING / ENERGISING, see V44), so the same short code
-- may now appear once per category instead of once globally.
-- Rows with a NULL category (not yet backfilled) never conflict: NULLs are distinct in a unique index.
ALTER TABLE prana_color_catalog DROP CONSTRAINT uq_prana_color_catalog_code;

-- One live color per code (case-insensitive) and category; soft-deleted rows do not block re-adding.
CREATE UNIQUE INDEX uq_prana_color_catalog_code_category
    ON prana_color_catalog (lower(code), category)
    WHERE is_deleted = false;
