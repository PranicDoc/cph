package com.pranicdoc.cph.rbac.service;

import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.rbac.dto.RoleAssignedUserResponse;
import com.pranicdoc.cph.rbac.model.UserRole;
import com.pranicdoc.cph.rbac.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleAssignmentQueryServiceTest {

    @Mock private UserRoleRepository userRoleRepository;
    @Mock private HealerRepository healerRepository;

    @InjectMocks private RoleAssignmentQueryService service;

    // --- fixtures ---

    private UserRole assignmentRow(UUID principalId, UUID roleId, Instant assignedAt) {
        return UserRole.builder()
                .principalType("HEALER")
                .principalId(principalId)
                .roleId(roleId)
                .assignedAt(assignedAt)
                .build();
    }

    private Healer healer(UUID id, String fullName, String email) {
        Healer h = Healer.builder().fullName(fullName).email(email).build();
        h.setId(id);
        return h;
    }

    // --- tests ---

    @Test
    void listAssignedUsers_zipsEachRowToItsHealerNameEmailAndAssignedAt() {
        UUID roleId = UUID.randomUUID();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        Instant t1 = Instant.parse("2026-01-02T03:04:05Z");
        Instant t2 = Instant.parse("2026-02-03T04:05:06Z");

        when(userRoleRepository.findAllByRoleId(roleId)).thenReturn(List.of(
                assignmentRow(alice, roleId, t1),
                assignmentRow(bob, roleId, t2)));
        when(healerRepository.findAllById(anyIterable())).thenReturn(List.of(
                healer(alice, "Alice Anderson", "alice@example.com"),
                healer(bob, "Bob Brown", "bob@example.com")));

        List<RoleAssignedUserResponse> result = service.listAssignedUsers(roleId);

        assertThat(result).containsExactly(
                new RoleAssignedUserResponse(alice, "Alice Anderson", "alice@example.com", t1),
                new RoleAssignedUserResponse(bob, "Bob Brown", "bob@example.com", t2));
    }

    @Test
    void listAssignedUsers_principalWithNoMatchingHealer_yieldsNullNameAndEmailWithoutThrowing() {
        UUID roleId = UUID.randomUUID();
        UUID present = UUID.randomUUID();
        UUID orphan = UUID.randomUUID();
        Instant t1 = Instant.parse("2026-01-02T03:04:05Z");
        Instant t2 = Instant.parse("2026-02-03T04:05:06Z");

        when(userRoleRepository.findAllByRoleId(roleId)).thenReturn(List.of(
                assignmentRow(present, roleId, t1),
                assignmentRow(orphan, roleId, t2)));
        // Only the first principal resolves to a Healer; the second is orphaned.
        when(healerRepository.findAllById(anyIterable())).thenReturn(List.of(
                healer(present, "Present Person", "present@example.com")));

        List<RoleAssignedUserResponse> result = service.listAssignedUsers(roleId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(
                new RoleAssignedUserResponse(present, "Present Person", "present@example.com", t1));

        RoleAssignedUserResponse orphanRow = result.get(1);
        assertThat(orphanRow.userId()).isEqualTo(orphan);
        assertThat(orphanRow.name()).isNull();
        assertThat(orphanRow.email()).isNull();
        assertThat(orphanRow.assignedAt()).isEqualTo(t2);
    }

    @Test
    void listAssignedUsers_batchesHealerLookupExactlyOnceWithEveryPrincipalId() {
        UUID roleId = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        when(userRoleRepository.findAllByRoleId(roleId)).thenReturn(List.of(
                assignmentRow(a, roleId, Instant.now()),
                assignmentRow(b, roleId, Instant.now()),
                assignmentRow(c, roleId, Instant.now())));
        when(healerRepository.findAllById(anyIterable())).thenReturn(List.of());

        service.listAssignedUsers(roleId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<UUID>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(healerRepository).findAllById(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(a, b, c);
    }

    @Test
    void listAssignedUsers_noAssignments_returnsEmptyListAndNeverLooksUpHealers() {
        UUID roleId = UUID.randomUUID();
        when(userRoleRepository.findAllByRoleId(roleId)).thenReturn(List.of());

        List<RoleAssignedUserResponse> result = service.listAssignedUsers(roleId);

        assertThat(result).isEmpty();
        verify(healerRepository, never()).findAllById(any());
    }
}
