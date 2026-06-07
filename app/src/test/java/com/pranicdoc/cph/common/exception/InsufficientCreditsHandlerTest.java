package com.pranicdoc.cph.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InsufficientCreditsHandlerTest {

    private MockMvc mockMvc;

    @RestController
    static class ThrowingController {
        @GetMapping("/test/insufficient")
        void trigger() {
            throw new InsufficientCreditsException();
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
    void insufficientCredits_returns402WithProblemDetail() throws Exception {
        mockMvc.perform(get("/test/insufficient").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.status").value(402))
                .andExpect(jsonPath("$.title").value("Insufficient Credits"));
    }
}
