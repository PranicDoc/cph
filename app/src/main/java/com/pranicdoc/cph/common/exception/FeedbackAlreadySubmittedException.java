package com.pranicdoc.cph.common.exception;

public class FeedbackAlreadySubmittedException extends RuntimeException {
    public FeedbackAlreadySubmittedException() {
        super("Feedback Already Submitted.");
    }
}
