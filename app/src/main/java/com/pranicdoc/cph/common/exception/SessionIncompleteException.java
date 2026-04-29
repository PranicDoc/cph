package com.pranicdoc.cph.common.exception;

/**
 * Thrown when attempting to complete a session that has missing required steps
 * (e.g., no pre-scan, no protocol, no post-scan).
 */
public class SessionIncompleteException extends RuntimeException {

    public SessionIncompleteException(String message) {
        super(message);
    }

    public SessionIncompleteException() {
        super("Session cannot be completed — required steps are missing");
    }
}
