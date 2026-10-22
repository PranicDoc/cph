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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** {@link RolePermissionLookupService#effectiveGrants}: the union of grants the UI uses to show the admin area. */
@ExtendWith(MockitoExtension.class)
class RolePermissionLookupServiceEffectiveGrantsTest {

    @Mock private UserRoleRepository userRoleRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @InjectMocks private RolePermissionLookupService lookupService;

    private UserRole assignment(UUID healerId, UUID roleId) {
        return UserRole.builder().principalType("HEALER").principalId(healerId).roleId(roleId)
                .assignedAt(Instant.now()).build();
    }

    private RolePermission grant(UUID roleId, Resource resource, Operation operation) {
        return RolePermission.builder().roleId(roleId).resource(resource).operation(operation).build();
    }

    @Test
    void unionsTheGrantsOfEveryHeldRole() {
        UUID healerId = UUID.randomUUID();
        UUID practitioner = UUID.randomUUID();
        UUID seeder = UUID.randomUUID();
        when(userRoleRepository.findAllByPrincipalTypeAndPrincipalId("HEALER", healerId))
                .thenReturn(List.of(assignment(healerId, practitioner), assignment(healerId, seeder)));
        when(rolePermissionRepository.findAllByRoleId(practitioner)).thenReturn(List.of(
                grant(practitioner, Resource.MASTER_DATA_PROTOCOLS, Operation.READ)));
        when(rolePermissionRepository.findAllByRoleId(seeder)).thenReturn(List.of(
                grant(seeder, Resource.MASTER_DATA_PROTOCOLS, Operation.READ),
                grant(seeder, Resource.MASTER_DATA_PROTOCOLS, Operation.CREATE),
                grant(seeder, Resource.MASTER_DATA_CHAKRAS, Operation.READ)));

        Map<Resource, Set<Operation>> grants = lookupService.effectiveGrants(healerId);

        assertThat(grants).containsOnlyKeys(Resource.MASTER_DATA_PROTOCOLS, Resource.MASTER_DATA_CHAKRAS);
        assertThat(grants.get(Resource.MASTER_DATA_PROTOCOLS)).containsExactlyInAnyOrder(Operation.READ, Operation.CREATE);
        assertThat(grants.get(Resource.MASTER_DATA_CHAKRAS)).containsExactly(Operation.READ);
    }

    @Test
    void noRolesMeansNoGrants() {
        UUID healerId = UUID.randomUUID();
        when(userRoleRepository.findAllByPrincipalTypeAndPrincipalId("HEALER", healerId)).thenReturn(List.of());

        assertThat(lookupService.effectiveGrants(healerId)).isEmpty();
    }

    @Test
    void nullHealerHasNoGrantsAndTouchesNoTable() {
        assertThat(lookupService.effectiveGrants(null)).isEmpty();
        verifyNoInteractions(userRoleRepository, rolePermissionRepository);
    }
}
