-- =============================================================================
-- V61__review_revision_snapshot_hash.sql
-- Content hash (SHA-256 hex) of the case data a revision's PDF was built from.
-- A submit whose case data hashes the same as the latest revision adds no revision.
-- Existing revisions keep NULL and never match.
-- =============================================================================

ALTER TABLE review_revision     ADD COLUMN snapshot_hash VARCHAR(64);
ALTER TABLE review_revision_aud ADD COLUMN snapshot_hash VARCHAR(64);
