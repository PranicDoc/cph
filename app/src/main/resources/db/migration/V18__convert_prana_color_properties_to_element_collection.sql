-- Converts prana_color_catalog.properties (free-text TEXT description) into a
-- proper @ElementCollection List<String> child table, per the TODO on
-- PranaColorCatalog: lets a healer pick individual color properties instead
-- of a single opaque description. Mirrors the existing treatment_step_targets
-- / treatment_step_targets_aud @ElementCollection pattern (no explicit
-- @CollectionTable/@OrderColumn, so Hibernate's default naming applies).

CREATE TABLE prana_color_catalog_properties (
    prana_color_catalog_id  UUID         NOT NULL,
    properties               VARCHAR(255),
    CONSTRAINT fk_prana_color_catalog_properties FOREIGN KEY (prana_color_catalog_id) REFERENCES prana_color_catalog(id)
);

CREATE TABLE prana_color_catalog_properties_aud (
    prana_color_catalog_id  UUID,
    rev                      BIGINT      NOT NULL,
    revtype                  SMALLINT,
    properties               VARCHAR(255),
    CONSTRAINT pk_prana_color_catalog_properties_aud     PRIMARY KEY (prana_color_catalog_id, rev, properties),
    CONSTRAINT fk_prana_color_catalog_properties_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

-- Backfill: split each existing comma-separated properties description into
-- individual rows (e.g. "Strengthening, warming, energizing" -> 3 rows).
INSERT INTO prana_color_catalog_properties (prana_color_catalog_id, properties)
SELECT id, trim(part)
FROM prana_color_catalog, unnest(string_to_array(properties, ',')) AS part
WHERE properties IS NOT NULL
  AND trim(properties) <> ''
  AND trim(part) <> '';

ALTER TABLE prana_color_catalog DROP COLUMN properties;
ALTER TABLE prana_color_catalog_aud DROP COLUMN properties;
