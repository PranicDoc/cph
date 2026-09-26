package com.pranicdoc.cph.common.exception;

import com.pranicdoc.cph.common.enums.MaritalStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * A request body Jackson cannot bind (e.g. an enum value that does not exist) is a
 * client error, not a server fault: it must surface as a 400 rather than falling
 * through to the catch-all 500, and must not leak class names via ex.getMessage().
 */
class UnreadableRequestBodyHandlerTest {

    private MockMvc mockMvc;

    record Inner(MaritalStatus maritalStatus) {}
    record Outer(Inner patient) {}

    @RestController
    static class BindingController {
        @PostMapping("/test/bind")
        void bind(@RequestBody Outer body) {}
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new BindingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void invalidEnumValue_returns400NamingTheFieldPath() throws Exception {
        mockMvc.perform(post("/test/bind")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"patient\":{\"maritalStatus\":\"SEPARATED\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value(containsString("patient.maritalStatus")))
                .andExpect(jsonPath("$.detail").value(not(containsString("com.pranicdoc"))));
    }

    @Test
    void malformedJson_returns400WithGenericDetail() throws Exception {
        mockMvc.perform(post("/test/bind")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"patient\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Malformed request body"));
    }
}
