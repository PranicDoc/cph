package com.pranicdoc.cph.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

/** Turns an error response from creditService into an exception that keeps its status and readable message. */
final class UpstreamErrors {

    private UpstreamErrors() {
    }

    static ResponseStatusException toException(HttpStatusCode status, String body, ObjectMapper mapper) {
        String message = body == null ? "" : body.strip();
        try {
            JsonNode detail = mapper.readTree(message).path("detail");
            if (detail.isTextual() && !detail.asText().isBlank()) {
                message = detail.asText();
            }
        } catch (Exception notJson) {
            // keep the raw body
        }
        return new ResponseStatusException(status, message.isEmpty() ? "Credit service request failed" : message);
    }
}
