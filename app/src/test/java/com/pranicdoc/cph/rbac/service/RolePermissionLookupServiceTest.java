package com.pranicdoc.cph.rbac.service;

import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;
import com.pranicdoc.cph.rbac.model.RolePermission;
import com.pranicdoc.cph.rbac.model.UserRole;
import com.pranicdoc.cph.rbac.repository.RolePermissionRepository;
import com.pranicdoc.cph.rbac.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RolePermissionLookupServiceTest {

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @InjectMocks
    private RolePermissionLookupService lookupService;

    private UserRole assignment(UUID healerId, UUID roleId) {
        return UserRole.builder()
                .principalType("HEALER")
                .principalId(healerId)
                .roleId(roleId)
                .assignedAt(Instant.now())
                .build();
    }

    private RolePermission grant(UUID roleId, Resource resource, Operation operation) {
        return RolePermission.builder()
                .roleId(roleId)
                .resource(resource)
                .operation(operation)
                .build();
    }

    @Test
    void grantedPairReturnsTrue() {
        UUID healerId = UUID.randomUUID();
        UUID roleA = UUID.randomUUID();
        when(userRoleRepository.findAllByPrincipalTypeAndPrincipalId("HEALER", healerId))
                .thenReturn(List.of(assignment(healerId, roleA)));
        when(rolePermissionRepository.findAllByRoleIdIn(List.of(roleA)))
                .thenReturn(List.of(grant(roleA, Resource.ROLES, Operation.CREATE)));

        assertTrue(lookupService.hasPermission(healerId, Resource.ROLES, Operation.CREATE));
    }

    @Test
    void ungrantedVerbOnGrantedResourceReturnsFalse() {
        UUID healerId = UUID.randomUUID();
        UUID roleA = UUID.randomUUID();
        when(userRoleRepository.findAllByPrincipalTypeAndPrincipalId("HEALER", healerId))
                .thenReturn(List.of(assignment(healerId, roleA)));
        when(rolePermissionRepository.findAllByRoleIdIn(List.of(roleA)))
                .thenReturn(List.of(grant(roleA, Resource.ROLES, Operation.READ)));

        assertFalse(lookupService.hasPermission(healerId, Resource.ROLES, Operation.DELETE));
    }

    @Test
    void unionAcrossTwoRolesReturnsTrue() {
        UUID healerId = UUID.randomUUID();
        UUID roleA = UUID.randomUUID();
        UUID roleB = UUID.randomUUID();
        when(userRoleRepository.findAllByPrincipalTypeAndPrincipalId("HEALER", healerId))
                .thenReturn(List.of(assignment(healerId, roleA), assignment(healerId, roleB)));
        when(rolePermissionRepository.findAllByRoleIdIn(List.of(roleA, roleB)))
                .thenReturn(List.of(grant(roleA, Resource.ROLES, Operation.READ),
                        grant(roleB, Resource.ROLES, Operation.DELETE)));

        assertTrue(lookupService.hasPermission(healerId, Resource.ROLES, Operation.READ));
        assertTrue(lookupService.hasPermission(healerId, Resource.ROLES, Operation.DELETE));
    }

    @Test
    void noAssignmentsReturnsFalse() {
        UUID healerId = UUID.randomUUID();
        when(userRoleRepository.findAllByPrincipalTypeAndPrincipalId("HEALER", healerId))
                .thenReturn(Collections.emptyList());

        assertFalse(lookupService.hasPermission(healerId, Resource.ROLES, Operation.READ));
        verifyNoInteractions(rolePermissionRepository);
    }

    @Test
    void nullHealerIdReturnsFalseWithoutAnyRepositoryRead() {
        assertFalse(lookupService.hasPermission(null, Resource.ROLES, Operation.READ));
        verifyNoInteractions(userRoleRepository);
        verifyNoInteractions(rolePermissionRepository);
    }

    /**
     * RBAC-03 proof: the same service instance, called twice in a row for the
     * same healer, must reflect an intervening revoke on the immediately
     * following call — no eviction step, no re-instantiation, no cache of
     * any kind between the two calls.
     */
    @Test
    void revokedGrantIsDeniedOnTheVeryNextCall() {
        UUID healerId = UUID.randomUUID();
        UUID roleA = UUID.randomUUID();
        when(userRoleRepository.findAllByPrincipalTypeAndPrincipalId("HEALER", healerId))
                .thenReturn(List.of(assignment(healerId, roleA)));
        when(rolePermissionRepository.findAllByRoleIdIn(List.of(roleA)))
                .thenReturn(List.of(grant(roleA, Resource.ROLES, Operation.CREATE)))
                .thenReturn(Collections.emptyList());

        assertTrue(lookupService.hasPermission(healerId, Resource.ROLES, Operation.CREATE));
        assertFalse(lookupService.hasPermission(healerId, Resource.ROLES, Operation.CREATE));
    }
}
