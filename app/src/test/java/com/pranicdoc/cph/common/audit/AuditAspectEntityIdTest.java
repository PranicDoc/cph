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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for AuditAspect's entityId resolution fallback.
 *
 * Every DTO in the consent module (ConsentResponse, PolicyVersionResponse) is a
 * Java record, whose component accessor is id() rather than getId(). Before the
 * fix, the return-value fallback only tried getId() via reflection, so any audited
 * method returning a record left AuditLog.entityId permanently null (AUDIT-03/D-05).
 */
@ExtendWith(MockitoExtension.class)
class AuditAspectEntityIdTest {

    @Mock private AuditLogWriter auditLogWriter;
    @Mock private SecurityContextUtil securityContextUtil;
    @Mock private HttpServletRequest httpServletRequest;
    @Mock private ProceedingJoinPoint joinPoint;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AuditAspect auditAspect;

    /** Record shape matching every consent-module DTO: id() accessor, not getId(). */
    private record SampleRecordDto(UUID id, String name) {}

    /** Return value with neither getId() nor id() — must resolve to null, no throw. */
    private record NoIdShapeDto(String name) {}

    /** id-shaped accessor present but returns a non-UUID value. */
    private record NonUuidIdDto(String id) {}

    @BeforeEach
    void setUp() {
        auditAspect = new AuditAspect(
                auditLogWriter, securityContextUtil, httpServletRequest, objectMapper);

        lenient().when(joinPoint.getTarget()).thenReturn(new Object());

    }

    @Test
    void recordReturnValue_entityIdResolvedFromRecordAccessor() throws Throwable {
        UUID expectedId = UUID.randomUUID();
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn(new SampleRecordDto(expectedId, "some-name"));
        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("PUBLISH_POLICY");

        auditAspect.audit(joinPoint, auditable);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogWriter).enqueue(captor.capture());
        assertEquals(expectedId, captor.getValue().getEntityId());
    }

    @Test
    void uuidArgument_entityIdResolvedFromArgument_ignoresReturnValue() throws Throwable {
        UUID argId = UUID.randomUUID();
        UUID returnValueId = UUID.randomUUID();
        when(joinPoint.getArgs()).thenReturn(new Object[]{argId});
        when(joinPoint.proceed()).thenReturn(new SampleRecordDto(returnValueId, "other-name"));
        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("WITHDRAW_CONSENT");

        auditAspect.audit(joinPoint, auditable);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogWriter).enqueue(captor.capture());
        assertEquals(argId, captor.getValue().getEntityId());
    }

    @Test
    void returnValueWithNoIdAccessor_entityIdNullAndDoesNotThrow() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn(new NoIdShapeDto("no-id-here"));
        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("SOME_ACTION");

        assertDoesNotThrow(() -> auditAspect.audit(joinPoint, auditable));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogWriter).enqueue(captor.capture());
        assertNull(captor.getValue().getEntityId());
    }

    @Test
    void idAccessorReturnsNonUuid_entityIdNullAndDoesNotThrow() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn(new NonUuidIdDto("not-a-uuid"));
        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("SOME_ACTION");

        assertDoesNotThrow(() -> auditAspect.audit(joinPoint, auditable));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogWriter).enqueue(captor.capture());
        assertNull(captor.getValue().getEntityId());
    }
}
