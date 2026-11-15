package com.pranicdoc.cph.review;

import com.pranicdoc.cph.review.dto.CommentRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CommentRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> invalidFields(CommentRequest req) {
        return validator.validate(req).stream().map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void validPinAndReply_haveNoViolations() {
        assertThat(invalidFields(new CommentRequest(2, null, 1, 0.0, 1.0, "Look here"))).isEmpty();
        assertThat(invalidFields(new CommentRequest(null, UUID.randomUUID(), null, null, null, "thanks"))).isEmpty();
        assertThat(invalidFields(new CommentRequest(2, null, null, null, null, "a".repeat(4000)))).isEmpty();
    }

    @Test
    void blankOrOversizedBody_isInvalid() {
        assertThat(invalidFields(new CommentRequest(2, null, 1, 0.5, 0.5, "   "))).containsExactly("body");
        assertThat(invalidFields(new CommentRequest(2, null, 1, 0.5, 0.5, null))).containsExactly("body");
        assertThat(invalidFields(new CommentRequest(2, null, 1, 0.5, 0.5, "a".repeat(4001)))).containsExactly("body");
    }

    @Test
    void coordinatesOutsideUnitRange_areInvalid() {
        assertThat(invalidFields(new CommentRequest(2, null, 1, -0.1, 0.5, "x"))).containsExactly("x");
        assertThat(invalidFields(new CommentRequest(2, null, 1, 0.5, 1.01, "x"))).containsExactly("y");
    }

    @Test
    void pageAndRevisionNoBelowOne_areInvalid() {
        assertThat(invalidFields(new CommentRequest(2, null, 0, 0.5, 0.5, "x"))).containsExactly("page");
        assertThat(invalidFields(new CommentRequest(0, null, 1, 0.5, 0.5, "x"))).containsExactly("revisionNo");
        assertThat(invalidFields(new CommentRequest(-3, null, 1, 0.5, 0.5, "x"))).containsExactly("revisionNo");
    }
}
