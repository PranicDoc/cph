-- =============================================================================
-- V60__review_comment.sql
-- Case review: comments on a submission. A comment is either general (page NULL)
-- or pinned to a point on a PDF page (page, x, y with x/y normalised to 0..1).
-- Replies (parent_id set) carry no pin and sit on their parent's revision.
-- =============================================================================

CREATE TABLE review_comment (
    id            UUID          NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    is_deleted    BOOLEAN       NOT NULL DEFAULT FALSE,
    version       BIGINT        NOT NULL DEFAULT 0,
    submission_id UUID          NOT NULL,
    revision_id   UUID          NOT NULL,
    parent_id     UUID,
    author_id     UUID          NOT NULL,
    author_side   VARCHAR(10)   NOT NULL,
    page          INTEGER,
    x             NUMERIC(6,5),
    y             NUMERIC(6,5),
    body          VARCHAR(4000) NOT NULL,
    resolved_at   TIMESTAMPTZ,
    CONSTRAINT pk_review_comment PRIMARY KEY (id),
    CONSTRAINT fk_review_comment_submission FOREIGN KEY (submission_id) REFERENCES review_submission(id),
    CONSTRAINT fk_review_comment_revision   FOREIGN KEY (revision_id)   REFERENCES review_revision(id),
    CONSTRAINT fk_review_comment_parent     FOREIGN KEY (parent_id)     REFERENCES review_comment(id),
    CONSTRAINT fk_review_comment_author     FOREIGN KEY (author_id)     REFERENCES healer(id),
    CONSTRAINT ck_review_comment_side CHECK (author_side IN ('HEALER', 'REVIEWER')),
    CONSTRAINT ck_review_comment_pin  CHECK ((page IS NULL AND x IS NULL AND y IS NULL)
                                          OR (page IS NOT NULL AND x IS NOT NULL AND y IS NOT NULL
                                              AND x BETWEEN 0 AND 1 AND y BETWEEN 0 AND 1))
);

CREATE INDEX idx_review_comment_submission ON review_comment (submission_id, created_at);
CREATE INDEX idx_review_comment_parent     ON review_comment (parent_id);

-- Envers shadow table (BaseEntity is @Audited).
CREATE TABLE review_comment_aud (
    id            UUID,
    rev           BIGINT NOT NULL,
    revtype       SMALLINT,
    created_at    TIMESTAMPTZ,
    updated_at    TIMESTAMPTZ,
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    is_deleted    BOOLEAN,
    version       BIGINT,
    submission_id UUID,
    revision_id   UUID,
    parent_id     UUID,
    author_id     UUID,
    author_side   VARCHAR(10),
    page          INTEGER,
    x             NUMERIC(6,5),
    y             NUMERIC(6,5),
    body          VARCHAR(4000),
    resolved_at   TIMESTAMPTZ,
    CONSTRAINT pk_review_comment_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_review_comment_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);
