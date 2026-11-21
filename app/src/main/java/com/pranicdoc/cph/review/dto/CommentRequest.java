package com.pranicdoc.cph.review.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * A new comment. {@code page}/{@code x}/{@code y} are all set for a pin (x and y in 0..1) and all
 * null otherwise; a reply sets {@code parentId} and no pin. Field rules are enforced here; the
 * rules that span fields or depend on stored state live in {@code ReviewCommentService}.
 */
public record CommentRequest(
        @Positive(message = "revisionNo must be 1 or more") Integer revisionNo,
        UUID parentId,
        @Min(value = 1, message = "page must be 1 or more") Integer page,
        @DecimalMin(value = "0", message = "x must be between 0 and 1")
        @DecimalMax(value = "1", message = "x must be between 0 and 1") Double x,
        @DecimalMin(value = "0", message = "y must be between 0 and 1")
        @DecimalMax(value = "1", message = "y must be between 0 and 1") Double y,
        @NotBlank(message = "Comment must not be blank")
        @Size(max = 4000, message = "Comment must be at most 4000 characters") String body) {
}
