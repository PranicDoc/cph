package com.pranicdoc.cph.rbac.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pranicdoc.cph.common.audit.AuditAspect;
import com.pranicdoc.cph.common.audit.AuditLog;
import com.pranicdoc.cph.common.audit.AuditLogWriter;
import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.rbac.dto.ResourceGrantDto;
import com.pranicdoc.cph.rbac.dto.RoleCreateRequest;
import com.pranicdoc.cph.rbac.dto.RoleResponse;
import com.pranicdoc.cph.rbac.dto.RoleUpdateRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves AUDIT-01 for role mutations at two levels: a reflection contract
 * over {@link RbacRoleService}'s method signatures (no Spring, no Mockito),
 * and a behavioural pass through the real audit-recording mechanism showing
 * the resulting payload actually captures the submitted change.
 * <p>
 * Other mutation services in this codebase ship with a request-id parameter
 * order that reduces their audit payload to a bare identifier. This test
 * class exists because role mutations deliberately do not repeat that gap.
 */
@ExtendWith(MockitoExtension.class)
class RbacRoleServiceAuditTest {

    // --- Half A: reflection contract over RbacRoleService's method signatures ---

    @Test
    void create_isAuditableWithCreateRoleAction() throws NoSuchMethodException {
        Method method = RbacRoleService.class.getMethod("create", RoleCreateRequest.class);

        Auditable auditable = method.getAnnotation(Auditable.class);

        assertNotNull(auditable);
        assertEquals("CREATE_ROLE", auditable.action());
    }

    @Test
    void update_isAuditableWithUpdateRoleAction() throws NoSuchMethodException {
        // getMethod itself is the parameter-order gate: it resolves only if the
        // request payload is declared as the first parameter and the identifier
        // as the second. A reordering back to identifier-first would make this
        // resolution throw NoSuchMethodException rather than merely fail an assertion.
        Method method = RbacRoleService.class.getMethod("update", RoleUpdateRequest.class, UUID.class);

        Auditable auditable = method.getAnnotation(Auditable.class);

        assertNotNull(auditable);
        assertEquals("UPDATE_ROLE", auditable.action());
    }

    @Test
    void delete_isAuditableWithDeleteRoleAction() throws NoSuchMethodException {
        Method method = RbacRoleService.class.getMethod("delete", UUID.class);

        Auditable auditable = method.getAnnotation(Auditable.class);

        assertNotNull(auditable);
        assertEquals("DELETE_ROLE", auditable.action());
    }

    @Test
    void mutationMethodsPlaceTheRequestDtoFirst() throws NoSuchMethodException {
        Method createMethod = RbacRoleService.class.getMethod("create", RoleCreateRequest.class);
        Method updateMethod = RbacRoleService.class.getMethod("update", RoleUpdateRequest.class, UUID.class);

        // The audit mechanism serializes only the first method argument into the
        // audit payload — an identifier-first signature would silently reduce
        // that payload to a bare id.
        assertEquals(RoleCreateRequest.class, createMethod.getParameterTypes()[0],
                "create's first parameter must be the request payload, or the audit payload loses its content");
        assertEquals(RoleUpdateRequest.class, updateMethod.getParameterTypes()[0],
                "update's first parameter must be the request payload, or the audit payload loses its content");
    }

    @Test
    void everyPublicMutationMethodIsAudited() {
        for (Method method : RbacRoleService.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            String name = method.getName();
            if (name.equals("create") || name.equals("update") || name.equals("delete")) {
                assertNotNull(method.getAnnotation(Auditable.class),
                        "public mutation method " + name + " must carry an audit action");
            }
        }
    }

    // --- Half B: behavioural proof through the real AuditAspect ---

    @Mock private AuditLogWriter auditLogWriter;
    @Mock private SecurityContextUtil securityContextUtil;
    @Mock private HttpServletRequest httpServletRequest;
    @Mock private ProceedingJoinPoint joinPoint;
    @Mock private Auditable auditable;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private AuditAspect auditAspect;

    @BeforeEach
    void setUp() {
        auditAspect = new AuditAspect(
                auditLogWriter, securityContextUtil, httpServletRequest, objectMapper);

        lenient().when(joinPoint.getTarget()).thenReturn(new Object());
        // A Mockito mock of @Auditable returns false for auditPayload(); the real annotation
        // defaults to true, which is what the payload assertions below are about.
        lenient().when(auditable.auditPayload()).thenReturn(true);

    }

    private RoleResponse sampleRoleResponse(UUID roleId) {
        return new RoleResponse(
                roleId,
                "Billing Ops",
                "desc",
                List.of(new ResourceGrantDto("ROLES", List.of("READ", "UPDATE"))),
                Instant.now(),
                Instant.now());
    }

    /**
     * The gate this class exists to prove: submitting the request payload
     * first (matching the real update signature) yields an audit payload
     * that actually shows the submitted role name and grants, not merely
     * the row that changed.
     */
    @Test
    void updatePayloadCapturesTheSubmittedRoleNameAndGrants() throws Throwable {
        UUID roleId = UUID.randomUUID();
        RoleUpdateRequest request = new RoleUpdateRequest(
                "Billing Ops", "desc",
                List.of(new ResourceGrantDto("ROLES", List.of("READ", "UPDATE"))));

        when(joinPoint.getArgs()).thenReturn(new Object[]{request, roleId});
        when(joinPoint.proceed()).thenReturn(sampleRoleResponse(roleId));
        when(auditable.action()).thenReturn("UPDATE_ROLE");

        auditAspect.audit(joinPoint, auditable);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogWriter).enqueue(captor.capture());
        AuditLog saved = captor.getValue();

        assertEquals("UPDATE_ROLE", saved.getAction());
        assertEquals(roleId, saved.getEntityId());
        assertTrue(saved.getPayload().contains("Billing Ops"));
        assertTrue(saved.getPayload().contains("ROLES"));
        assertTrue(saved.getPayload().contains("READ"));
        assertTrue(saved.getPayload().contains("UPDATE"));
        assertNotEquals("\"" + roleId + "\"", saved.getPayload());
        assertTrue(saved.getPayload().length() > 40);
    }

    /**
     * The explicit counter-example: running the SAME harness with the
     * identifier submitted first shows exactly what the parameter-order
     * convention buys — the payload degrades to the bare serialized
     * identifier, proving test 6 above is not a tautology.
     */
    @Test
    void idFirstOrderWouldCapturedOnlyTheUuid() throws Throwable {
        UUID roleId = UUID.randomUUID();
        RoleUpdateRequest request = new RoleUpdateRequest(
                "Billing Ops", "desc",
                List.of(new ResourceGrantDto("ROLES", List.of("READ", "UPDATE"))));

        when(joinPoint.getArgs()).thenReturn(new Object[]{roleId, request});
        when(joinPoint.proceed()).thenReturn(sampleRoleResponse(roleId));
        when(auditable.action()).thenReturn("UPDATE_ROLE");

        auditAspect.audit(joinPoint, auditable);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogWriter).enqueue(captor.capture());
        AuditLog saved = captor.getValue();

        assertFalse(saved.getPayload().contains("Billing Ops"));
        assertFalse(saved.getPayload().contains("ROLES"));
    }
}
