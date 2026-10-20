package com.pranicdoc.cph.common.http;

import org.springframework.http.HttpHeaders;

/** How an outbound call authenticates. Pass {@link #none()} for public endpoints. */
public sealed interface HttpAuth {

    void apply(HttpHeaders headers);

    static HttpAuth none() {
        return None.INSTANCE;
    }

    static HttpAuth bearer(String token) {
        return new Bearer(token);
    }

    static HttpAuth basic(String username, String password) {
        return new Basic(username, password);
    }

    /** A static key in a header of your choosing, e.g. {@code apiKey("X-Api-Key", key)}. */
    static HttpAuth apiKey(String headerName, String key) {
        return new ApiKey(headerName, key);
    }

    enum None implements HttpAuth {
        INSTANCE;

        @Override
        public void apply(HttpHeaders headers) {
        }
    }

    record Bearer(String token) implements HttpAuth {
        @Override
        public void apply(HttpHeaders headers) {
            headers.setBearerAuth(token);
        }

        @Override
        public String toString() {
            return "Bearer[***]";
        }
    }

    record Basic(String username, String password) implements HttpAuth {
        @Override
        public void apply(HttpHeaders headers) {
            headers.setBasicAuth(username, password);
        }

        @Override
        public String toString() {
            return "Basic[" + username + ":***]";
        }
    }

    record ApiKey(String headerName, String key) implements HttpAuth {
        @Override
        public void apply(HttpHeaders headers) {
            headers.set(headerName, key);
        }

        @Override
        public String toString() {
            return "ApiKey[" + headerName + "=***]";
        }
    }
}
