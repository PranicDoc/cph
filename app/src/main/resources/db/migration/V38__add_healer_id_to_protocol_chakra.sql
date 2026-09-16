-- =============================================================================
-- V38__add_healer_id_to_protocol_chakra.sql
-- Lets a healer add their own private chakra (and, transitively, color)
-- associations to a protocol, independent of the system (bulk-imported)
-- associations and every other healer's additions -- mirrors the nullable
-- healer_id "system vs custom" pattern already used by protocol_catalog /
-- chakra_catalog / prana_color_catalog / protocol_color_mapping (see V19's
-- fk_protocol_color_mapping_healer for the closest precedent: nullable
-- healer_id + named FK + a healer-inclusive UNIQUE constraint).
--
-- protocol_chakra_color does NOT get its own healer_id column: a color row
-- is always created as a child of exactly one protocol_chakra row (via
-- protocol_chakra_id), so its ownership is transitively whatever that
-- parent row's healer_id is -- adding a second, independently-settable
-- healer_id here would let a color row disagree with its own parent's
-- ownership for no reason.
--
-- The old UNIQUE(protocol_id, chakra_id) is replaced by
-- UNIQUE(protocol_id, chakra_id, healer_id): one system row per
-- (protocol, chakra) as before, and now each healer may independently add
-- their own row for that same (protocol, chakra) pair without colliding
-- with the system row or with any other healer's row. (Postgres treats
-- each NULL as distinct for uniqueness purposes, same as
-- protocol_color_mapping's healer-keyed constraint already relies on --
-- de-duplication of the NULL/system row is enforced by the import path's
-- existing find-or-create check, not by this constraint, exactly as today.)
-- =============================================================================

ALTER TABLE protocol_chakra
    ADD COLUMN healer_id UUID,
    ADD CONSTRAINT fk_protocol_chakra_healer FOREIGN KEY (healer_id) REFERENCES healer(id);

CREATE INDEX idx_protocol_chakra_healer ON protocol_chakra (healer_id);

ALTER TABLE protocol_chakra DROP CONSTRAINT uq_protocol_chakra;
ALTER TABLE protocol_chakra ADD CONSTRAINT uq_protocol_chakra_healer UNIQUE (protocol_id, chakra_id, healer_id);

ALTER TABLE protocol_chakra_aud ADD COLUMN IF NOT EXISTS healer_id UUID;
