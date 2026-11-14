package com.pranicdoc.cph.common.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A timed-out or abandoned SSE stream is a normal end, not an error: it must not reach the
 * catch-all handler, which logs an ERROR and writes a ProblemDetail onto the committed stream.
 */
class AsyncRequestEndedHandlerTest {

    @RestController
    static class AsyncEndingController {
        @GetMapping("/test/async-timeout")
        void timeout() {
            throw new AsyncRequestTimeoutException();
        }

        @GetMapping("/test/async-not-usable")
        void notUsable() throws AsyncRequestNotUsableException {
            throw new AsyncRequestNotUsableException("client went away");
        }
    }

    private MockMvc mockMvc;
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @BeforeEach
    void setUp() {
        logs.start();
        handlerLogger.addAppender(logs);
        mockMvc = MockMvcBuilders.standaloneSetup(new AsyncEndingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        handlerLogger.detachAppender(logs);
    }

    @Test
    void asyncTimeout_isHandledQuietlyNotByTheCatchAll() throws Exception {
        mockMvc.perform(get("/test/async-timeout"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        assertThat(logs.list).noneMatch(e -> e.getLevel() == Level.ERROR);
    }

    @Test
    void asyncNotUsable_isHandledQuietlyNotByTheCatchAll() throws Exception {
        mockMvc.perform(get("/test/async-not-usable"))
                .andExpect(content().string(""));

        assertThat(logs.list).noneMatch(e -> e.getLevel() == Level.ERROR);
    }
}
