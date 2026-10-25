package com.pranicdoc.cph.rbac.service;

import com.pranicdoc.cph.common.dto.PageResponse;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.exception.SelfLockoutException;
import com.pranicdoc.cph.rbac.dto.ResourceGrantDto;
import com.pranicdoc.cph.rbac.dto.RoleCreateRequest;
import com.pranicdoc.cph.rbac.dto.RoleResponse;
import com.pranicdoc.cph.rbac.dto.RoleSummaryResponse;
import com.pranicdoc.cph.rbac.dto.RoleUpdateRequest;
import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.RbacRole;
import com.pranicdoc.cph.rbac.model.Resource;
import com.pranicdoc.cph.rbac.model.RolePermission;
import com.pranicdoc.cph.rbac.model.UserRole;
import com.pranicdoc.cph.rbac.repository.RbacRoleRepository;
import com.pranicdoc.cph.rbac.repository.RolePermissionRepository;
import com.pranicdoc.cph.rbac.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RbacRoleServiceTest {

    @Mock private RbacRoleRepository rbacRoleRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private RoleManagerGuard roleManagerGuard;

    @InjectMocks private RbacRoleService rbacRoleService;

    // --- fixtures ---

    private RbacRole existingRole(String name) {
        RbacRole role = RbacRole.builder().name(name).description("desc").build();
        role.setId(UUID.randomUUID());
        role.setUpdatedAt(java.time.Instant.parse("2026-08-01T12:00:00Z"));
        return role;
    }

    private void stubRoleSaveAssignsRandomId() {
        lenient().when(rbacRoleRepository.save(any(RbacRole.class))).thenAnswer(inv -> {
            RbacRole r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            return r;
        });
    }

    private void stubGrantSaveAllReturnsInput() {
        lenient().when(rolePermissionRepository.saveAll(any())).thenAnswer(inv -> {
            List<RolePermission> rows = inv.getArgument(0);
            rows.forEach(r -> {
                if (r.getId() == null) {
                    r.setId(UUID.randomUUID());
                }
            });
            return rows;
        });
    }

    // --- create ---

    @Test
    void create_persistsRoleAndOneGrantRowPerPair() {
        stubRoleSaveAssignsRandomId();
        stubGrantSaveAllReturnsInput();
        when(rbacRoleRepository.existsByNameIgnoreCase("Billing Ops")).thenReturn(false);

        ArgumentCaptor<RbacRole> roleCaptor = ArgumentCaptor.forClass(RbacRole.class);
        ArgumentCaptor<List<RolePermission>> grantsCaptor = ArgumentCaptor.forClass(List.class);

        RoleCreateRequest request = new RoleCreateRequest(
                "Billing Ops", "desc",
                List.of(new ResourceGrantDto("ROLES", List.of("READ", "CREATE"))));

        RoleResponse response = rbacRoleService.create(request);

        verify(rbacRoleRepository).save(roleCaptor.capture());
        assertEquals("Billing Ops", roleCaptor.getValue().getName());
        assertEquals("desc", roleCaptor.getValue().getDescription());

        verify(rolePermissionRepository).saveAll(grantsCaptor.capture());
        List<RolePermission> savedGrants = grantsCaptor.getValue();
        assertEquals(2, savedGrants.size());
        assertTrue(savedGrants.stream().allMatch(g -> g.getRoleId().equals(roleCaptor.getValue().getId())));
        assertTrue(savedGrants.stream().anyMatch(
                g -> g.getResource() == Resource.ROLES && g.getOperation() == Operation.READ));
        assertTrue(savedGrants.stream().anyMatch(
                g -> g.getResource() == Resource.ROLES && g.getOperation() == Operation.CREATE));

        assertEquals(2, response.grants().get(0).operations().size());
    }

    @Test
    void create_duplicateNameIgnoringCase_throwsAndNeverSaves() {
        when(rbacRoleRepository.existsByNameIgnoreCase("billing ops")).thenReturn(true);

        RoleCreateRequest request = new RoleCreateRequest(
                "billing ops", "desc",
                List.of(new ResourceGrantDto("ROLES", List.of("READ"))));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> rbacRoleService.create(request));

        assertEquals("A role with that name already exists. Please choose a different name.", ex.getMessage());
        verify(rbacRoleRepository, never()).save(any());
        verify(rolePermissionRepository, never()).saveAll(any());
    }

    @Test
    void create_usesCaseInsensitiveCheckNotCaseSensitiveOne() {
        stubRoleSaveAssignsRandomId();
        stubGrantSaveAllReturnsInput();
        when(rbacRoleRepository.existsByNameIgnoreCase(anyString())).thenReturn(false);

        RoleCreateRequest request = new RoleCreateRequest(
                "Billing Ops", "desc",
                List.of(new ResourceGrantDto("ROLES", List.of("READ"))));

        rbacRoleService.create(request);

        verify(rbacRoleRepository).existsByNameIgnoreCase(anyString());
        verify(rbacRoleRepository, never()).existsByName(anyString());
    }

    @Test
    void create_unsupportedOperationForResource_throwsAndNeverSaves() {
        RoleCreateRequest request = new RoleCreateRequest(
                "Billing Ops", "desc",
                List.of(new ResourceGrantDto("ROLES", List.of("WRITE"))));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> rbacRoleService.create(request));

        assertTrue(ex.getMessage().contains("WRITE"));
        assertTrue(ex.getMessage().contains("ROLES"));
        verify(rbacRoleRepository, never()).save(any());
        verify(rolePermissionRepository, never()).saveAll(any());
    }

    @Test
    void create_unknownResourceLiteral_throwsAndNeverSaves() {
        RoleCreateRequest request = new RoleCreateRequest(
                "Billing Ops", "desc",
                List.of(new ResourceGrantDto("NOT_A_RESOURCE", List.of("READ"))));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> rbacRoleService.create(request));

        assertTrue(ex.getMessage().contains("NOT_A_RESOURCE"));
        verify(rbacRoleRepository, never()).save(any());
        verify(rolePermissionRepository, never()).saveAll(any());
    }

    @Test
    void create_unknownOperationLiteral_throwsAndNeverSaves() {
        RoleCreateRequest request = new RoleCreateRequest(
                "Billing Ops", "desc",
                List.of(new ResourceGrantDto("ROLES", List.of("FROBNICATE"))));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> rbacRoleService.create(request));

        assertTrue(ex.getMessage().contains("FROBNICATE"));
        verify(rbacRoleRepository, never()).save(any());
        verify(rolePermissionRepository, never()).saveAll(any());
    }

    // --- update ---

    @Test
    void update_replacesGrantSetEntirely() {
        RbacRole role = existingRole("Billing Ops");
        when(rbacRoleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        when(rbacRoleRepository.save(any(RbacRole.class))).thenAnswer(inv -> inv.getArgument(0));
        stubGrantSaveAllReturnsInput();

        RolePermission existingRead = RolePermission.builder()
                .roleId(role.getId()).resource(Resource.ROLES).operation(Operation.READ).build();
        RolePermission existingDelete = RolePermission.builder()
                .roleId(role.getId()).resource(Resource.ROLES).operation(Operation.DELETE).build();
        when(rolePermissionRepository.findAllByRoleId(role.getId()))
                .thenReturn(List.of(existingRead, existingDelete));

        RoleUpdateRequest request = new RoleUpdateRequest(
                "Billing Ops", "desc",
                List.of(new ResourceGrantDto("ROLES", List.of("UPDATE"))));

        ArgumentCaptor<List<RolePermission>> deleteCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<RolePermission>> saveCaptor = ArgumentCaptor.forClass(List.class);

        rbacRoleService.update(request, role.getId());

        verify(rolePermissionRepository).deleteAll(deleteCaptor.capture());
        assertEquals(List.of(existingRead, existingDelete), deleteCaptor.getValue());

        verify(rolePermissionRepository).saveAll(saveCaptor.capture());
        List<RolePermission> saved = saveCaptor.getValue();
        assertEquals(1, saved.size());
        assertEquals(Resource.ROLES, saved.get(0).getResource());
        assertEquals(Operation.UPDATE, saved.get(0).getOperation());
    }

    /**
     * Regression test for the 2026-08-26 finding during 36-05's live-verification
     * re-run: every real HTTP PUT that kept an already-active (resource, operation)
     * pair on the same role failed with a 409 (uq_role_permission violation).
     * Hibernate's default flush action-queue order runs queued INSERTs before
     * queued DELETEs within a single flush, regardless of the order these
     * repository calls happen in Java — so without an explicit flush() between
     * deleteAll(outgoing) and saveAll(incoming), the new grant row's INSERT hits
     * the DB before the old row's DELETE does, colliding on the unique
     * (role_id, resource, operation) constraint.
     * <p>
     * Mockito alone can't observe real Hibernate flush timing — a mocked
     * repository has no action queue to reorder — so this test only proves the
     * call *order* (deleteAll -> flush -> saveAll), which is what the fix
     * actually depends on. The real regression was caught live against Postgres,
     * not by a unit test; see PHASE-36-LIVE-VERIFICATION-RESULTS.md.
     */
    @Test
    void update_flushesDeleteBeforeSavingIncomingGrants() {
        RbacRole role = existingRole("Billing Ops");
        when(rbacRoleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        when(rbacRoleRepository.save(any(RbacRole.class))).thenAnswer(inv -> inv.getArgument(0));
        stubGrantSaveAllReturnsInput();

        RolePermission existingRead = RolePermission.builder()
                .roleId(role.getId()).resource(Resource.ROLES).operation(Operation.READ).build();
        when(rolePermissionRepository.findAllByRoleId(role.getId()))
                .thenReturn(List.of(existingRead));

        // Same (resource, operation) pair as the existing grant -- the exact
        // shape that triggered the real 409.
        RoleUpdateRequest request = new RoleUpdateRequest(
                "Billing Ops", "desc",
                List.of(new ResourceGrantDto("ROLES", List.of("READ"))));

        rbacRoleService.update(request, role.getId());

        InOrder order = inOrder(rolePermissionRepository);
        order.verify(rolePermissionRepository).deleteAll(any());
        order.verify(rolePermissionRepository).flush();
        order.verify(rolePermissionRepository).saveAll(any());
    }

    @Test
    void update_keepingOwnNameDoesNotTripUniquenessCheck() {
        RbacRole role = existingRole("Billing Ops");
        when(rbacRoleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        when(rbacRoleRepository.save(any(RbacRole.class))).thenAnswer(inv -> inv.getArgument(0));
        // Stubbed to return true to prove the self-name case is exempted from ever consulting this.
        lenient().when(rbacRoleRepository.existsByNameIgnoreCase(anyString())).thenReturn(true);
        when(rolePermissionRepository.findAllByRoleId(role.getId())).thenReturn(List.of());
        stubGrantSaveAllReturnsInput();

        RoleUpdateRequest request = new RoleUpdateRequest(
                "Billing Ops", "desc",
                List.of(new ResourceGrantDto("ROLES", List.of("READ"))));

        RoleResponse response = rbacRoleService.update(request, role.getId());

        assertEquals("Billing Ops", response.name());
    }

    @Test
    void update_renamingToAnotherExistingNameThrows() {
        RbacRole role = existingRole("Billing Ops");
        when(rbacRoleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        when(rbacRoleRepository.existsByNameIgnoreCase("content reviewer")).thenReturn(true);

        RoleUpdateRequest request = new RoleUpdateRequest(
                "content reviewer", "desc",
                List.of(new ResourceGrantDto("ROLES", List.of("READ"))));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> rbacRoleService.update(request, role.getId()));

        assertEquals("A role with that name already exists. Please choose a different name.", ex.getMessage());
        verify(rbacRoleRepository, never()).save(any());
        verify(rolePermissionRepository, never()).saveAll(any());
    }

    @Test
    void update_unknownId_throwsResourceNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(rbacRoleRepository.findById(unknownId)).thenReturn(Optional.empty());

        RoleUpdateRequest request = new RoleUpdateRequest(
                "Billing Ops", "desc",
                List.of(new ResourceGrantDto("ROLES", List.of("READ"))));

        assertThrows(ResourceNotFoundException.class,
                () -> rbacRoleService.update(request, unknownId));
    }

    // --- update: self-lockout guard (RBAC-05, D-01) ---

    /**
     * A role that currently grants manage-roles, edited to a grant set that no
     * longer includes it, while it is the last holder: the guard throws and
     * NOTHING is written — save, deleteAll and saveAll are each never called,
     * proving a blocked edit leaves the role and its grant rows byte-identical.
     */
    @Test
    void update_strippingLastManageRolesGrant_throwsSelfLockoutAndNeverWrites() {
        RbacRole role = existingRole("Role Admins");
        when(rbacRoleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        when(roleManagerGuard.roleGrantsRoleManagement(role.getId())).thenReturn(true);
        when(roleManagerGuard.grantSetIncludesRoleManagement(any())).thenReturn(false);
        when(userRoleRepository.countByRoleId(role.getId())).thenReturn(1L);
        doThrow(new SelfLockoutException())
                .when(roleManagerGuard).assertActionPreservesRoleManagement(anyBoolean(), anyLong());

        RoleUpdateRequest request = new RoleUpdateRequest(
                "Role Admins", "desc",
                List.of(new ResourceGrantDto("ROLES", List.of("READ"))));

        assertThrows(SelfLockoutException.class, () -> rbacRoleService.update(request, role.getId()));

        verify(rbacRoleRepository, never()).save(any());
        verify(rolePermissionRepository, never()).deleteAll(any());
        verify(rolePermissionRepository, never()).saveAll(any());
    }

    /**
     * Same scenario, but a survivor remains so the guard returns normally: the
     * update completes and the grant set is replaced exactly as it would be
     * without the guard.
     */
    @Test
    void update_strippingManageRolesGrantWithSurvivorRemaining_completesNormally() {
        RbacRole role = existingRole("Role Admins");
        when(rbacRoleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        when(rbacRoleRepository.save(any(RbacRole.class))).thenAnswer(inv -> inv.getArgument(0));
        stubGrantSaveAllReturnsInput();
        when(roleManagerGuard.roleGrantsRoleManagement(role.getId())).thenReturn(true);
        when(roleManagerGuard.grantSetIncludesRoleManagement(any())).thenReturn(false);
        when(userRoleRepository.countByRoleId(role.getId())).thenReturn(1L);

        RolePermission existingUpdate = RolePermission.builder()
                .roleId(role.getId()).resource(Resource.ROLES).operation(Operation.UPDATE).build();
        when(rolePermissionRepository.findAllByRoleId(role.getId())).thenReturn(List.of(existingUpdate));

        RoleUpdateRequest request = new RoleUpdateRequest(
                "Role Admins", "desc",
                List.of(new ResourceGrantDto("ROLES", List.of("READ"))));

        ArgumentCaptor<List<RolePermission>> saveCaptor = ArgumentCaptor.forClass(List.class);

        rbacRoleService.update(request, role.getId());

        verify(roleManagerGuard).assertActionPreservesRoleManagement(true, 1L);
        verify(rolePermissionRepository).saveAll(saveCaptor.capture());
        List<RolePermission> saved = saveCaptor.getValue();
        assertEquals(1, saved.size());
        assertEquals(Operation.READ, saved.get(0).getOperation());
    }

    /**
     * A role that grants manage-roles, edited with a grant set that STILL
     * includes it: the count is never consulted (PD-37-11).
     */
    @Test
    void update_keepingManageRolesGrant_neverConsultsGuardCount() {
        RbacRole role = existingRole("Role Admins");
        when(rbacRoleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        when(rbacRoleRepository.save(any(RbacRole.class))).thenAnswer(inv -> inv.getArgument(0));
        stubGrantSaveAllReturnsInput();
        when(roleManagerGuard.roleGrantsRoleManagement(role.getId())).thenReturn(true);
        when(roleManagerGuard.grantSetIncludesRoleManagement(any())).thenReturn(true);
        when(rolePermissionRepository.findAllByRoleId(role.getId())).thenReturn(List.of());

        RoleUpdateRequest request = new RoleUpdateRequest(
                "Role Admins", "desc",
                List.of(new ResourceGrantDto("ROLES", List.of("UPDATE"))));

        rbacRoleService.update(request, role.getId());

        verify(roleManagerGuard, never()).assertActionPreservesRoleManagement(anyBoolean(), anyLong());
        verify(userRoleRepository, never()).countByRoleId(any());
    }

    /**
     * A role that does NOT currently grant manage-roles, edited to any grant
     * set: the guard's count is never consulted.
     */
    @Test
    void update_roleWithoutManageRolesGrant_neverConsultsGuardCount() {
        RbacRole role = existingRole("Billing Ops");
        when(rbacRoleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        when(rbacRoleRepository.save(any(RbacRole.class))).thenAnswer(inv -> inv.getArgument(0));
        stubGrantSaveAllReturnsInput();
        when(roleManagerGuard.roleGrantsRoleManagement(role.getId())).thenReturn(false);
        when(rolePermissionRepository.findAllByRoleId(role.getId())).thenReturn(List.of());

        RoleUpdateRequest request = new RoleUpdateRequest(
                "Billing Ops", "desc",
                List.of(new ResourceGrantDto("ROLES", List.of("READ"))));

        rbacRoleService.update(request, role.getId());

        verify(roleManagerGuard, never()).grantSetIncludesRoleManagement(any());
        verify(roleManagerGuard, never()).assertActionPreservesRoleManagement(anyBoolean(), anyLong());
        verify(userRoleRepository, never()).countByRoleId(any());
    }

    /**
     * When the guard IS consulted, the pairs-about-to-be-removed argument it
     * receives is exactly {@code userRoleRepository.countByRoleId(id)} for the
     * role being edited.
     */
    @Test
    void update_whenGuarded_passesCountByRoleIdForThisRoleAsPairsToRemove() {
        RbacRole role = existingRole("Role Admins");
        when(rbacRoleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        when(rbacRoleRepository.save(any(RbacRole.class))).thenAnswer(inv -> inv.getArgument(0));
        stubGrantSaveAllReturnsInput();
        when(roleManagerGuard.roleGrantsRoleManagement(role.getId())).thenReturn(true);
        when(roleManagerGuard.grantSetIncludesRoleManagement(any())).thenReturn(false);
        when(userRoleRepository.countByRoleId(role.getId())).thenReturn(4L);
        when(rolePermissionRepository.findAllByRoleId(role.getId())).thenReturn(List.of());

        RoleUpdateRequest request = new RoleUpdateRequest(
                "Role Admins", "desc",
                List.of(new ResourceGrantDto("ROLES", List.of("READ"))));

        rbacRoleService.update(request, role.getId());

        ArgumentCaptor<Long> pairsCaptor = ArgumentCaptor.forClass(Long.class);
        verify(roleManagerGuard).assertActionPreservesRoleManagement(eq(true), pairsCaptor.capture());
        assertEquals(4L, pairsCaptor.getValue());
    }

    // --- delete ---

    @Test
    void delete_softDeletesRoleAndItsGrants() {
        RbacRole role = existingRole("Billing Ops");
        when(rbacRoleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        when(rbacRoleRepository.save(any(RbacRole.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rolePermissionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        RolePermission grantOne = RolePermission.builder()
                .roleId(role.getId()).resource(Resource.ROLES).operation(Operation.READ).build();
        RolePermission grantTwo = RolePermission.builder()
                .roleId(role.getId()).resource(Resource.ROLES).operation(Operation.DELETE).build();
        when(rolePermissionRepository.findAllByRoleId(role.getId())).thenReturn(List.of(grantOne, grantTwo));

        ArgumentCaptor<RbacRole> roleCaptor = ArgumentCaptor.forClass(RbacRole.class);
        ArgumentCaptor<List<RolePermission>> grantsCaptor = ArgumentCaptor.forClass(List.class);

        rbacRoleService.delete(role.getId());

        verify(rbacRoleRepository).save(roleCaptor.capture());
        assertTrue(roleCaptor.getValue().isDeleted());

        verify(rolePermissionRepository).saveAll(grantsCaptor.capture());
        assertTrue(grantsCaptor.getValue().stream().allMatch(RolePermission::isDeleted));

        verify(rbacRoleRepository, never()).delete(any());
        verify(rbacRoleRepository, never()).deleteById(any());
    }

    // --- delete: self-lockout guard (RBAC-05, D-01) ---

    /**
     * Deleting a role that grants manage-roles and holds the last assignment
     * pairs: the guard throws and nothing is soft-deleted — neither
     * rolePermissionRepository.saveAll nor rbacRoleRepository.save is called.
     */
    @Test
    void delete_lastManageRolesRole_throwsSelfLockoutAndSoftDeletesNothing() {
        RbacRole role = existingRole("Role Admins");
        when(rbacRoleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        when(roleManagerGuard.roleGrantsRoleManagement(role.getId())).thenReturn(true);
        when(userRoleRepository.countByRoleId(role.getId())).thenReturn(1L);
        doThrow(new SelfLockoutException())
                .when(roleManagerGuard).assertActionPreservesRoleManagement(anyBoolean(), anyLong());

        assertThrows(SelfLockoutException.class, () -> rbacRoleService.delete(role.getId()));

        verify(rolePermissionRepository, never()).saveAll(any());
        verify(rbacRoleRepository, never()).save(any());
        verify(rolePermissionRepository, never()).findAllByRoleId(any());
    }

    /**
     * Same delete with a survivor remaining (guard returns normally): the role
     * and its grants are soft-deleted exactly as the pre-existing behavioural
     * test asserts.
     */
    @Test
    void delete_manageRolesRoleWithSurvivorRemaining_softDeletesRoleAndGrants() {
        RbacRole role = existingRole("Role Admins");
        when(rbacRoleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        when(rbacRoleRepository.save(any(RbacRole.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rolePermissionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(roleManagerGuard.roleGrantsRoleManagement(role.getId())).thenReturn(true);
        when(userRoleRepository.countByRoleId(role.getId())).thenReturn(2L);

        RolePermission grant = RolePermission.builder()
                .roleId(role.getId()).resource(Resource.ROLES).operation(Operation.UPDATE).build();
        when(rolePermissionRepository.findAllByRoleId(role.getId())).thenReturn(List.of(grant));

        rbacRoleService.delete(role.getId());

        verify(roleManagerGuard).assertActionPreservesRoleManagement(true, 2L);
        verify(rbacRoleRepository).save(role);
        assertTrue(role.isDeleted());
        assertTrue(grant.isDeleted());
    }

    /**
     * Deleting a role that does NOT grant manage-roles: the guard is still
     * called (unconditionally, PD-37-12) but with {@code false} as its first
     * argument, and the delete completes.
     */
    @Test
    void delete_roleWithoutManageRolesGrant_callsGuardWithFalseAndCompletes() {
        RbacRole role = existingRole("Billing Ops");
        when(rbacRoleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        when(rbacRoleRepository.save(any(RbacRole.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rolePermissionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(roleManagerGuard.roleGrantsRoleManagement(role.getId())).thenReturn(false);
        when(userRoleRepository.countByRoleId(role.getId())).thenReturn(3L);
        when(rolePermissionRepository.findAllByRoleId(role.getId())).thenReturn(List.of());

        rbacRoleService.delete(role.getId());

        verify(roleManagerGuard).assertActionPreservesRoleManagement(false, 3L);
        assertTrue(role.isDeleted());
    }

    /**
     * The pairs-about-to-be-removed argument equals
     * {@code userRoleRepository.countByRoleId(id)} for the role being deleted.
     */
    @Test
    void delete_passesCountByRoleIdForThisRoleAsPairsToRemove() {
        RbacRole role = existingRole("Role Admins");
        when(rbacRoleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        when(rbacRoleRepository.save(any(RbacRole.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rolePermissionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(roleManagerGuard.roleGrantsRoleManagement(role.getId())).thenReturn(true);
        when(userRoleRepository.countByRoleId(role.getId())).thenReturn(7L);
        when(rolePermissionRepository.findAllByRoleId(role.getId())).thenReturn(List.of());

        rbacRoleService.delete(role.getId());

        ArgumentCaptor<Long> pairsCaptor = ArgumentCaptor.forClass(Long.class);
        verify(roleManagerGuard).assertActionPreservesRoleManagement(eq(true), pairsCaptor.capture());
        assertEquals(7L, pairsCaptor.getValue());
    }

    /**
     * Deleting an unknown id still throws ResourceNotFoundException before the
     * guard is consulted at all.
     */
    @Test
    void delete_unknownId_throwsResourceNotFoundBeforeGuard() {
        UUID unknownId = UUID.randomUUID();
        when(rbacRoleRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> rbacRoleService.delete(unknownId));

        verify(roleManagerGuard, never()).assertActionPreservesRoleManagement(anyBoolean(), anyLong());
    }

    // --- list (server-side searched + paged, D-08) ---

    /**
     * The service forwards the caller's search string and Pageable verbatim to
     * the derived paged finder (never findAll()), preserves the per-row
     * resourceCount/userCount derivation inside the page map, and wraps the
     * result in a PageResponse whose totalElements comes from the repository
     * page — not recomputed from content.size().
     */
    @Test
    void list_delegatesSearchAndPageableToDerivedFinderAndPreservesPerRowCounts() {
        RbacRole role = existingRole("Billing Ops");
        Pageable pageable = PageRequest.of(0, 20);
        when(rbacRoleRepository.findByNameContainingIgnoreCase("bill", pageable))
                .thenReturn(new PageImpl<>(List.of(role), pageable, 1));
        when(rolePermissionRepository.findAllByRoleIdIn(List.of(role.getId()))).thenReturn(List.of(
                RolePermission.builder().roleId(role.getId())
                        .resource(Resource.ROLES).operation(Operation.READ).build(),
                RolePermission.builder().roleId(role.getId())
                        .resource(Resource.ROLES).operation(Operation.UPDATE).build()));
        when(userRoleRepository.findAllByRoleIdIn(List.of(role.getId()))).thenReturn(List.of(
                UserRole.builder().roleId(role.getId()).build(),
                UserRole.builder().roleId(role.getId()).build(),
                UserRole.builder().roleId(role.getId()).build()));

        ArgumentCaptor<String> searchCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        PageResponse<RoleSummaryResponse> result = rbacRoleService.list("bill", pageable);

        verify(rbacRoleRepository).findByNameContainingIgnoreCase(searchCaptor.capture(), pageableCaptor.capture());
        assertEquals("bill", searchCaptor.getValue());
        assertEquals(pageable, pageableCaptor.getValue());
        verify(rbacRoleRepository, never()).findAll();

        assertEquals(1, result.content().size());
        RoleSummaryResponse row = result.content().get(0);
        assertEquals("Billing Ops", row.name());
        assertEquals(1, row.resourceCount());   // two grants, one distinct resource
        assertEquals(3L, row.userCount());
        assertEquals(java.time.Instant.parse("2026-08-01T12:00:00Z"), row.updatedAt());
        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(1L, result.totalElements());
        assertTrue(result.last());
    }

    /**
     * totalElements/totalPages/last reflect the repository page's own totals,
     * not the size of the single page slice returned in content.
     */
    @Test
    void list_pageMetadataComesFromRepositoryPageNotContentSize() {
        RbacRole role = existingRole("Billing Ops");
        Pageable pageable = PageRequest.of(0, 20);
        when(rbacRoleRepository.findByNameContainingIgnoreCase("", pageable))
                .thenReturn(new PageImpl<>(List.of(role), pageable, 57));
        PageResponse<RoleSummaryResponse> result = rbacRoleService.list("", pageable);

        assertEquals(1, result.content().size());
        assertEquals(57L, result.totalElements());
        assertEquals(3, result.totalPages());
        assertFalse(result.last());
    }

    // --- getById ---

    @Test
    void getById_unknownId_throwsResourceNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(rbacRoleRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> rbacRoleService.getById(unknownId));
    }
}
