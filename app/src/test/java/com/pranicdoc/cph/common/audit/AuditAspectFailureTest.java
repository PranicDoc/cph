package com.pranicdoc.cph.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for AUDIT-05: an exception out of an @Auditable method
 * (including whatever surfaces to the caller as a 500) must still produce an
 * audit_log row, not be silently dropped from the trail.
 */
@ExtendWith(MockitoExtension.class)
class AuditAspectFailureTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private SecurityContextUtil securityContextUtil;
    @Mock private HttpServletRequest httpServletRequest;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private ProceedingJoinPoint joinPoint;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AuditAspect auditAspect;

    private record SamplePayload(String consentType) {}

    @BeforeEach
    void setUp() {
        auditAspect = new AuditAspect(
                auditLogRepository, securityContextUtil, httpServletRequest, transactionManager, objectMapper);

        lenient().when(joinPoint.getTarget()).thenReturn(new Object());

        TransactionStatus status = mock(TransactionStatus.class);
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
    }

    @Test
    void proceedThrows_originalExceptionStillPropagates() throws Throwable {
        RuntimeException boom = new RuntimeException("db exploded");
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenThrow(boom);
        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("PUBLISH_POLICY");

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> auditAspect.audit(joinPoint, auditable));
        assertSame(boom, thrown);
    }

    @Test
    void proceedThrows_auditLogWrittenWithFailureStatusAndMessage() throws Throwable {
        RuntimeException boom = new RuntimeException("db exploded");
        SamplePayload payload = new SamplePayload("MEDICAL_CONSENT");
        when(joinPoint.getArgs()).thenReturn(new Object[]{payload});
        when(joinPoint.proceed()).thenThrow(boom);
        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("PUBLISH_POLICY");

        assertThrows(RuntimeException.class, () -> auditAspect.audit(joinPoint, auditable));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertEquals(AuditStatus.FAILURE, saved.getStatus());
        assertEquals("db exploded", saved.getMessage());
        assertEquals("PUBLISH_POLICY", saved.getAction());
        assertEquals(objectMapper.writeValueAsString(payload), saved.getPayload());
    }

    @Test
    void proceedThrows_entityIdResolvedFromUuidArgument_resultIsNull() throws Throwable {
        UUID argId = UUID.randomUUID();
        when(joinPoint.getArgs()).thenReturn(new Object[]{argId});
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("nope"));
        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("WITHDRAW_CONSENT");

        assertThrows(IllegalStateException.class, () -> auditAspect.audit(joinPoint, auditable));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertEquals(argId, captor.getValue().getEntityId());
    }

    @Test
    void proceedThrows_exceptionWithNullMessage_fallsBackToClassName() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenThrow(new NullPointerException());
        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("SOME_ACTION");

        assertThrows(NullPointerException.class, () -> auditAspect.audit(joinPoint, auditable));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertEquals("NullPointerException", captor.getValue().getMessage());
    }

    @Test
    void proceedSucceeds_auditLogWrittenWithSuccessStatus() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn("ok-result");
        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("SOME_ACTION");

        Object result = auditAspect.audit(joinPoint, auditable);

        assertEquals("ok-result", result);
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertEquals(AuditStatus.SUCCESS, captor.getValue().getStatus());
        assertEquals("Success", captor.getValue().getMessage());
    }

    @Test
    void auditWriteItselfFails_originalBusinessExceptionStillPropagates() throws Throwable {
        RuntimeException boom = new RuntimeException("db exploded");
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenThrow(boom);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenThrow(new RuntimeException("audit infra down"));
        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("SOME_ACTION");

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> auditAspect.audit(joinPoint, auditable));
        assertSame(boom, thrown);
    }
}
