package com.pranicdoc.cph.review.model;

import com.pranicdoc.cph.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A comment on a submission: general ({@code page} null) or pinned to a point on a page, with
 * {@code x}/{@code y} normalised to 0..1 against the page. A reply has a {@code parentId}, no pin,
 * and its parent's revision.
 */
@Entity
@Table(name = "review_comment")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class ReviewComment extends BaseEntity {

    @Column(name = "submission_id", nullable = false, updatable = false)
    private UUID submissionId;

    @Column(name = "revision_id", nullable = false, updatable = false)
    private UUID revisionId;

    @Column(name = "parent_id", updatable = false)
    private UUID parentId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_side", nullable = false, updatable = false)
    private AuthorSide authorSide;

    @Column(name = "page", updatable = false)
    private Integer page;

    @Column(name = "x", precision = 6, scale = 5, updatable = false)
    private BigDecimal x;

    @Column(name = "y", precision = 6, scale = 5, updatable = false)
    private BigDecimal y;

    @Column(name = "body", nullable = false, length = 4000, updatable = false)
    private String body;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
