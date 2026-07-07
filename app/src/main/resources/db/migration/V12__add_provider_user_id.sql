-- Add provider_user_id column to healer table for Google account linking.
-- Stores the Google OIDC `sub` claim. NULL means "not linked" — this is
-- distinct from LOCAL/GOOGLE auth_provider and is the sole source of truth
-- for link state (see 21-google-oauth-linking D-09).
ALTER TABLE healer
    ADD COLUMN IF NOT EXISTS provider_user_id VARCHAR(255);

-- Mirror into the audit table so Envers history stays consistent.
ALTER TABLE healer_aud
    ADD COLUMN IF NOT EXISTS provider_user_id VARCHAR(255);

-- Enforce one Google account can only be linked to a single healer.
-- Plain UNIQUE (no partial index) is intentional: Postgres treats multiple
-- NULLs as distinct under a plain UNIQUE constraint, so all currently-NULL
-- (unlinked) healer rows coexist without violating the constraint.
ALTER TABLE healer
    ADD CONSTRAINT uk_healer_provider_user_id UNIQUE (provider_user_id);
