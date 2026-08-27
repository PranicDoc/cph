package com.pranicdoc.cph.rbac.service;

import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.rbac.dto.RoleAssignmentRequest;
import com.pranicdoc.cph.rbac.dto.RoleAssignmentResponse;
import com.pranicdoc.cph.rbac.model.UserRole;
import com.pranicdoc.cph.rbac.repository.RbacRoleRepository;
import com.pranicdoc.cph.rbac.repository.RolePermissionRepository;
import com.pranicdoc.cph.rbac.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleAssignmentServiceTest {

    @Mock private UserRoleRepository userRoleRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private RbacRoleRepository rbacRoleRepository;
    @Mock private HealerRepository healerRepository;
    @Mock private RoleManagerGuard roleManagerGuard;
    @Mock private SecurityContextUtil securityContextUtil;

    @InjectMocks private RoleAssignmentService roleAssignmentService;

    private void stubUserRoleSaveAssignsRandomId() {
        lenient().when(userRoleRepository.save(any(UserRole.class))).thenAnswer(inv -> {
            UserRole ur = inv.getArgument(0);
            if (ur.getId() == null) {
                ur.setId(UUID.randomUUID());
            }
            return ur;
        });
    }

    // --- assign ---

    @Test
    void assign_validRequest_persistsHealerAssignmentRowWithCurrentUserAsAssignedBy() {
        UUID principalId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID currentUser = UUID.randomUUID();
        when(rbacRoleRepository.existsById(roleId)).thenReturn(true);
        when(healerRepository.existsById(principalId)).thenReturn(true);
        when(userRoleRepository.findByPrincipalTypeAndPrincipalIdAndRoleId("HEALER", principalId, roleId))
                .thenReturn(Optional.empty());
        when(securityContextUtil.getCurrentHealerIdOrNull()).thenReturn(currentUser);
        stubUserRoleSaveAssignsRandomId();

        roleAssignmentService.assign(new RoleAssignmentRequest(principalId, roleId));

        ArgumentCaptor<UserRole> captor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(captor.capture());
        UserRole saved = captor.getValue();
        assertEquals("HEALER", saved.getPrincipalType());
        assertEquals(principalId, saved.getPrincipalId());
        assertEquals(roleId, saved.getRoleId());
        assertNotNull(saved.getAssignedAt());
        assertEquals(currentUser, saved.getAssignedBy());
    }

    @Test
    void assign_validRequest_returnsResponseMirroringPersistedRow() {
        UUID principalId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        when(rbacRoleRepository.existsById(roleId)).thenReturn(true);
        when(healerRepository.existsById(principalId)).thenReturn(true);
        when(userRoleRepository.findByPrincipalTypeAndPrincipalIdAndRoleId("HEALER", principalId, roleId))
                .thenReturn(Optional.empty());
        when(securityContextUtil.getCurrentHealerIdOrNull()).thenReturn(UUID.randomUUID());
        stubUserRoleSaveAssignsRandomId();

        RoleAssignmentResponse response = roleAssignmentService.assign(new RoleAssignmentRequest(principalId, roleId));

        ArgumentCaptor<UserRole> captor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(captor.capture());
        UserRole saved = captor.getValue();
        assertEquals(saved.getId(), response.id());
        assertEquals(saved.getPrincipalId(), response.principalId());
        assertEquals(saved.getRoleId(), response.roleId());
        assertEquals(saved.getAssignedAt(), response.assignedAt());
    }

    @Test
    void assign_unknownRole_throwsResourceNotFoundAndNeverSaves() {
        UUID principalId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        when(rbacRoleRepository.existsById(roleId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> roleAssignmentService.assign(new RoleAssignmentRequest(principalId, roleId)));

        verify(userRoleRepository, never()).save(any());
        verify(healerRepository, never()).existsById(any());
        verify(userRoleRepository, never()).findByPrincipalTypeAndPrincipalIdAndRoleId(any(), any(), any());
    }

    @Test
    void assign_unknownPrincipal_throwsResourceNotFoundAndNeverSaves() {
        UUID principalId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        when(rbacRoleRepository.existsById(roleId)).thenReturn(true);
        when(healerRepository.existsById(principalId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> roleAssignmentService.assign(new RoleAssignmentRequest(principalId, roleId)));

        verify(userRoleRepository, never()).save(any());
        verify(userRoleRepository, never()).findByPrincipalTypeAndPrincipalIdAndRoleId(any(), any(), any());
    }

    @Test
    void assign_duplicatePair_throwsIllegalArgumentWithDuplicateMessageAndNeverSaves() {
        UUID principalId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        when(rbacRoleRepository.existsById(roleId)).thenReturn(true);
        when(healerRepository.existsById(principalId)).thenReturn(true);
        when(userRoleRepository.findByPrincipalTypeAndPrincipalIdAndRoleId("HEALER", principalId, roleId))
                .thenReturn(Optional.of(new UserRole()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> roleAssignmentService.assign(new RoleAssignmentRequest(principalId, roleId)));

        assertEquals("This user is already assigned this role.", ex.getMessage());
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void assign_anyValidationFailure_leavesRepositoryUntouched() {
        UUID principalId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        when(rbacRoleRepository.existsById(roleId)).thenReturn(true);
        when(healerRepository.existsById(principalId)).thenReturn(true);
        when(userRoleRepository.findByPrincipalTypeAndPrincipalIdAndRoleId("HEALER", principalId, roleId))
                .thenReturn(Optional.of(new UserRole()));

        assertThrows(IllegalArgumentException.class,
                () -> roleAssignmentService.assign(new RoleAssignmentRequest(principalId, roleId)));

        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void assign_noCurrentUserResolvable_persistsNullAssignedByRatherThanThrowing() {
        UUID principalId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        when(rbacRoleRepository.existsById(roleId)).thenReturn(true);
        when(healerRepository.existsById(principalId)).thenReturn(true);
        when(userRoleRepository.findByPrincipalTypeAndPrincipalIdAndRoleId("HEALER", principalId, roleId))
                .thenReturn(Optional.empty());
        when(securityContextUtil.getCurrentHealerIdOrNull()).thenReturn(null);
        stubUserRoleSaveAssignsRandomId();

        roleAssignmentService.assign(new RoleAssignmentRequest(principalId, roleId));

        ArgumentCaptor<UserRole> captor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(captor.capture());
        assertNull(captor.getValue().getAssignedBy());
    }
}
