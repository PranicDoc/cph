CREATE TABLE feedback_link (
    token        VARCHAR(36)  PRIMARY KEY,
    case_id      UUID         NOT NULL,
    clinic_id    UUID         NOT NULL,
    status       VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    expires_at   TIMESTAMPTZ  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_feedback_link_case ON feedback_link(case_id);
