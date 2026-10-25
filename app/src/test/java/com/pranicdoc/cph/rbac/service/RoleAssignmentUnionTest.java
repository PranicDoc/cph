package com.pranicdoc.cph.rbac.service;

import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.rbac.dto.RoleAssignmentRequest;
import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;
import com.pranicdoc.cph.rbac.model.RolePermission;
import com.pranicdoc.cph.rbac.model.UserRole;
import com.pranicdoc.cph.rbac.repository.RbacRoleRepository;
import com.pranicdoc.cph.rbac.repository.RolePermissionRepository;
import com.pranicdoc.cph.rbac.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ASSIGN-04's non-tautological proof at the service seam CONTEXT.md D-03(a) names.
 *
 * <p>The union-of-roles behaviour itself shipped in Phase 36: {@code
 * RolePermissionLookupService} already unions grants across every role a
 * principal holds. What it never had, until plan 37-03, was a writer — the
 * assignment table held zero rows and no code could add one. This class is
 * where the two halves are finally wired together and observed: a <em>real</em>
 * {@link RoleAssignmentService} instance writes into a stateful store that a
 * <em>real</em> {@link RolePermissionLookupService} instance reads back, with a
 * <em>real</em> {@link RoleManagerGuard} sharing the same two repository fakes.
 *
 * <p>Doing this with ordinary per-call stubs would be a tautology — the
 * assertion would return whatever the stub was configured with, regardless of
 * whether {@code assign()} did anything at all. Instead the two Mockito mocks
 * for {@code UserRoleRepository} and {@code RolePermissionRepository} are
 * backed by two mutable {@link ArrayList}s: {@code assign()} genuinely appends,
 * {@code unassign()} genuinely removes, and every lookup genuinely re-reads
 * (PD-37-21). Only the pure existence / context collaborators
 * ({@code RbacRoleRepository}, {@code HealerRepository},
 * {@code SecurityContextUtil}) are flat stubs — they have no bearing on the
 * union behaviour (PD-37-22).
 *
 * <p>Per PD-37-23 this class carries <b>no</b> Mockito strict-stub JUnit
 * extension and builds every collaborator with {@code mock(X.class)} in
 * {@code @BeforeEach}: the stateful answers are registered once and exercised
 * unevenly across tests, which strict-stub mode would reject as unnecessary
 * stubbing. Per PD-37-24
 * every capability is asserted <em>absent</em> before the assignment that grants
 * it — without the before-state assertion a fake that returned {@code true}
 * unconditionally would still pass the after-state assertion.
 *
 * <p>This is a service-seam proof, not an end-to-end one. Nothing here
 * exercises the real Spring Security filter chain or a live database uniqueness
 * constraint — which is exactly why the phase-closing plan carries a blocking
 * live-verification gate.
 */
class RoleAssignmentUnionTest {

    private static final String HEALER = "HEALER";

    // Stateful stores shared by every real service under proof.
    private List<UserRole> assignmentStore;
    private List<RolePermission> grantStore;

    // Fixed fixture identifiers.
    private UUID testerId;
    private UUID roleAId;
    private UUID roleBId;
    private UUID assignerId;

    // Collaborators.
    private UserRoleRepository userRoleRepository;
    private RolePermissionRepository rolePermissionRepository;
    private RbacRoleRepository rbacRoleRepository;
    private HealerRepository healerRepository;
    private SecurityContextUtil securityContextUtil;

    // The three real instances.
    private RoleManagerGuard roleManagerGuard;
    private RoleAssignmentService roleAssignmentService;
    private RolePermissionLookupService lookupService;

    @BeforeEach
    void setUp() {
        assignmentStore = new ArrayList<>();
        grantStore = new ArrayList<>();

        testerId = UUID.randomUUID();
        roleAId = UUID.randomUUID();
        roleBId = UUID.randomUUID();
        assignerId = UUID.randomUUID();

        // Role A grants (PATIENT, READ) only; role B grants (SESSION, READ) only.
        grantStore.add(grant(roleAId, Resource.PATIENT, Operation.READ));
        grantStore.add(grant(roleBId, Resource.SESSION, Operation.READ));

        userRoleRepository = mock(UserRoleRepository.class);
        rolePermissionRepository = mock(RolePermissionRepository.class);
        rbacRoleRepository = mock(RbacRoleRepository.class);
        healerRepository = mock(HealerRepository.class);
        securityContextUtil = mock(SecurityContextUtil.class);

        // --- RolePermissionRepository: stateful reads over grantStore ---
        when(rolePermissionRepository.findAllByRoleId(any())).thenAnswer(inv -> {
            UUID roleId = inv.getArgument(0);
            return grantStore.stream().filter(rp -> rp.getRoleId().equals(roleId)).toList();
        });
        when(rolePermissionRepository.findAllByRoleIdIn(any())).thenAnswer(inv -> {
            java.util.Collection<UUID> roleIds = inv.getArgument(0);
            return grantStore.stream().filter(rp -> roleIds.contains(rp.getRoleId())).toList();
        });
        when(rolePermissionRepository.findAllByResourceAndOperation(any(), any())).thenAnswer(inv -> {
            Resource resource = inv.getArgument(0);
            Operation operation = inv.getArgument(1);
            return grantStore.stream()
                    .filter(rp -> rp.getResource() == resource && rp.getOperation() == operation)
                    .toList();
        });

        // --- UserRoleRepository: stateful reads + writes over assignmentStore ---
        when(userRoleRepository.save(any(UserRole.class))).thenAnswer(inv -> {
            UserRole ur = inv.getArgument(0);
            if (ur.getId() == null) {
                ur.setId(UUID.randomUUID());
            }
            assignmentStore.add(ur);
            return ur;
        });
        when(userRoleRepository.findAllByPrincipalTypeAndPrincipalId(any(), any())).thenAnswer(inv -> {
            String principalType = inv.getArgument(0);
            UUID principalId = inv.getArgument(1);
            return assignmentStore.stream()
                    .filter(ur -> ur.getPrincipalType().equals(principalType)
                            && ur.getPrincipalId().equals(principalId))
                    .toList();
        });
        when(userRoleRepository.findByPrincipalTypeAndPrincipalIdAndRoleId(any(), any(), any())).thenAnswer(inv -> {
            String principalType = inv.getArgument(0);
            UUID principalId = inv.getArgument(1);
            UUID roleId = inv.getArgument(2);
            return assignmentStore.stream()
                    .filter(ur -> ur.getPrincipalType().equals(principalType)
                            && ur.getPrincipalId().equals(principalId)
                            && ur.getRoleId().equals(roleId))
                    .findFirst();
        });
        when(userRoleRepository.countByRoleIdIn(any())).thenAnswer(inv -> {
            List<UUID> roleIds = inv.getArgument(0);
            return assignmentStore.stream().filter(ur -> roleIds.contains(ur.getRoleId())).count();
        });
        when(userRoleRepository.countByRoleId(any())).thenAnswer(inv -> {
            UUID roleId = inv.getArgument(0);
            return assignmentStore.stream().filter(ur -> ur.getRoleId().equals(roleId)).count();
        });
        doAnswer(inv -> {
            UserRole ur = inv.getArgument(0);
            assignmentStore.removeIf(row -> row.getId() != null && row.getId().equals(ur.getId()));
            return null;
        }).when(userRoleRepository).delete(any(UserRole.class));

        // --- pure existence / context stubs (no bearing on the union) ---
        when(rbacRoleRepository.existsById(any())).thenReturn(true);
        when(healerRepository.existsById(any())).thenReturn(true);
        when(securityContextUtil.getCurrentHealerIdOrNull()).thenReturn(assignerId);

        // --- the three real instances, all sharing the same two repository fakes ---
        roleManagerGuard = new RoleManagerGuard(rolePermissionRepository, userRoleRepository);
        roleAssignmentService = new RoleAssignmentService(
                userRoleRepository, rbacRoleRepository, healerRepository, roleManagerGuard, securityContextUtil);
        lookupService = new RolePermissionLookupService(userRoleRepository, rolePermissionRepository);
    }

    private static RolePermission grant(UUID roleId, Resource resource, Operation operation) {
        RolePermission rp = RolePermission.builder()
                .roleId(roleId)
                .resource(resource)
                .operation(operation)
                .build();
        rp.setId(UUID.randomUUID());
        return rp;
    }

    private boolean can(Resource resource, Operation operation) {
        return lookupService.hasPermission(testerId, resource, operation);
    }

    private void assign(UUID roleId) {
        roleAssignmentService.assign(new RoleAssignmentRequest(testerId, roleId));
    }

    private void unassign(UUID roleId) {
        roleAssignmentService.unassign(new RoleAssignmentRequest(testerId, roleId));
    }

    @Test
    void beforeAnyAssignment_lookupReportsNeitherRolesGrant() {
        assertFalse(can(Resource.PATIENT, Operation.READ));
        assertFalse(can(Resource.SESSION, Operation.READ));
    }

    @Test
    void afterAssigningRoleAOnly_hasPatientReadButNotSessionRead() {
        assertFalse(can(Resource.PATIENT, Operation.READ));
        assertFalse(can(Resource.SESSION, Operation.READ));

        assign(roleAId);

        assertTrue(can(Resource.PATIENT, Operation.READ));
        assertFalse(can(Resource.SESSION, Operation.READ));
    }

    @Test
    void userHoldingTwoRoles_hasAccessToTheUnionOfBothRolesGrants() {
        // PD-37-24: the negative is asserted before every positive.
        assertFalse(can(Resource.PATIENT, Operation.READ));
        assertFalse(can(Resource.SESSION, Operation.READ));

        assign(roleAId);
        assign(roleBId);

        // ASSIGN-04's core claim: the effective permission set is the union.
        assertTrue(can(Resource.PATIENT, Operation.READ));
        assertTrue(can(Resource.SESSION, Operation.READ));
    }

    @Test
    void assigningSecondRoleToUserAlreadyHoldingAnother_doesNotThrow() {
        assign(roleAId);

        assertDoesNotThrow(() -> assign(roleBId));
        assertEquals(2, assignmentStore.size());
    }

    @Test
    void assigningTheSameRoleTwice_throwsDuplicateAndLeavesTheStoreUnchanged() {
        assign(roleAId);
        assertEquals(1, assignmentStore.size());

        assertThrows(IllegalArgumentException.class, () -> assign(roleAId));

        assertEquals(1, assignmentStore.size());
    }

    @Test
    void detachingRoleAWhileRoleBRemains_narrowsToExactlyRoleBsGrantsOnTheNextCall() {
        assign(roleAId);
        assign(roleBId);
        assertTrue(can(Resource.PATIENT, Operation.READ));
        assertTrue(can(Resource.SESSION, Operation.READ));

        unassign(roleAId);

        // Live and scoped: observed on the very next call, no eviction step.
        assertFalse(can(Resource.PATIENT, Operation.READ));
        assertTrue(can(Resource.SESSION, Operation.READ));
    }

    @Test
    void reAssigningRoleAAfterDetachment_succeedsAndRestoresItsGrant() {
        assign(roleAId);
        unassign(roleAId);
        assertFalse(can(Resource.PATIENT, Operation.READ));

        assertDoesNotThrow(() -> assign(roleAId));

        assertTrue(can(Resource.PATIENT, Operation.READ));
    }

    @Test
    void lookupNeverReportsAVerbNeitherRoleGrants_atEveryStage() {
        assertFalse(can(Resource.PATIENT, Operation.DELETE));

        assign(roleAId);
        assertFalse(can(Resource.PATIENT, Operation.DELETE));

        assign(roleBId);
        assertFalse(can(Resource.PATIENT, Operation.DELETE));

        unassign(roleAId);
        assertFalse(can(Resource.PATIENT, Operation.DELETE));
    }

    @Test
    void endToEndSequence_assertsTheExpectedBooleanPairAtEveryStage() {
        // empty
        assertFalse(can(Resource.PATIENT, Operation.READ));
        assertFalse(can(Resource.SESSION, Operation.READ));

        // + A
        assign(roleAId);
        assertTrue(can(Resource.PATIENT, Operation.READ));
        assertFalse(can(Resource.SESSION, Operation.READ));

        // + B  → union
        assign(roleBId);
        assertTrue(can(Resource.PATIENT, Operation.READ));
        assertTrue(can(Resource.SESSION, Operation.READ));

        // - A  → narrowed to B's grants only
        unassign(roleAId);
        assertFalse(can(Resource.PATIENT, Operation.READ));
        assertTrue(can(Resource.SESSION, Operation.READ));

        // + A again → the removed pair is free for reuse
        assign(roleAId);
        assertTrue(can(Resource.PATIENT, Operation.READ));
        assertTrue(can(Resource.SESSION, Operation.READ));
    }

    @Test
    void duplicateRejectionIsScopedToThePairNotToHoldingAnyRole() {
        assign(roleAId);
        assign(roleBId);

        // Re-adding A collides; adding an unrelated role never would.
        assertThrows(IllegalArgumentException.class, () -> assign(roleAId));
        assertThrows(IllegalArgumentException.class, () -> assign(roleBId));
        assertEquals(2, assignmentStore.size());
    }
}
