package com.pranicdoc.cph.rbac.service;

import com.pranicdoc.cph.common.exception.SelfLockoutException;
import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Permission;
import com.pranicdoc.cph.rbac.model.Resource;
import com.pranicdoc.cph.rbac.model.RolePermission;
import com.pranicdoc.cph.rbac.repository.RolePermissionRepository;
import com.pranicdoc.cph.rbac.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleManagerGuardTest {

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @InjectMocks
    private RoleManagerGuard roleManagerGuard;

    private RolePermission grant(UUID roleId, Resource resource, Operation operation) {
        return RolePermission.builder()
                .roleId(roleId)
                .resource(resource)
                .operation(operation)
                .build();
    }

    // --- roleGrantsRoleManagement ---

    @Test
    void roleGrantsRoleManagement_roleHasRolesUpdateGrant_returnsTrue() {
        UUID roleId = UUID.randomUUID();
        when(rolePermissionRepository.findAllByRoleId(roleId))
                .thenReturn(List.of(grant(roleId, Resource.ROLES, Operation.READ),
                        grant(roleId, Resource.ROLES, Operation.UPDATE)));

        assertTrue(roleManagerGuard.roleGrantsRoleManagement(roleId));
    }

    @Test
    void roleGrantsRoleManagement_roleHasOtherRolesVerbsOnly_returnsFalse() {
        UUID roleId = UUID.randomUUID();
        when(rolePermissionRepository.findAllByRoleId(roleId))
                .thenReturn(List.of(grant(roleId, Resource.ROLES, Operation.READ),
                        grant(roleId, Resource.ROLES, Operation.DELETE)));

        assertFalse(roleManagerGuard.roleGrantsRoleManagement(roleId));
    }

    @Test
    void roleGrantsRoleManagement_roleHasNoGrants_returnsFalse() {
        UUID roleId = UUID.randomUUID();
        when(rolePermissionRepository.findAllByRoleId(roleId)).thenReturn(List.of());

        assertFalse(roleManagerGuard.roleGrantsRoleManagement(roleId));
    }

    // --- grantSetIncludesRoleManagement ---

    @Test
    void grantSetIncludesRoleManagement_listContainsRolesUpdate_returnsTrue() {
        List<Permission> permissions = List.of(
                new Permission(Resource.ROLES, Operation.READ),
                new Permission(Resource.ROLES, Operation.UPDATE));

        assertTrue(roleManagerGuard.grantSetIncludesRoleManagement(permissions));
    }

    @Test
    void grantSetIncludesRoleManagement_listHasReadAndDeleteButNotUpdate_returnsFalse() {
        List<Permission> permissions = List.of(
                new Permission(Resource.ROLES, Operation.READ),
                new Permission(Resource.ROLES, Operation.DELETE));

        assertFalse(roleManagerGuard.grantSetIncludesRoleManagement(permissions));
    }

    @Test
    void grantSetIncludesRoleManagement_emptyList_returnsFalse() {
        assertFalse(roleManagerGuard.grantSetIncludesRoleManagement(List.of()));
    }

    // --- countRoleManagerPairs ---

    @Test
    void countRoleManagerPairs_noRoleGrantsRolesUpdate_returnsZeroWithoutTouchingUserRoleRepository() {
        when(rolePermissionRepository.findAllByResourceAndOperation(Resource.ROLES, Operation.UPDATE))
                .thenReturn(List.of());

        assertEquals(0L, roleManagerGuard.countRoleManagerPairs());
        verifyNoInteractions(userRoleRepository);
    }

    @Test
    void countRoleManagerPairs_passesGrantingRoleIdsToCountByRoleIdIn_andReturnsItsResult() {
        UUID roleA = UUID.randomUUID();
        UUID roleB = UUID.randomUUID();
        when(rolePermissionRepository.findAllByResourceAndOperation(Resource.ROLES, Operation.UPDATE))
                .thenReturn(List.of(grant(roleA, Resource.ROLES, Operation.UPDATE),
                        grant(roleB, Resource.ROLES, Operation.UPDATE)));
        when(userRoleRepository.countByRoleIdIn(anyList())).thenReturn(7L);

        long result = roleManagerGuard.countRoleManagerPairs();

        assertEquals(7L, result);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
        verify(userRoleRepository).countByRoleIdIn(captor.capture());
        assertEquals(List.of(roleA, roleB), captor.getValue());
    }

    // --- assertActionPreservesRoleManagement ---

    @Test
    void assertActionPreservesRoleManagement_roleDoesNotGrantCapability_returnsWithoutRepositoryReads() {
        roleManagerGuard.assertActionPreservesRoleManagement(false, 5);

        verifyNoInteractions(rolePermissionRepository);
        verifyNoInteractions(userRoleRepository);
    }

    @Test
    void assertActionPreservesRoleManagement_removingZeroPairs_returnsWithoutRepositoryReads() {
        roleManagerGuard.assertActionPreservesRoleManagement(true, 0);

        verifyNoInteractions(rolePermissionRepository);
        verifyNoInteractions(userRoleRepository);
    }

    @Test
    void assertActionPreservesRoleManagement_oneToZero_throwsSelfLockout() {
        stubLiveTotal(1L);

        SelfLockoutException ex = assertThrows(SelfLockoutException.class,
                () -> roleManagerGuard.assertActionPreservesRoleManagement(true, 1));

        assertTrue(ex.getMessage().contains("leave nobody able to manage roles"));
    }

    @Test
    void assertActionPreservesRoleManagement_twoToOne_returnsNormally() {
        stubLiveTotal(2L);

        roleManagerGuard.assertActionPreservesRoleManagement(true, 1);
    }

    @Test
    void assertActionPreservesRoleManagement_bulkThreeToZero_throwsSelfLockout() {
        stubLiveTotal(3L);

        assertThrows(SelfLockoutException.class,
                () -> roleManagerGuard.assertActionPreservesRoleManagement(true, 3));
    }

    @Test
    void assertActionPreservesRoleManagement_bulkFourToOne_returnsNormally() {
        stubLiveTotal(4L);

        roleManagerGuard.assertActionPreservesRoleManagement(true, 3);
    }

    private void stubLiveTotal(long total) {
        UUID managerRole = UUID.randomUUID();
        when(rolePermissionRepository.findAllByResourceAndOperation(Resource.ROLES, Operation.UPDATE))
                .thenReturn(List.of(grant(managerRole, Resource.ROLES, Operation.UPDATE)));
        when(userRoleRepository.countByRoleIdIn(anyList())).thenReturn(total);
    }
}
