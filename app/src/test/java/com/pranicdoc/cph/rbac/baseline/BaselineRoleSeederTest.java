package com.pranicdoc.cph.rbac.baseline;

import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.RbacRole;
import com.pranicdoc.cph.rbac.model.Resource;
import com.pranicdoc.cph.rbac.model.RolePermission;
import com.pranicdoc.cph.rbac.repository.RbacRoleRepository;
import com.pranicdoc.cph.rbac.repository.RolePermissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BaselineRoleSeederTest {

    @Mock private RbacRoleRepository rbacRoleRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;

    @InjectMocks private BaselineRoleSeeder seeder;

    @Test
    void run_whenPractitionerRoleAbsent_createsItWithTheFullBaselineGrantMatrix() {
        UUID newRoleId = UUID.randomUUID();
        when(rbacRoleRepository.findByName("Practitioner")).thenReturn(Optional.empty());
        when(rbacRoleRepository.save(any(RbacRole.class))).thenAnswer(inv -> {
            RbacRole r = inv.getArgument(0);
            r.setId(newRoleId);
            return r;
        });

        seeder.run();

        ArgumentCaptor<RbacRole> roleCaptor = ArgumentCaptor.forClass(RbacRole.class);
        verify(rbacRoleRepository).save(roleCaptor.capture());
        assertEquals("Practitioner", roleCaptor.getValue().getName());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RolePermission>> grantsCaptor = ArgumentCaptor.forClass(List.class);
        verify(rolePermissionRepository).saveAll(grantsCaptor.capture());

        Map<Resource, Set<Operation>> persisted = grantsCaptor.getValue().stream()
                .peek(rp -> assertEquals(newRoleId, rp.getRoleId()))
                .collect(Collectors.groupingBy(
                        RolePermission::getResource,
                        Collectors.mapping(RolePermission::getOperation, Collectors.toSet())));

        Map<Resource, Set<Operation>> expected = Map.ofEntries(
                Map.entry(Resource.PATIENT, Set.of(Operation.CREATE, Operation.READ, Operation.UPDATE)),
                Map.entry(Resource.CASE, Set.of(Operation.CREATE, Operation.READ, Operation.UPDATE)),
                Map.entry(Resource.SESSION, Set.of(Operation.CREATE, Operation.READ, Operation.UPDATE)),
                Map.entry(Resource.PDF, Set.of(Operation.CREATE, Operation.READ, Operation.EXPORT)),
                Map.entry(Resource.SCAN, Set.of(Operation.CREATE)),
                Map.entry(Resource.SESSION_PROTOCOL, Set.of(Operation.CREATE)),
                Map.entry(Resource.FEEDBACK, Set.of(Operation.CREATE)),
                Map.entry(Resource.CREDIT_ACCOUNTS, Set.of(Operation.READ, Operation.CREATE)),
                Map.entry(Resource.CREDIT_ACCOUNT_TRANSACTIONS, Set.of(Operation.READ, Operation.WRITE)),
                Map.entry(Resource.MASTER_DATA_CHAKRAS, Set.of(Operation.READ)),
                Map.entry(Resource.MASTER_DATA_COLORS, Set.of(Operation.READ)),
                // CREATE/UPDATE/DELETE (not read-only) so a healer can self-serve their own
                // protocol-chakra-color additions -- see BaselineRole's class doc.
                Map.entry(Resource.MASTER_DATA_PROTOCOLS,
                        Set.of(Operation.READ, Operation.CREATE, Operation.UPDATE, Operation.DELETE)));

        assertEquals(expected, persisted);
    }

    @Test
    void run_whenPractitionerRoleAlreadyExists_writesNothing() {
        when(rbacRoleRepository.findByName("Practitioner"))
                .thenReturn(Optional.of(RbacRole.builder().name("Practitioner").build()));

        seeder.run();

        verify(rbacRoleRepository, never()).save(any());
        verifyNoInteractions(rolePermissionRepository);
    }
}
