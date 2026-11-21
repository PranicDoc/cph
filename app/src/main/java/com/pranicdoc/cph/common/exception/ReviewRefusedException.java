package com.pranicdoc.cph.common.exception;

/** A business refusal of a review action (no instructor linked, no PDF yet, already approved); surfaces as a 409. */
public class ReviewRefusedException extends RuntimeException {
    public ReviewRefusedException(String message) {
        super(message);
    }
}
