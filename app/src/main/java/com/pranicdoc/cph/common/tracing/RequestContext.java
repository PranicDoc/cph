package com.pranicdoc.cph.common.tracing;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The two IDs that tie log lines and audit rows together. {@code requestId} identifies one HTTP
 * request; {@code correlationId} identifies the whole flow, and stays the same across services and
 * background work. Both live in the logging MDC, so every log line on the thread carries them.
 */
public final class RequestContext {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String REQUEST_ID_KEY = "requestId";
    public static final String CORRELATION_ID_KEY = "correlationId";

    /** Headers can come from outside, and they end up in logs: anything else is replaced, not trusted. */
    private static final Pattern VALID_ID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    private RequestContext() {}

    /** The header value if it is a well-formed ID, otherwise a new one. */
    public static String validOrNew(String candidate) {
        return candidate != null && VALID_ID.matcher(candidate).matches() ? candidate : newId();
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    public static String requestId() {
        return MDC.get(REQUEST_ID_KEY);
    }

    public static String correlationId() {
        return MDC.get(CORRELATION_ID_KEY);
    }

    public static void set(String requestId, String correlationId) {
        MDC.put(REQUEST_ID_KEY, requestId);
        MDC.put(CORRELATION_ID_KEY, correlationId);
    }

    public static void clear() {
        MDC.remove(REQUEST_ID_KEY);
        MDC.remove(CORRELATION_ID_KEY);
    }
}
