package com.pranicdoc.cph.review.event;

import java.util.UUID;

/**
 * A change on a review submission. Carries both parties so every cph instance can route it to the
 * right open streams; only {@code submissionId}, {@code type} and {@code revisionNo} reach the browser.
 */
public record ReviewEvent(UUID submissionId, UUID healerId, UUID reviewerId, ReviewEventType type,
                          Integer revisionNo) {
}
