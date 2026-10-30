package com.pranicdoc.cph.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CreditServiceClient forwards upstream 4xx/5xx as ResponseStatusException (WR-02); the
 * catch-all Exception handler used to swallow those and answer 500 for every one of them.
 */
class ResponseStatusPassthroughHandlerTest {

    private MockMvc mockMvc;

    @RestController
    static class ThrowingController {
        @GetMapping("/test/conflict")
        void conflict() {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already on the PRO plan");
        }

        @GetMapping("/test/bad-gateway")
        void badGateway() {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Payment failed");
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("unrelated failure");
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void upstream4xx_isForwardedWithItsOwnStatus() throws Exception {
        mockMvc.perform(get("/test/conflict").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Already on the PRO plan"));
    }

    @Test
    void upstream5xx_isForwardedWithItsOwnStatus() throws Exception {
        mockMvc.perform(get("/test/bad-gateway").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));
    }

    @Test
    void unrelatedException_stillHitsCatchAll500() throws Exception {
        mockMvc.perform(get("/test/boom").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }
}
