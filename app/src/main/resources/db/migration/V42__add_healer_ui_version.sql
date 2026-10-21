-- Which web UI the user chose in the profile menu (classic or the redesigned "new view").
-- Stored on the account so the choice follows the user across devices. Existing rows stay on CLASSIC.
ALTER TABLE healer
    ADD COLUMN IF NOT EXISTS ui_version VARCHAR(10) NOT NULL DEFAULT 'CLASSIC';

ALTER TABLE healer
    ADD CONSTRAINT ck_healer_ui_version CHECK (ui_version IN ('CLASSIC', 'NEW'));

-- Mirror into the audit table so Envers history stays consistent.
ALTER TABLE healer_aud
    ADD COLUMN IF NOT EXISTS ui_version VARCHAR(10);
