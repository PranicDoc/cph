-- V2 created feedback_link without the isFinal field that was added to the entity later.
-- FeedbackLink.isFinal maps to @Column(name = "is_final"); DEFAULT FALSE matches @Builder.Default.
ALTER TABLE feedback_link
    ADD COLUMN IF NOT EXISTS is_final BOOLEAN NOT NULL DEFAULT FALSE;
