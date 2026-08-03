package com.pranicdoc.cph.consent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pranicdoc.cph.consent.dto.ConsentAcceptRequest;
import com.pranicdoc.cph.consent.dto.ConsentResponse;
import com.pranicdoc.cph.consent.dto.PolicyVersionResponse;
import com.pranicdoc.cph.consent.model.ConsentType;
import com.pranicdoc.cph.consent.service.ConsentService;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer coverage for {@link ConsentController}. Standalone MockMvc + a
 * Mockito-mocked {@link ConsentService} (no Spring/JWT context), matching
 * the lightweight controller-test precedent in cph. Critically proves T-30-01:
 * a request body carrying ipAddress/userAgent/language is silently dropped by
 * Jackson binding to {@link ConsentAcceptRequest} — server-captured metadata
 * cannot be smuggled through the accept endpoint's body.
 */
class ConsentControllerTest {

    private MockMvc mockMvc;
    private ConsentService consentService;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        consentService = mock(ConsentService.class);
        ConsentController controller = new ConsentController(consentService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(new LocalValidatorFactoryBean())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ─── accept() ────────────────────────────────────────────────

    @Test
    void accept_validRequest_returns201AndDelegates() throws Exception {
        ConsentResponse response = sampleConsentResponse();
        when(consentService.accept(any(ConsentAcceptRequest.class), any(HttpServletRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/consents")
                        .contentType("application/json")
                        .content("{\"consentType\":\"PRIVACY_POLICY\",\"policyVersion\":1}"))
                .andExpect(status().isCreated());

        verify(consentService).accept(any(ConsentAcceptRequest.class), any(HttpServletRequest.class));
    }

    @Test
    void accept_bodyWithSpoofedMetadata_metadataNeverReachesService() throws Exception {
        ConsentResponse response = sampleConsentResponse();
        when(consentService.accept(any(ConsentAcceptRequest.class), any(HttpServletRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/consents")
                        .contentType("application/json")
                        .content("""
                                {
                                  "consentType": "PRIVACY_POLICY",
                                  "policyVersion": 1,
                                  "ipAddress": "1.2.3.4",
                                  "userAgent": "evil",
                                  "language": "zz"
                                }
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<ConsentAcceptRequest> captor = ArgumentCaptor.forClass(ConsentAcceptRequest.class);
        verify(consentService).accept(captor.capture(), any(HttpServletRequest.class));

        ConsentAcceptRequest captured = captor.getValue();
        assertThat(captured.consentType()).isEqualTo(ConsentType.PRIVACY_POLICY);
        assertThat(captured.policyVersion()).isEqualTo(1);
        // ConsentAcceptRequest structurally has no ipAddress/userAgent/language accessors —
        // the extra JSON fields above were silently dropped by Jackson at bind time.
    }

    @Test
    void accept_missingConsentType_returns400AndServiceNeverCalled() throws Exception {
        mockMvc.perform(post("/api/v1/consents")
                        .contentType("application/json")
                        .content("{\"policyVersion\":1}"))
                .andExpect(status().isBadRequest());

        verify(consentService, never()).accept(any(), any());
    }

    // ─── withdraw() ──────────────────────────────────────────────

    @Test
    void withdraw_returns200AndDelegates() throws Exception {
        UUID id = UUID.randomUUID();
        ConsentResponse response = sampleConsentResponse();
        when(consentService.withdraw(id)).thenReturn(response);

        mockMvc.perform(delete("/api/v1/consents/{id}", id))
                .andExpect(status().isOk());

        verify(consentService).withdraw(id);
    }

    // ─── history() ───────────────────────────────────────────────

    @Test
    void history_returns200WithList() throws Exception {
        when(consentService.history()).thenReturn(List.of(sampleConsentResponse()));

        mockMvc.perform(get("/api/v1/consents/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));

        verify(consentService).history();
    }

    // ─── activePolicies() ────────────────────────────────────────

    @Test
    void activePolicies_returns200() throws Exception {
        when(consentService.activePolicyVersions()).thenReturn(List.of(samplePolicyVersionResponse()));

        mockMvc.perform(get("/api/v1/consents/policy-versions/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));

        verify(consentService).activePolicyVersions();
    }

    // ─── outstanding() ───────────────────────────────────────────

    @Test
    void outstanding_returns200() throws Exception {
        when(consentService.outstandingConsents()).thenReturn(List.of(samplePolicyVersionResponse()));

        mockMvc.perform(get("/api/v1/consents/outstanding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));

        verify(consentService).outstandingConsents();
    }

    private static ConsentResponse sampleConsentResponse() {
        return new ConsentResponse(
                UUID.randomUUID(), UUID.randomUUID(), ConsentType.PRIVACY_POLICY, 1,
                Instant.now(), null, "CLICKWRAP");
    }

    private static PolicyVersionResponse samplePolicyVersionResponse() {
        return new PolicyVersionResponse(
                UUID.randomUUID(), ConsentType.PRIVACY_POLICY, 1, true, Instant.now());
    }
}
