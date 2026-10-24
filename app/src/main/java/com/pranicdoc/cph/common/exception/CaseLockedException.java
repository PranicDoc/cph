package com.pranicdoc.cph.common.exception;

/**
 * Thrown when a healer reads or changes a case that has not been unlocked with a credit yet.
 */
public class CaseLockedException extends RuntimeException {

    public CaseLockedException() {
        super("Unlock this case to continue");
    }
}
