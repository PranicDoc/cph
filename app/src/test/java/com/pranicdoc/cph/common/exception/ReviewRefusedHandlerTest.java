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

class ReviewRefusedHandlerTest {

    private MockMvc mockMvc;

    @RestController
    static class ThrowingController {
        @GetMapping("/test/review-refused")
        void trigger() {
            throw new ReviewRefusedException("A newer version was submitted. Refresh to review it.");
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
    void reviewRefused_returns409CarryingTheMessageVerbatim() throws Exception {
        mockMvc.perform(get("/test/review-refused").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Review Action Refused"))
                .andExpect(jsonPath("$.detail").value("A newer version was submitted. Refresh to review it."));
    }
}
