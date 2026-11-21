package com.pranicdoc.cph.review.dto;

import com.pranicdoc.cph.review.model.AuthorSide;

import java.time.Instant;
import java.util.UUID;

/** {@code revisionNo} lets the UI label a comment "on version N". */
public record CommentResponse(
        UUID id,
        UUID submissionId,
        int revisionNo,
        UUID parentId,
        UUID authorId,
        String authorName,
        AuthorSide authorSide,
        Integer page,
        Double x,
        Double y,
        String body,
        Instant createdAt,
        Instant resolvedAt) {
}
