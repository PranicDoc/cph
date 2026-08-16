package com.pranicdoc.cph.consent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.consent.dto.ReadingSessionCloseRequest;
import com.pranicdoc.cph.consent.dto.ReadingSessionOpenRequest;
import com.pranicdoc.cph.consent.dto.ReadingSessionResponse;
import com.pranicdoc.cph.consent.service.ReadingSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer coverage for {@link ReadingSessionController}. Standalone MockMvc
 * + a Mockito-mocked {@link ReadingSessionService} (no Spring/JWT context),
 * matching the {@code ConsentControllerTest} house style. Proves both the
 * dual-method close mapping (PATCH and the {@code navigator.sendBeacon} POST
 * path — Pitfall 2) and the trust boundary: spoofed timing/subject fields in
 * request bodies are structurally unable to reach the service (T-33-01,
 * T-33-02).
 */
class ReadingSessionControllerTest {

    private MockMvc mockMvc;
    private ReadingSessionService readingSessionService;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        readingSessionService = mock(ReadingSessionService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ReadingSessionController(readingSessionService))
                .setValidator(new LocalValidatorFactoryBean())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ─── open() ──────────────────────────────────────────────────

    @Test
    void open_validRequest_returns201AndDelegates() throws Exception {
        when(readingSessionService.open(any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/consents/reading-sessions")
                        .contentType("application/json")
                        .content("{\"consentType\":\"PRIVACY_POLICY\",\"policyVersion\":1}"))
                .andExpect(status().isCreated());

        verify(readingSessionService).open(any());
    }

    @Test
    void open_blankConsentType_returns400AndNeverReachesService() throws Exception {
        mockMvc.perform(post("/api/v1/consents/reading-sessions")
                        .contentType("application/json")
                        .content("{\"consentType\":\"\",\"policyVersion\":1}"))
                .andExpect(status().isBadRequest());

        verify(readingSessionService, never()).open(any());
    }

    @Test
    void open_bodyWithSpoofedOpenedAt_metadataNeverReachesService() throws Exception {
        when(readingSessionService.open(any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/consents/reading-sessions")
                        .contentType("application/json")
                        .content("""
                                {
                                  "consentType": "PRIVACY_POLICY",
                                  "policyVersion": 1,
                                  "openedAt": "2000-01-01T00:00:00Z",
                                  "subjectId": "11111111-1111-1111-1111-111111111111"
                                }
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<ReadingSessionOpenRequest> captor = ArgumentCaptor.forClass(ReadingSessionOpenRequest.class);
        verify(readingSessionService).open(captor.capture());

        ReadingSessionOpenRequest captured = captor.getValue();
        assertThat(captured.consentType()).isEqualTo("PRIVACY_POLICY");
        assertThat(captured.policyVersion()).isEqualTo(1);
        // ReadingSessionOpenRequest structurally has no openedAt/subjectId accessors —
        // the extra JSON fields above were silently dropped by Jackson at bind time.
    }

    // ─── close() ─────────────────────────────────────────────────

    @Test
    void close_patchPath_returns200AndDelegates() throws Exception {
        UUID id = UUID.randomUUID();
        when(readingSessionService.close(any(), any())).thenReturn(sampleResponse());

        mockMvc.perform(patch("/api/v1/consents/reading-sessions/{id}/close", id)
                        .contentType("application/json")
                        .content("{\"scrollCompleted\":true}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ReadingSessionCloseRequest> captor = ArgumentCaptor.forClass(ReadingSessionCloseRequest.class);
        verify(readingSessionService).close(eq(id), captor.capture());
        assertThat(captor.getValue().scrollCompleted()).isTrue();
    }

    @Test
    void close_postBeaconPath_returns200AndDelegates() throws Exception {
        // navigator.sendBeacon() can only issue POST (Pitfall 2) — this proves the same
        // /close URL answers to POST identically to PATCH. Do not delete as a "duplicate"
        // of close_patchPath_returns200AndDelegates.
        UUID id = UUID.randomUUID();
        when(readingSessionService.close(any(), any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/consents/reading-sessions/{id}/close", id)
                        .contentType("application/json")
                        .content("{\"scrollCompleted\":true}"))
                .andExpect(status().isOk());

        verify(readingSessionService).close(any(), any());
    }

    @Test
    void close_bodyWithSpoofedDurationAndClosedAt_neverReachesService() throws Exception {
        UUID id = UUID.randomUUID();
        when(readingSessionService.close(any(), any())).thenReturn(sampleResponse());

        mockMvc.perform(patch("/api/v1/consents/reading-sessions/{id}/close", id)
                        .contentType("application/json")
                        .content("""
                                {
                                  "scrollCompleted": true,
                                  "closedAt": "2000-01-01T00:00:00Z",
                                  "durationSeconds": 99999
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<ReadingSessionCloseRequest> captor = ArgumentCaptor.forClass(ReadingSessionCloseRequest.class);
        verify(readingSessionService).close(eq(id), captor.capture());
        assertThat(captor.getValue().scrollCompleted()).isTrue();
        // ReadingSessionCloseRequest structurally has no closedAt/durationSeconds accessors —
        // the extra JSON fields above were silently dropped by Jackson at bind time.
    }

    private static ReadingSessionResponse sampleResponse() {
        return new ReadingSessionResponse(
                UUID.randomUUID(), UUID.randomUUID(), "PRIVACY_POLICY", 1,
                Instant.now(), null, null, false);
    }
}
