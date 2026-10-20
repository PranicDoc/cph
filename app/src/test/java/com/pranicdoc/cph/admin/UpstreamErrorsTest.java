package com.pranicdoc.cph.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpstreamErrorsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void keepsTheUpstreamStatusAndShowsItsDetailText() {
        ResponseStatusException ex = UpstreamErrors.toException(HttpStatus.CONFLICT,
                "{\"title\":\"Conflict\",\"status\":409,\"detail\":\"Accounts are on the PRO plan. Disable the package instead of deleting it.\"}",
                mapper);

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Accounts are on the PRO plan. Disable the package instead of deleting it.", ex.getReason());
    }

    @Test
    void fallsBackToTheRawBodyWhenItIsNotAProblemDocument() {
        ResponseStatusException ex = UpstreamErrors.toException(HttpStatus.BAD_GATEWAY, "upstream down", mapper);

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
        assertEquals("upstream down", ex.getReason());
    }

    @Test
    void usesAGenericMessageForAnEmptyBody() {
        ResponseStatusException ex = UpstreamErrors.toException(HttpStatus.NOT_FOUND, "", mapper);

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Credit service request failed", ex.getReason());
    }
}
