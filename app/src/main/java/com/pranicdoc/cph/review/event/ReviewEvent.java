package com.pranicdoc.cph.review.event;

import com.pranicdoc.cph.review.model.AuthorSide;
import com.pranicdoc.cph.review.model.ReviewStatus;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A change on a review submission. Carries both parties so every cph instance can route it to the
 * right open streams; only {@code submissionId}, {@code type} and {@code revisionNo} reach the browser.
 * {@code actor} (who wrote the comment) is set only on {@code COMMENT_ADDED}; the notifier uses it to
 * email the other party. {@code status} is the submission's status when the service published, so the
 * notifier decides the kind of mail from the event alone, without reading anything. {@code standInId}
 * is set only when a reviewer-side action was taken by someone other than the submission's stored
 * reviewer (a super admin, possibly with no reviewer stored), so the mail names who really acted.
 * None of these three is in the browser payload.
 */
public record ReviewEvent(UUID submissionId, UUID healerId, UUID reviewerId, ReviewEventType type,
                          Integer revisionNo, AuthorSide actor, ReviewStatus status,
                          UUID standInId) implements UserEvent {

    public ReviewEvent(UUID submissionId, UUID healerId, UUID reviewerId, ReviewEventType type,
                       Integer revisionNo, AuthorSide actor, ReviewStatus status) {
        this(submissionId, healerId, reviewerId, type, revisionNo, actor, status, null);
    }

    public ReviewEvent(UUID submissionId, UUID healerId, UUID reviewerId, ReviewEventType type,
                       Integer revisionNo) {
        this(submissionId, healerId, reviewerId, type, revisionNo, null, null);
    }

    /** The {@code standInId} of a reviewer-side action: the acting user unless they are the stored reviewer. */
    public static UUID standIn(UUID actingUserId, UUID reviewerId) {
        return actingUserId.equals(reviewerId) ? null : actingUserId;
    }

    /** Both parties' streams, and no one else. */
    @Override
    @JsonIgnore
    public Set<UUID> targetUserIds() {
        Set<UUID> parties = new LinkedHashSet<>();
        if (healerId != null) {
            parties.add(healerId);
        }
        if (reviewerId != null) {
            parties.add(reviewerId);
        }
        return parties;
    }
}
