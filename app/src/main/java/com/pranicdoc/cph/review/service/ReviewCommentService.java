package com.pranicdoc.cph.review.service;

import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.review.dto.CommentRequest;
import com.pranicdoc.cph.review.dto.CommentResponse;
import com.pranicdoc.cph.review.event.ReviewEvent;
import com.pranicdoc.cph.review.event.ReviewEventPublisher;
import com.pranicdoc.cph.review.event.ReviewEventType;
import com.pranicdoc.cph.review.model.AuthorSide;
import com.pranicdoc.cph.review.model.ReviewComment;
import com.pranicdoc.cph.review.model.ReviewRevision;
import com.pranicdoc.cph.review.model.ReviewSubmission;
import com.pranicdoc.cph.review.repository.ReviewCommentRepository;
import com.pranicdoc.cph.review.repository.ReviewRevisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Pinned comments and threads on a submission. Access reuses the row-level checks of the two
 * submission services: the healer side needs {@code healerId == caller}; the reviewer side needs the
 * submission assigned to the caller and not their own. Invalid input throws
 * {@link IllegalArgumentException}, which the global handler maps to 400.
 */
@Service
public class ReviewCommentService {

    static final int MAX_BODY = 4000;

    private final ReviewCommentRepository comments;
    private final ReviewRevisionRepository revisions;
    private final ReviewSubmissionService healerSide;
    private final ReviewInboxService reviewerSide;
    private final HealerRepository healers;
    private final SecurityContextUtil security;
    private final ReviewEventPublisher events;

    public ReviewCommentService(ReviewCommentRepository comments, ReviewRevisionRepository revisions,
                                ReviewSubmissionService healerSide, ReviewInboxService reviewerSide,
                                HealerRepository healers, SecurityContextUtil security,
                                ReviewEventPublisher events) {
        this.comments = comments;
        this.revisions = revisions;
        this.healerSide = healerSide;
        this.reviewerSide = reviewerSide;
        this.healers = healers;
        this.security = security;
        this.events = events;
    }

    private ReviewSubmission access(UUID submissionId, AuthorSide side) {
        return side == AuthorSide.HEALER
                ? healerSide.ownSubmission(submissionId)
                : reviewerSide.assignedSubmission(submissionId);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> list(UUID submissionId, AuthorSide side) {
        ReviewSubmission s = access(submissionId, side);
        List<ReviewComment> rows = comments.findBySubmissionIdOrderByCreatedAtAsc(s.getId());
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, Integer> revisionNo = revisions.findBySubmissionIdOrderByRevisionNoAsc(s.getId()).stream()
                .collect(Collectors.toMap(ReviewRevision::getId, ReviewRevision::getRevisionNo));
        // An erased author has a null id; it is never looked up and shows without a name.
        Set<UUID> authorIds = rows.stream().map(ReviewComment::getAuthorId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> names = authorIds.isEmpty() ? Map.of() : healers.findAllById(authorIds).stream()
                .collect(Collectors.toMap(Healer::getId, h -> h.getFullName() == null ? "" : h.getFullName()));
        return rows.stream()
                .map(c -> toResponse(c, revisionNo.getOrDefault(c.getRevisionId(), 0),
                        c.getAuthorId() == null ? null : names.getOrDefault(c.getAuthorId(), "")))
                .toList();
    }

    @Auditable(action = "ADD_REVIEW_COMMENT")
    @Transactional
    public CommentResponse add(UUID submissionId, CommentRequest req, AuthorSide side) {
        ReviewSubmission s = access(submissionId, side);
        String body = req.body() == null ? "" : req.body().trim();
        if (body.isEmpty() || body.length() > MAX_BODY) {
            throw new IllegalArgumentException("Comment must be between 1 and " + MAX_BODY + " characters");
        }
        boolean hasPin = req.page() != null || req.x() != null || req.y() != null;
        if (hasPin) {
            validatePin(req);
        }

        ReviewRevision revision;
        if (req.parentId() != null) {
            if (hasPin) {
                throw new IllegalArgumentException("A reply cannot carry a pin");
            }
            ReviewComment parent = comments.findById(req.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Comment", req.parentId()));
            if (!parent.getSubmissionId().equals(s.getId())) {
                throw new IllegalArgumentException("Parent comment belongs to another submission");
            }
            if (parent.getParentId() != null) {
                throw new IllegalArgumentException("Reply to the first comment of the thread");
            }
            revision = revisions.findById(parent.getRevisionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Revision", parent.getRevisionId()));
        } else {
            if (req.revisionNo() == null) {
                throw new IllegalArgumentException("revisionNo is required");
            }
            revision = revisions.findBySubmissionIdAndRevisionNo(s.getId(), req.revisionNo())
                    .orElseThrow(() -> new ResourceNotFoundException("Revision", req.revisionNo()));
            if (hasPin) {
                int latest = revisions.findTopBySubmissionIdOrderByRevisionNoDesc(s.getId())
                        .map(ReviewRevision::getRevisionNo).orElse(0);
                if (revision.getRevisionNo() != latest) {
                    throw new IllegalArgumentException(
                            "Pins can only be placed on the latest version (version " + latest + ")");
                }
            }
        }

        UUID me = security.getCurrentHealerId();
        ReviewComment saved = comments.save(ReviewComment.builder()
                .submissionId(s.getId()).revisionId(revision.getId()).parentId(req.parentId())
                .authorId(me).authorSide(side)
                .page(hasPin ? req.page() : null)
                .x(hasPin ? scale(req.x()) : null).y(hasPin ? scale(req.y()) : null)
                .body(body).build());
        events.publish(new ReviewEvent(s.getId(), s.getHealerId(), s.getReviewerId(),
                ReviewEventType.COMMENT_ADDED, revision.getRevisionNo(), side));
        String name = healers.findById(me).map(Healer::getFullName).orElse("");
        return toResponse(saved, revision.getRevisionNo(), name == null ? "" : name);
    }

    /** Reviewer only: marks a thread (its first comment) resolved. Idempotent. */
    @Auditable(action = "RESOLVE_REVIEW_COMMENT")
    @Transactional
    public void resolve(UUID commentId) {
        ReviewComment c = comments.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));
        access(c.getSubmissionId(), AuthorSide.REVIEWER);
        if (c.getParentId() != null) {
            throw new IllegalArgumentException("Resolve the thread from its first comment");
        }
        if (c.getResolvedAt() == null) {
            c.setResolvedAt(Instant.now());
            comments.save(c);
        }
    }

    private static void validatePin(CommentRequest req) {
        if (req.page() == null || req.x() == null || req.y() == null) {
            throw new IllegalArgumentException("A pin needs page, x and y");
        }
        if (req.page() < 1) {
            throw new IllegalArgumentException("page must be 1 or more");
        }
        if (!inUnitRange(req.x()) || !inUnitRange(req.y())) {
            throw new IllegalArgumentException("x and y must be between 0 and 1");
        }
    }

    private static boolean inUnitRange(double v) {
        return !Double.isNaN(v) && v >= 0 && v <= 1;
    }

    private static BigDecimal scale(Double v) {
        return BigDecimal.valueOf(v).setScale(5, RoundingMode.HALF_UP);
    }

    private static CommentResponse toResponse(ReviewComment c, int revisionNo, String authorName) {
        return new CommentResponse(c.getId(), c.getSubmissionId(), revisionNo, c.getParentId(), c.getAuthorId(),
                authorName, c.getAuthorSide(), c.getPage(),
                c.getX() == null ? null : c.getX().doubleValue(),
                c.getY() == null ? null : c.getY().doubleValue(),
                c.getBody(), c.getCreatedAt(), c.getResolvedAt());
    }
}
