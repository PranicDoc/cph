package com.pranicdoc.cph.review.service;

/** The PDF could not be read from or written to storage. A server fault: deliberately not mapped to a 4xx. */
public class ReviewStorageException extends RuntimeException {
    public ReviewStorageException(String message) {
        super(message);
    }

    public ReviewStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
