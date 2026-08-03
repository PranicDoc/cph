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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
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

    // ─── publish() ───────────────────────────────────────────────

    @Test
    void publish_pastedTextMode_returns201AndDelegatesWithNullFile() throws Exception {
        PolicyVersionResponse response = samplePolicyVersionResponse();
        when(consentService.publish(any(ConsentType.class), any(), anyString(), any()))
                .thenReturn(response);

        mockMvc.perform(multipart("/api/v1/consents/policy-versions")
                        .param("consentType", "PRIVACY_POLICY")
                        .param("content", "Sample pasted policy text."))
                .andExpect(status().isCreated());

        ArgumentCaptor<ConsentType> typeCaptor = ArgumentCaptor.forClass(ConsentType.class);
        ArgumentCaptor<MultipartFile> fileCaptor = ArgumentCaptor.forClass(MultipartFile.class);
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(consentService).publish(typeCaptor.capture(), fileCaptor.capture(), contentCaptor.capture(), any());

        assertThat(typeCaptor.getValue()).isEqualTo(ConsentType.PRIVACY_POLICY);
        assertThat(fileCaptor.getValue()).isNull();
        assertThat(contentCaptor.getValue()).isEqualTo("Sample pasted policy text.");
    }

    @Test
    void publish_fileUploadMode_returns201AndDelegatesWithNullContent() throws Exception {
        PolicyVersionResponse response = samplePolicyVersionResponse();
        when(consentService.publish(any(ConsentType.class), any(), any(), any()))
                .thenReturn(response);

        byte[] fileBytes = "Uploaded policy document content.".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "policy.md", "text/markdown", fileBytes);

        mockMvc.perform(multipart("/api/v1/consents/policy-versions")
                        .file(file)
                        .param("consentType", "TERMS_AND_CONDITIONS"))
                .andExpect(status().isCreated());

        ArgumentCaptor<MultipartFile> fileCaptor = ArgumentCaptor.forClass(MultipartFile.class);
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(consentService).publish(eq(ConsentType.TERMS_AND_CONDITIONS), fileCaptor.capture(), contentCaptor.capture(), any());

        assertThat(fileCaptor.getValue()).isNotNull();
        assertThat(fileCaptor.getValue().getBytes()).isEqualTo(fileBytes);
        assertThat(contentCaptor.getValue()).isNull();
    }

    @Test
    void publish_returns201WithFullSevenFieldResponseBody() throws Exception {
        PolicyVersionResponse response = samplePolicyVersionResponse();
        when(consentService.publish(any(ConsentType.class), any(), anyString(), any()))
                .thenReturn(response);

        mockMvc.perform(multipart("/api/v1/consents/policy-versions")
                        .param("consentType", "PRIVACY_POLICY")
                        .param("content", "Sample pasted policy text."))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(response.id().toString()))
                .andExpect(jsonPath("$.consentType").value(response.consentType().name()))
                .andExpect(jsonPath("$.policyVersion").value(response.policyVersion()))
                .andExpect(jsonPath("$.mandatory").value(response.mandatory()))
                .andExpect(jsonPath("$.effectiveDate").exists())
                .andExpect(jsonPath("$.content").value(response.content()))
                .andExpect(jsonPath("$.publisherId").value(response.publisherId().toString()));

        assertThat(response.content()).isNotNull();
        assertThat(response.publisherId()).isNotNull();
    }

    @Test
    void publish_bothFileAndContentProvided_returns400WithExactDetail() throws Exception {
        when(consentService.publish(any(ConsentType.class), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Provide either pasted text or a file, not both."));

        MockMultipartFile file = new MockMultipartFile("file", "policy.md", "text/markdown", "content".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/consents/policy-versions")
                        .file(file)
                        .param("consentType", "PRIVACY_POLICY")
                        .param("content", "Also pasted text."))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Provide either pasted text or a file, not both."));
    }

    @Test
    void publish_neitherFileNorContentProvided_returns400() throws Exception {
        when(consentService.publish(any(ConsentType.class), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Provide either pasted text or a file."));

        mockMvc.perform(multipart("/api/v1/consents/policy-versions")
                        .param("consentType", "PRIVACY_POLICY"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publish_missingConsentType_returns400AndServiceNeverInvoked() throws Exception {
        mockMvc.perform(multipart("/api/v1/consents/policy-versions")
                        .param("content", "Sample pasted policy text."))
                .andExpect(status().isBadRequest());

        verify(consentService, never()).publish(any(), any(), any(), any());
    }

    /**
     * Reflection assertion rather than a request-level 403 test: this suite uses
     * standalone MockMvc with no Spring Security filter chain, so {@code @PreAuthorize}
     * is not enforced at request time here — the live 403 behaviour (T-31-01) is
     * verified by Plan 07's checkpoint.
     */
    @Test
    void publish_isAnnotatedWithAdminSupadmPreAuthorize() throws Exception {
        Method publishMethod = ConsentController.class.getMethod(
                "publish", ConsentType.class, MultipartFile.class, String.class, Boolean.class);

        PreAuthorize preAuthorize = publishMethod.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasAnyRole('ADMIN', 'SUPADM')");
    }

    private static ConsentResponse sampleConsentResponse() {
        return new ConsentResponse(
                UUID.randomUUID(), UUID.randomUUID(), ConsentType.PRIVACY_POLICY, 1,
                Instant.now(), null, "CLICKWRAP");
    }

    private static PolicyVersionResponse samplePolicyVersionResponse() {
        return new PolicyVersionResponse(
                UUID.randomUUID(), ConsentType.PRIVACY_POLICY, 1, true, Instant.now(),
                "Sample policy content.", UUID.randomUUID());
    }
}
