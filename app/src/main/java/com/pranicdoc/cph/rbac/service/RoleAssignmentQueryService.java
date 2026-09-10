package com.pranicdoc.cph.rbac.service;

import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.rbac.dto.RoleAssignedUserResponse;
import com.pranicdoc.cph.rbac.model.UserRole;
import com.pranicdoc.cph.rbac.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only companion to {@link RoleAssignmentService}: composes the
 * "users assigned to this role" list (ASSIGN-01) that the role→user
 * assignment tab reads.
 * <p>
 * This is deliberately kept separate from {@link RoleAssignmentService},
 * which is write-oriented and fully {@code @Auditable} (38-RESEARCH Open
 * Question Q4). A list read is not an authorization event and must not emit
 * audit rows, so it lives here with no {@code @Auditable} annotation.
 * <p>
 * {@code user_role.principal_id} carries no foreign key to {@code healer} —
 * the column is polymorphic by design — so the account details cannot be
 * associated in JPQL. The composition is a two-step no-FK zip: read the
 * assignment rows for the role, batch-fetch every referenced {@code Healer}
 * in a single {@code findAllById}, then map each row to a response. A row
 * whose principal has no matching {@code Healer} yields a null name/email —
 * it is never dropped and never raises.
 */
@Service
@RequiredArgsConstructor
public class RoleAssignmentQueryService {

    private final UserRoleRepository userRoleRepository;
    private final HealerRepository healerRepository;

    @Transactional(readOnly = true)
    public List<RoleAssignedUserResponse> listAssignedUsers(UUID roleId) {
        List<UserRole> rows = userRoleRepository.findAllByRoleId(roleId);

        List<UUID> principalIds = rows.stream()
                .map(UserRole::getPrincipalId)
                .toList();

        Map<UUID, Healer> healersById = principalIds.isEmpty()
                ? Map.of()
                : healerRepository.findAllById(principalIds).stream()
                        .collect(Collectors.toMap(Healer::getId, Function.identity()));

        return rows.stream()
                .map(row -> {
                    Healer healer = healersById.get(row.getPrincipalId());
                    return new RoleAssignedUserResponse(
                            row.getPrincipalId(),
                            healer != null ? healer.getFullName() : null,
                            healer != null ? healer.getEmail() : null,
                            row.getAssignedAt());
                })
                .toList();
    }
}
