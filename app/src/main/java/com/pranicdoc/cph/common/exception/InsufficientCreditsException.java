package com.pranicdoc.cph.common.exception;

public class InsufficientCreditsException extends RuntimeException {
    public InsufficientCreditsException() {
        super("You have run out of credits. Please top up to start a new case.");
    }
}
