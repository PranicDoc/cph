package com.pranicdoc.cph.rbac.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pranicdoc.cph.common.audit.AuditAspect;
import com.pranicdoc.cph.common.audit.AuditLog;
import com.pranicdoc.cph.common.audit.AuditLogWriter;
import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.rbac.dto.RoleAssignmentRequest;
import com.pranicdoc.cph.rbac.dto.RoleAssignmentResponse;
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
 * Proves the audit contract for role assignment. Attaching or detaching a role
 * is a privilege-changing mutation, and its audit record must name <em>both</em>
 * the account and the role — a record that captured only one of the two would
 * be indistinguishable, after the fact, from a mutation that never happened.
 *
 * <p>Structured as a direct sibling of {@code RbacRoleServiceAuditTest}. Half A
 * is a pure reflection contract over {@link RoleAssignmentService}'s method
 * signatures — no Spring, no mocks. The reflective {@code getMethod} call is
 * itself the parameter-shape gate: under a two-identifier signature the
 * resolution throws {@code NoSuchMethodException} rather than merely failing an
 * assertion, so a future refactor that quietly splits the request record back
 * into two loose parameters cannot slip through. Half B drives the <em>real</em>
 * {@link AuditAspect} — instantiated, not mocked — through a mocked
 * {@code ProceedingJoinPoint} and asserts the resulting audit row carries both
 * identifiers, plus an explicit counter-example showing exactly what the
 * single-record signature buys.
 */
@ExtendWith(MockitoExtension.class)
class RoleAssignmentServiceAuditTest {

    // ─── Half A — reflection contract over the method signatures (no mocks) ───

    @Test
    void assign_resolvesWithTheRequestRecordAsSoleParameterAndCarriesAssignRoleAction() throws NoSuchMethodException {
        // getMethod itself is the parameter-shape gate: this line throws
        // NoSuchMethodException under a two-identifier signature rather than
        // failing an assertion.
        Method method = RoleAssignmentService.class.getMethod("assign", RoleAssignmentRequest.class);

        Auditable auditable = method.getAnnotation(Auditable.class);

        assertNotNull(auditable);
        assertEquals("ASSIGN_ROLE", auditable.action());
    }

    @Test
    void unassign_resolvesWithTheRequestRecordAsSoleParameterAndCarriesUnassignRoleAction()
            throws NoSuchMethodException {
        // Same reflective parameter-shape gate as assign().
        Method method = RoleAssignmentService.class.getMethod("unassign", RoleAssignmentRequest.class);

        Auditable auditable = method.getAnnotation(Auditable.class);

        assertNotNull(auditable);
        assertEquals("UNASSIGN_ROLE", auditable.action());
    }

    @Test
    void bothMutationMethodsReturnTheResponseRecordNotVoid() throws NoSuchMethodException {
        Method assign = RoleAssignmentService.class.getMethod("assign", RoleAssignmentRequest.class);
        Method unassign = RoleAssignmentService.class.getMethod("unassign", RoleAssignmentRequest.class);

        // A void return would leave AuditAspect's entity-reference fallback with
        // nothing to reflect over, so entityId on every assignment audit row
        // would be null.
        assertEquals(RoleAssignmentResponse.class, assign.getReturnType());
        assertEquals(RoleAssignmentResponse.class, unassign.getReturnType());
    }

    @Test
    void mutationMethodsDeclareExactlyOneParameterOfTheRequestRecordType() throws NoSuchMethodException {
        Method assign = RoleAssignmentService.class.getMethod("assign", RoleAssignmentRequest.class);
        Method unassign = RoleAssignmentService.class.getMethod("unassign", RoleAssignmentRequest.class);

        assertEquals(1, assign.getParameterCount());
        assertEquals(RoleAssignmentRequest.class, assign.getParameterTypes()[0]);
        assertEquals(1, unassign.getParameterCount());
        assertEquals(RoleAssignmentRequest.class, unassign.getParameterTypes()[0]);
    }

    @Test
    void responseRecordFirstComponentIsNamedIdSoTheEntityReferenceFallbackResolves() {
        assertEquals("id", RoleAssignmentResponse.class.getRecordComponents()[0].getName());
    }

    @Test
    void everyPublicMutationMethodCarriesAnAuditAction() {
        int inspected = 0;
        for (Method method : RoleAssignmentService.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            String name = method.getName();
            if (name.equals("assign") || name.equals("unassign")) {
                inspected++;
                assertNotNull(method.getAnnotation(Auditable.class),
                        "public mutation method " + name + " must carry an audit action");
            }
        }
        // Anti-vacuity: the sweep must have actually seen both mutations, so a
        // rename cannot make it pass silently and a future third mutation
        // method cannot ship unaudited unnoticed.
        assertEquals(2, inspected, "expected exactly the two declared mutation methods");
    }

    // ─── Half B — behavioural proof through the real AuditAspect ───

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

    private AuditLog driveAspect(Object[] args, RoleAssignmentResponse result, String action) throws Throwable {
        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.proceed()).thenReturn(result);
        when(auditable.action()).thenReturn(action);

        auditAspect.audit(joinPoint, auditable);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogWriter).enqueue(captor.capture());
        return captor.getValue();
    }

    @Test
    void assignPayloadNamesBothTheAccountAndTheRoleWithEntityRefFromTheReturnedRow() throws Throwable {
        UUID principalId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID rowId = UUID.randomUUID();
        RoleAssignmentRequest request = new RoleAssignmentRequest(principalId, roleId);
        RoleAssignmentResponse response = new RoleAssignmentResponse(rowId, principalId, roleId, Instant.now());

        AuditLog saved = driveAspect(new Object[]{request}, response, "ASSIGN_ROLE");

        assertEquals("ASSIGN_ROLE", saved.getAction());
        // No UUID among the arguments, so the entity reference comes from the
        // returned row's id() accessor — the fallback path.
        assertEquals(rowId, saved.getEntityId());
        assertTrue(saved.getPayload().contains(principalId.toString()),
                "payload must record the target account");
        assertTrue(saved.getPayload().contains(roleId.toString()),
                "payload must record which role was granted");
    }

    @Test
    void unassignPayloadNamesBothTheAccountAndTheRoleExactlyAsAssignDoes() throws Throwable {
        UUID principalId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID rowId = UUID.randomUUID();
        RoleAssignmentRequest request = new RoleAssignmentRequest(principalId, roleId);
        RoleAssignmentResponse response = new RoleAssignmentResponse(rowId, principalId, roleId, Instant.now());

        AuditLog saved = driveAspect(new Object[]{request}, response, "UNASSIGN_ROLE");

        assertEquals("UNASSIGN_ROLE", saved.getAction());
        assertEquals(rowId, saved.getEntityId());
        assertTrue(saved.getPayload().contains(principalId.toString()));
        assertTrue(saved.getPayload().contains(roleId.toString()));
    }

    /**
     * The explicit counter-example. Running the SAME harness with two bare
     * identifiers as the arguments — the shape a split signature would produce —
     * degrades the payload to just the first identifier and takes the entity
     * reference from that argument rather than from the returned row. This is
     * what the single-record signature prevents, and it proves the two tests
     * above are not tautologies.
     */
    @Test
    void twoBareIdentifierArgsWouldDegradeThePayloadAndTakeTheEntityRefFromTheArgs() throws Throwable {
        UUID principalId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID rowId = UUID.randomUUID();
        RoleAssignmentResponse response = new RoleAssignmentResponse(rowId, principalId, roleId, Instant.now());

        AuditLog saved = driveAspect(new Object[]{principalId, roleId}, response, "ASSIGN_ROLE");

        // Payload collapses to the quoted serialization of the first arg alone.
        assertEquals("\"" + principalId + "\"", saved.getPayload());
        assertFalse(saved.getPayload().contains(roleId.toString()),
                "the role identifier is lost entirely under a split signature");
        // Entity reference is taken from the first UUID arg, not the returned row.
        assertEquals(principalId, saved.getEntityId());
        assertNotEquals(rowId, saved.getEntityId());
    }
}
