package com.pranicdoc.cph.consent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.consent.dto.ConsentTypeCreateRequest;
import com.pranicdoc.cph.consent.dto.ConsentTypeRenameRequest;
import com.pranicdoc.cph.consent.dto.ConsentTypeResponse;
import com.pranicdoc.cph.consent.service.ConsentTypeService;
import com.pranicdoc.cph.rbac.RbacAuthz;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer coverage for {@link ConsentTypeController}. Standalone MockMvc +
 * Mockito-mocked {@link ConsentTypeService}/{@link RbacAuthz} (no Spring/JWT
 * context), matching {@code ConsentControllerTest}'s convention.
 *
 * <p>Because standalone MockMvc has no Spring Security filter chain,
 * {@code @PreAuthorize} is never enforced at request time in these tests —
 * that is exactly why the five reflection contract tests exist (T-31.1-03):
 * each reads the {@code PreAuthorize} annotation off the compiled method and
 * asserts its literal value, which is the only automated defence in this
 * file against a future endpoint being added without a gate. The real
 * filter-chain allow/deny behaviour and the D-04 override matrix live in
 * {@code ConsentTypeControllerAuthorizationTest}.
 *
 * <p>The four capability-flag tests below stub {@code RbacAuthz.hasPermission
 * ("LEGAL_DOCUMENTS", "ADMIN")} — the D-04 source of {@code isSuperAdmin} — and
 * keep their {@code ArgumentCaptor} assertions on the boolean reaching the
 * service unchanged: that captor is the proof the service contract did not
 * change when the flag's source was swapped from {@code SecurityContextUtil}.
 */
class ConsentTypeControllerTest {

    private MockMvc mockMvc;
    private ConsentTypeService consentTypeService;
    private RbacAuthz rbacAuthz;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        consentTypeService = mock(ConsentTypeService.class);
        rbacAuthz = mock(RbacAuthz.class);
        ConsentTypeController controller = new ConsentTypeController(consentTypeService, rbacAuthz);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(new LocalValidatorFactoryBean())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ─── list() ──────────────────────────────────────────────────

    @Test
    void list_defaultsToActiveOnly() throws Exception {
        when(consentTypeService.list(false)).thenReturn(java.util.List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/consents/types"))
                .andExpect(status().isOk());

        verify(consentTypeService).list(false);
    }

    @Test
    void list_includeInactiveTrue_passesThrough() throws Exception {
        when(consentTypeService.list(true)).thenReturn(java.util.List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/consents/types").param("includeInactive", "true"))
                .andExpect(status().isOk());

        verify(consentTypeService).list(true);
    }

    // ─── create() ────────────────────────────────────────────────

    @Test
    void create_validLabel_returns201AndBody() throws Exception {
        ConsentTypeResponse response = sampleResponse();
        when(consentTypeService.create(any(ConsentTypeCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/consents/types")
                        .contentType("application/json")
                        .content("{\"label\":\"Marketing Consent\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(response.id().toString()))
                .andExpect(jsonPath("$.key").value(response.key()))
                .andExpect(jsonPath("$.label").value(response.label()));

        verify(consentTypeService).create(any(ConsentTypeCreateRequest.class));
    }

    @Test
    void create_blankLabel_returns400AndServiceNeverCalled() throws Exception {
        mockMvc.perform(post("/api/v1/consents/types")
                        .contentType("application/json")
                        .content("{\"label\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(consentTypeService, never()).create(any());
    }

    @Test
    void create_labelOverMaxLength_returns400AndServiceNeverCalled() throws Exception {
        String tooLong = "a".repeat(121);

        mockMvc.perform(post("/api/v1/consents/types")
                        .contentType("application/json")
                        .content("{\"label\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());

        verify(consentTypeService, never()).create(any());
    }

    @Test
    void create_serviceThrowsIllegalArgument_returns400WithDetail() throws Exception {
        when(consentTypeService.create(any(ConsentTypeCreateRequest.class)))
                .thenThrow(new IllegalArgumentException(
                        "A consent form with that name already exists. Please choose a different name."));

        mockMvc.perform(post("/api/v1/consents/types")
                        .contentType("application/json")
                        .content("{\"label\":\"Marketing Consent\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "A consent form with that name already exists. Please choose a different name."));
    }

    // ─── rename() ────────────────────────────────────────────────

    @Test
    void rename_validLabel_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        ConsentTypeResponse response = sampleResponse();
        when(consentTypeService.rename(eq(id), any(ConsentTypeRenameRequest.class))).thenReturn(response);

        mockMvc.perform(patch("/api/v1/consents/types/{id}/label", id)
                        .contentType("application/json")
                        .content("{\"label\":\"Renamed Consent\"}"))
                .andExpect(status().isOk());

        verify(consentTypeService).rename(eq(id), any(ConsentTypeRenameRequest.class));
    }

    @Test
    void rename_blankLabel_returns400AndServiceNeverCalled() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/consents/types/{id}/label", id)
                        .contentType("application/json")
                        .content("{\"label\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(consentTypeService, never()).rename(any(), any());
    }

    // ─── setMandatory() ──────────────────────────────────────────

    @Test
    void setMandatory_callerWithLegalDocumentsAdminGrant_passesIsSuperAdminTrue() throws Exception {
        UUID id = UUID.randomUUID();
        when(rbacAuthz.hasPermission("LEGAL_DOCUMENTS", "ADMIN")).thenReturn(true);
        when(consentTypeService.setMandatory(eq(id), eq(true), anyBoolean())).thenReturn(sampleResponse());

        mockMvc.perform(patch("/api/v1/consents/types/{id}/mandatory", id).param("value", "true"))
                .andExpect(status().isOk());

        ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(Boolean.class);
        verify(consentTypeService).setMandatory(eq(id), eq(true), captor.capture());
        assertThat(captor.getValue()).isTrue();
    }

    @Test
    void setMandatory_callerWithoutLegalDocumentsAdminGrant_passesIsSuperAdminFalse() throws Exception {
        UUID id = UUID.randomUUID();
        when(rbacAuthz.hasPermission("LEGAL_DOCUMENTS", "ADMIN")).thenReturn(false);
        when(consentTypeService.setMandatory(eq(id), eq(true), anyBoolean())).thenReturn(sampleResponse());

        mockMvc.perform(patch("/api/v1/consents/types/{id}/mandatory", id).param("value", "true"))
                .andExpect(status().isOk());

        ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(Boolean.class);
        verify(consentTypeService).setMandatory(eq(id), eq(true), captor.capture());
        assertThat(captor.getValue()).isFalse();
    }

    // ─── setActive() ─────────────────────────────────────────────

    @Test
    void setActive_callerWithLegalDocumentsAdminGrant_passesIsSuperAdminTrue() throws Exception {
        UUID id = UUID.randomUUID();
        when(rbacAuthz.hasPermission("LEGAL_DOCUMENTS", "ADMIN")).thenReturn(true);
        when(consentTypeService.setActive(eq(id), eq(false), anyBoolean())).thenReturn(sampleResponse());

        mockMvc.perform(patch("/api/v1/consents/types/{id}/active", id).param("value", "false"))
                .andExpect(status().isOk());

        ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(Boolean.class);
        verify(consentTypeService).setActive(eq(id), eq(false), captor.capture());
        assertThat(captor.getValue()).isTrue();
    }

    @Test
    void setActive_callerWithoutLegalDocumentsAdminGrant_passesIsSuperAdminFalse() throws Exception {
        UUID id = UUID.randomUUID();
        when(rbacAuthz.hasPermission("LEGAL_DOCUMENTS", "ADMIN")).thenReturn(false);
        when(consentTypeService.setActive(eq(id), eq(false), anyBoolean())).thenReturn(sampleResponse());

        mockMvc.perform(patch("/api/v1/consents/types/{id}/active", id).param("value", "false"))
                .andExpect(status().isOk());

        ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(Boolean.class);
        verify(consentTypeService).setActive(eq(id), eq(false), captor.capture());
        assertThat(captor.getValue()).isFalse();
    }

    @Test
    void setActive_serviceThrowsAccessDenied_returns403() throws Exception {
        UUID id = UUID.randomUUID();
        when(rbacAuthz.hasPermission("LEGAL_DOCUMENTS", "ADMIN")).thenReturn(false);
        when(consentTypeService.setActive(eq(id), eq(false), anyBoolean()))
                .thenThrow(new AccessDeniedException("System consent types can only be deactivated by SUPADM"));

        mockMvc.perform(patch("/api/v1/consents/types/{id}/active", id).param("value", "false"))
                .andExpect(status().isForbidden());
    }

    // ─── @PreAuthorize reflection contract tests (T-31.1-03) ──────

    @Test
    void list_isAnnotatedWithBareLegalDocumentsReadGate() throws Exception {
        Method method = ConsentTypeController.class.getMethod("list", boolean.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("@rbacAuthz.hasPermission('LEGAL_DOCUMENTS','READ')");
    }

    @Test
    void create_isAnnotatedWithBareLegalDocumentsCreateGate() throws Exception {
        Method method = ConsentTypeController.class.getMethod("create", ConsentTypeCreateRequest.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("@rbacAuthz.hasPermission('LEGAL_DOCUMENTS','CREATE')");
    }

    @Test
    void rename_isAnnotatedWithBareLegalDocumentsUpdateGate() throws Exception {
        Method method = ConsentTypeController.class.getMethod(
                "rename", UUID.class, ConsentTypeRenameRequest.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("@rbacAuthz.hasPermission('LEGAL_DOCUMENTS','UPDATE')");
    }

    @Test
    void setMandatory_isAnnotatedWithBareLegalDocumentsUpdateGate() throws Exception {
        Method method = ConsentTypeController.class.getMethod("setMandatory", UUID.class, boolean.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("@rbacAuthz.hasPermission('LEGAL_DOCUMENTS','UPDATE')");
    }

    @Test
    void setActive_isAnnotatedWithBareLegalDocumentsUpdateGate() throws Exception {
        Method method = ConsentTypeController.class.getMethod("setActive", UUID.class, boolean.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("@rbacAuthz.hasPermission('LEGAL_DOCUMENTS','UPDATE')");
    }

    // ─── structural no-delete guard (D-02) ─────────────────────────

    @Test
    void controller_exposesNoDeleteMapping() {
        for (Method method : ConsentTypeController.class.getMethods()) {
            assertThat(method.getAnnotation(DeleteMapping.class))
                    .as("method %s must not carry @DeleteMapping (D-02)", method.getName())
                    .isNull();
        }
    }

    private static ConsentTypeResponse sampleResponse() {
        return new ConsentTypeResponse(
                UUID.randomUUID(), "marketing-consent", "Marketing Consent",
                false, true, false, Instant.now());
    }
}
