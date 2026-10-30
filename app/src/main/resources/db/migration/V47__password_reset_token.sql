-- =============================================================================
-- V47__password_reset_token.sql
-- Forgot password: a one-time, short-lived token is emailed to the healer. Only the
-- SHA-256 hash of the token is stored, so a database read cannot be turned into a
-- working reset link. A token is spent when used_at is set.
-- =============================================================================

CREATE TABLE password_reset_token (
    id          UUID         NOT NULL,
    healer_id   UUID         NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_password_reset_token PRIMARY KEY (id),
    CONSTRAINT fk_password_reset_token_healer FOREIGN KEY (healer_id) REFERENCES healer(id),
    CONSTRAINT uq_password_reset_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_password_reset_token_healer ON password_reset_token (healer_id) WHERE used_at IS NULL;
