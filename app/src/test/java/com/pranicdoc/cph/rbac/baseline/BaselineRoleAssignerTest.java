package com.pranicdoc.cph.rbac.baseline;

import com.pranicdoc.cph.rbac.dto.RoleAssignmentRequest;
import com.pranicdoc.cph.rbac.model.RbacRole;
import com.pranicdoc.cph.rbac.repository.RbacRoleRepository;
import com.pranicdoc.cph.rbac.service.RoleAssignmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BaselineRoleAssignerTest {

    @Mock private RbacRoleRepository rbacRoleRepository;
    @Mock private RoleAssignmentService roleAssignmentService;

    @InjectMocks private BaselineRoleAssigner assigner;

    private final UUID healerId = UUID.randomUUID();

    @Test
    void assignTo_whenBaselineRolePresent_assignsItToTheHealer() {
        UUID roleId = UUID.randomUUID();
        RbacRole role = RbacRole.builder().name("Practitioner").build();
        role.setId(roleId);
        when(rbacRoleRepository.findByName("Practitioner")).thenReturn(Optional.of(role));

        assigner.assignTo(healerId);

        verify(roleAssignmentService).assign(new RoleAssignmentRequest(healerId, roleId));
    }

    @Test
    void assignTo_whenBaselineRoleMissing_doesNotAttemptAssignment() {
        when(rbacRoleRepository.findByName("Practitioner")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> assigner.assignTo(healerId));

        verifyNoInteractions(roleAssignmentService);
    }

    @Test
    void assignTo_whenAssignmentThrows_swallowsSoOnboardingIsNotBroken() {
        UUID roleId = UUID.randomUUID();
        RbacRole role = RbacRole.builder().name("Practitioner").build();
        role.setId(roleId);
        when(rbacRoleRepository.findByName("Practitioner")).thenReturn(Optional.of(role));
        when(roleAssignmentService.assign(any())).thenThrow(new IllegalStateException("boom"));

        assertDoesNotThrow(() -> assigner.assignTo(healerId));
    }
}
