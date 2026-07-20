package com.pranicdoc.cph.common.exception;

public class InsufficientCreditsException extends RuntimeException {
    public InsufficientCreditsException() {
        super("You have insufficient credits to generate a PDF. Please top up.");
    }
}
