-- Add auth_provider column to healer table for SSO support.
-- Existing rows are LOCAL (email/password) by default.
ALTER TABLE healer
    ADD COLUMN IF NOT EXISTS auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL';

-- Mirror into the audit table so Envers history stays consistent.
ALTER TABLE healer_aud
    ADD COLUMN IF NOT EXISTS auth_provider VARCHAR(20);
