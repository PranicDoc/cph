-- =============================================================================
-- V29__widen_consent_language_column.sql
-- Fixes a pre-existing Phase 30 schema/entity mismatch discovered while
-- verifying Phase 34's live "I Agree" flow: Consent.java declares
-- `@Column(name = "language", length = 100)` but V25 actually created the
-- column as VARCHAR(10). ConsentService.accept() stores the raw
-- Accept-Language request header verbatim (e.g. "en-US,en;q=0.9", 14+ chars),
-- which overflows VARCHAR(10) and surfaces as a 409 (DataIntegrityViolation
-- -> GlobalExceptionHandler's generic CONFLICT mapping) on every real browser
-- acceptance. Widening to VARCHAR(100) matches the entity's own declared
-- length -- no application code change needed.
-- =============================================================================

ALTER TABLE consent     ALTER COLUMN language TYPE VARCHAR(100);
ALTER TABLE consent_aud ALTER COLUMN language TYPE VARCHAR(100);
