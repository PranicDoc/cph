package com.pranicdoc.cph.notification.controller;

import com.pranicdoc.cph.common.security.InternalApiKeyFilter;
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

/** Controller + the key filter in front of it (the filter itself is covered by InternalApiKeyFilterTest). */
class InternalNotificationControllerTest {

    private static final String PATH = "/api/v1/internal/notifications/sweep";
    private final OutboxSweeper sweeper = mock(OutboxSweeper.class);
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new InternalNotificationController(sweeper))
            .addFilters(new InternalApiKeyFilter("s3cret"))
            .build();

    @Test
    void runsSweepAndReturnsCounts() throws Exception {
        when(sweeper.sweep()).thenReturn(new OutboxSweeper.Result(1, 3, 2));

        mvc.perform(post(PATH).header(InternalApiKeyFilter.HEADER, "s3cret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deadLettered").value(1))
                .andExpect(jsonPath("$.attempted").value(3))
                .andExpect(jsonPath("$.sent").value(2));
    }

    @Test
    void neverSweepsWithoutTheKey() throws Exception {
        mvc.perform(post(PATH)).andExpect(status().isUnauthorized());
        verify(sweeper, never()).sweep();
    }
}
