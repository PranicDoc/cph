package com.pranicdoc.cph.common.audit;

import com.pranicdoc.cph.common.enums.MaritalStatus;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Failures that happen before an @Auditable service method is entered (request-body
 * binding, controller-level exceptions) are invisible to AuditAspect; the resolver
 * must record which operation was attempted, by whom, and when.
 */
class RequestFailureAuditResolverTest {

    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final SecurityContextUtil securityContextUtil = mock(SecurityContextUtil.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

    private RequestFailureAuditResolver resolver;

    record Inner(MaritalStatus maritalStatus) {}
    record Outer(Inner patient) {}

    @RestController
    static class SampleController {
        @PostMapping("/api/things/{id}")
        void create(@RequestBody Outer body) {}
    }

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(mock(TransactionStatus.class));
        resolver = new RequestFailureAuditResolver(auditLogRepository, securityContextUtil, transactionManager);
    }

    private HandlerMethod handler() throws Exception {
        return new HandlerMethod(new SampleController(), SampleController.class.getDeclaredMethod("create", Outer.class));
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/things/123");
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                "/api/things/{id}");
        request.setRemoteAddr("10.1.2.3");
        return request;
    }

    @Test
    void recordsOperationWhoWhenAndWhy_andLeavesTheExceptionToTheNormalHandlers() throws Exception {
        UUID healerId = UUID.randomUUID();
        when(securityContextUtil.getCurrentHealerIdOrNull()).thenReturn(healerId);
        Instant before = Instant.now();

        Object result = resolver.resolveException(request(), new MockHttpServletResponse(), handler(),
                new IllegalStateException("boom"));

        assertNull(result, "must not swallow the exception — GlobalExceptionHandler still builds the response");
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertEquals("POST /api/things/{id}", saved.getAction());
        assertEquals("SampleController", saved.getEntityType());
        assertEquals(AuditStatus.FAILURE, saved.getStatus());
        assertEquals(healerId, saved.getHealerId());
        assertEquals("10.1.2.3", saved.getIpAddress());
        assertEquals("IllegalStateException: boom", saved.getMessage());
        assertNull(saved.getPayload(), "body may hold patient data and is not captured");
        assertFalse(saved.getPerformedAt().isBefore(before));
    }

    @Test
    void anonymousCaller_isRecordedWithNullHealer() throws Exception {
        when(securityContextUtil.getCurrentHealerIdOrNull()).thenThrow(new IllegalStateException("no auth"));

        resolver.resolveException(request(), new MockHttpServletResponse(), handler(), new RuntimeException("x"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertNull(captor.getValue().getHealerId());
    }

    @Test
    void failureAlreadyAuditedByAspect_isNotRecordedTwice() throws Exception {
        MockHttpServletRequest request = request();
        request.setAttribute(AuditAspect.AUDITED_ATTRIBUTE, Boolean.TRUE);

        resolver.resolveException(request, new MockHttpServletResponse(), handler(), new RuntimeException("x"));

        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void noHandlerMatched_isNotRecorded() {
        resolver.resolveException(request(), new MockHttpServletResponse(), null, new RuntimeException("x"));

        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void auditWriteFailure_neverPropagates() throws Exception {
        when(auditLogRepository.save(any())).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> resolver.resolveException(
                request(), new MockHttpServletResponse(), handler(), new RuntimeException("x")));
    }

    /**
     * The reported bug end to end: an unknown enum value fails during @RequestBody
     * binding, before the controller method (and any @Auditable service) runs.
     * Real resolver chain: audit resolver first, then GlobalExceptionHandler.
     */
    @Test
    void bindingFailureBeforeTheController_isAuditedAndStillReturns400() throws Exception {
        UUID healerId = UUID.randomUUID();
        when(securityContextUtil.getCurrentHealerIdOrNull()).thenReturn(healerId);

        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(GlobalExceptionHandler.class);
        ExceptionHandlerExceptionResolver advice = new ExceptionHandlerExceptionResolver();
        advice.setMessageConverters(List.of(new MappingJackson2HttpMessageConverter()));
        advice.setApplicationContext(ctx);
        advice.afterPropertiesSet();

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SampleController())
                .setHandlerExceptionResolvers(List.of(resolver, advice))
                .build();

        mockMvc.perform(post("/api/things/123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"patient\":{\"maritalStatus\":\"SEPARATED\"}}"))
                .andExpect(status().isBadRequest());

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertEquals("POST /api/things/{id}", saved.getAction());
        assertEquals(AuditStatus.FAILURE, saved.getStatus());
        assertEquals(healerId, saved.getHealerId());
        assertTrue(saved.getMessage().startsWith("HttpMessageNotReadableException"), saved.getMessage());
        ctx.close();
    }
}
