package com.pranicdoc.cph.review;

import com.pranicdoc.cph.admin.AdminAuthz;
import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.security.OAuth2AuthenticationSuccessHandler;
import com.pranicdoc.cph.common.security.SecurityConfig;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.tenant.TenantInterceptor;
import com.pranicdoc.cph.rbac.RbacAuthz;
import com.pranicdoc.cph.rbac.service.RolePermissionLookupService;
import com.pranicdoc.cph.review.controller.InternalPdfJobController;
import com.pranicdoc.cph.review.event.PdfReadyEvent;
import com.pranicdoc.cph.review.event.ReviewEventPublisher;
import com.pranicdoc.cph.review.service.PdfJobCaseResolver;
import com.pranicdoc.cph.review.service.ReviewAutoSubmitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalPdfJobController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, AdminAuthz.class, SecurityContextUtil.class,
        InternalPdfJobControllerTest.RbacAuthzSliceConfig.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "cph.internal.token=s3cret")
class InternalPdfJobControllerTest {

    @TestConfiguration
    static class RbacAuthzSliceConfig {
        @Bean
        RbacAuthz rbacAuthz(SecurityContextUtil securityContextUtil, RolePermissionLookupService lookupService) {
            return new RbacAuthz(securityContextUtil, lookupService);
        }
    }

    @Autowired private MockMvc mockMvc;

    @MockBean private ReviewAutoSubmitService autoSubmit;
    @MockBean private PdfJobCaseResolver jobCases;
    @MockBean private ReviewEventPublisher events;
    @MockBean private RolePermissionLookupService lookupService;
    @MockBean private JwtService jwtService;
    @MockBean private TenantInterceptor tenantInterceptor;
    @MockBean private HealerCacheService healerCacheService;
    @MockBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    private static final String PATH = "/internal/pdf-jobs/job-1/completed";

    @Test
    void missingToken_401() throws Exception {
        mockMvc.perform(post(PATH)).andExpect(status().isUnauthorized());
        verifyNoInteractions(autoSubmit, jobCases, events);
    }

    @Test
    void wrongToken_401() throws Exception {
        mockMvc.perform(post(PATH).header("X-Internal-Token", "nope")).andExpect(status().isUnauthorized());
        verifyNoInteractions(autoSubmit, jobCases, events);
    }

    @Test
    void validToken_204_andServiceCalled() throws Exception {
        mockMvc.perform(post(PATH).header("X-Internal-Token", "s3cret")).andExpect(status().isNoContent());
        verify(autoSubmit).onPdfCompleted("job-1");
    }

    @Test
    void unknownJob_204() throws Exception {
        // The service is a silent no-op for a job it cannot resolve.
        mockMvc.perform(post("/internal/pdf-jobs/ghost/completed").header("X-Internal-Token", "s3cret"))
                .andExpect(status().isNoContent());
        verify(autoSubmit).onPdfCompleted("ghost");
    }

    @Test
    void serviceThrows_still204() throws Exception {
        doThrow(new IllegalStateException("boom")).when(autoSubmit).onPdfCompleted("job-1");

        mockMvc.perform(post(PATH).header("X-Internal-Token", "s3cret")).andExpect(status().isNoContent());
    }

    private static final UUID CASE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID HEALER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Test
    void webhook_publishesPdfReady_evenWhenAutoSubmitIsOff() throws Exception {
        when(jobCases.resolve("job-1")).thenReturn(Optional.of(new PdfJobCaseResolver.JobCase(CASE_ID, HEALER_ID)));
        // Auto-submit being off is a silent no-op in the service; the event must not depend on it.

        mockMvc.perform(post(PATH).header("X-Internal-Token", "s3cret")).andExpect(status().isNoContent());

        verify(events).publishPdfReady(new PdfReadyEvent(HEALER_ID, CASE_ID, "job-1"));
        verify(autoSubmit).onPdfCompleted("job-1");
    }

    @Test
    void webhook_stillAnswers204_whenPublishingFails() throws Exception {
        when(jobCases.resolve("job-1")).thenReturn(Optional.of(new PdfJobCaseResolver.JobCase(CASE_ID, HEALER_ID)));
        doThrow(new IllegalStateException("redis down")).when(events).publishPdfReady(any());

        mockMvc.perform(post(PATH).header("X-Internal-Token", "s3cret")).andExpect(status().isNoContent());

        verify(autoSubmit).onPdfCompleted("job-1");
    }

    @Test
    void webhook_unknownJob_publishesNothing() throws Exception {
        when(jobCases.resolve("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(post("/internal/pdf-jobs/ghost/completed").header("X-Internal-Token", "s3cret"))
                .andExpect(status().isNoContent());

        verify(events, never()).publishPdfReady(any());
    }
}
