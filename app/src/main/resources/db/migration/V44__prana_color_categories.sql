-- A prana color is CLEANSING, ENERGISING, or both. Stored as an
-- @ElementCollection Set<ColorUsageType> child table, mirroring
-- prana_color_catalog_properties (V18) and its default Hibernate naming.
-- Existing colors are NOT backfilled here: the one-time "mark every existing
-- color ENERGISING" update is run manually outside Flyway.

CREATE TABLE prana_color_catalog_categories (
    prana_color_catalog_id  UUID         NOT NULL,
    categories               VARCHAR(20)  NOT NULL,
    CONSTRAINT pk_prana_color_catalog_categories PRIMARY KEY (prana_color_catalog_id, categories),
    CONSTRAINT fk_prana_color_catalog_categories FOREIGN KEY (prana_color_catalog_id) REFERENCES prana_color_catalog(id),
    CONSTRAINT ck_prana_color_catalog_categories CHECK (categories IN ('CLEANSING', 'ENERGISING'))
);

CREATE TABLE prana_color_catalog_categories_aud (
    prana_color_catalog_id  UUID,
    rev                      BIGINT      NOT NULL,
    revtype                  SMALLINT,
    categories               VARCHAR(20),
    CONSTRAINT pk_prana_color_catalog_categories_aud     PRIMARY KEY (prana_color_catalog_id, rev, categories),
    CONSTRAINT fk_prana_color_catalog_categories_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);
