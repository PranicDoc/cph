package com.pranicdoc.cph.review.event;

import com.pranicdoc.cph.review.model.AuthorSide;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A change on a review submission. Carries both parties so every cph instance can route it to the
 * right open streams; only {@code submissionId}, {@code type} and {@code revisionNo} reach the browser.
 * {@code actor} (who wrote the comment) is set only on {@code COMMENT_ADDED}; the notifier uses it to
 * email the other party. It never leaves the server.
 */
public record ReviewEvent(UUID submissionId, UUID healerId, UUID reviewerId, ReviewEventType type,
                          Integer revisionNo, AuthorSide actor) implements UserEvent {

    public ReviewEvent(UUID submissionId, UUID healerId, UUID reviewerId, ReviewEventType type,
                       Integer revisionNo) {
        this(submissionId, healerId, reviewerId, type, revisionNo, null);
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
