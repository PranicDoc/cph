package com.pranicdoc.cph.common.exception;

/**
 * Thrown by {@link com.pranicdoc.cph.common.ratelimit.RateLimitFilter} when a per-IP or
 * per-email Bucket4j bucket is exhausted for one of the rate-limited public auth endpoints
 * (INFRA-02, D-11/D-12/D-13). Mirrors {@link InsufficientCreditsException}'s shape
 * (a plain {@code RuntimeException}), but takes a parameterized message since the filter
 * needs to distinguish IP-exhaustion from email-exhaustion in the response detail.
 */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
