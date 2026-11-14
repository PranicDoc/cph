package com.pranicdoc.cph.review.dto;

import java.util.UUID;

/**
 * A new comment. {@code page}/{@code x}/{@code y} are all set for a pin (x and y in 0..1) and all
 * null otherwise; a reply sets {@code parentId} and no pin.
 */
public record CommentRequest(Integer revisionNo, UUID parentId, Integer page, Double x, Double y, String body) {
}
