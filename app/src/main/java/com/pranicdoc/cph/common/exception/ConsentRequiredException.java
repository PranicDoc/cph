package com.pranicdoc.cph.common.exception;

/**
 * Thrown when a patient's declaration/consent has not been signed
 * and data access is therefore blocked.
 */
public class ConsentRequiredException extends RuntimeException {

    public ConsentRequiredException(String message) {
        super(message);
    }

    public ConsentRequiredException() {
        super("Patient consent (Declaration) is required before accessing this data");
    }
}
