package com.pranicdoc.cph.notification.controller;

import com.pranicdoc.cph.notification.service.OutboxSweeper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalNotificationControllerTest {

    private static final String PATH = "/api/v1/internal/notifications/sweep";
    private final OutboxSweeper sweeper = mock(OutboxSweeper.class);

    @Test
    void runsSweepWithCorrectKey() throws Exception {
        when(sweeper.sweep()).thenReturn(new OutboxSweeper.Result(1, 3, 2));

        mvc("s3cret").perform(post(PATH).header("X-Internal-Key", "s3cret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deadLettered").value(1))
                .andExpect(jsonPath("$.attempted").value(3))
                .andExpect(jsonPath("$.sent").value(2));
    }

    @Test
    void rejectsWrongOrMissingKey() throws Exception {
        MockMvc mvc = mvc("s3cret");

        mvc.perform(post(PATH).header("X-Internal-Key", "nope")).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH)).andExpect(status().isUnauthorized());
        verify(sweeper, never()).sweep();
    }

    @Test
    void endpointDoesNotExistWhenNoKeyIsConfigured() throws Exception {
        mvc("").perform(post(PATH).header("X-Internal-Key", "")).andExpect(status().isNotFound());
        verify(sweeper, never()).sweep();
    }

    private MockMvc mvc(String configuredKey) {
        return MockMvcBuilders.standaloneSetup(new InternalNotificationController(sweeper, configuredKey)).build();
    }
}
