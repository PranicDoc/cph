package com.pranicdoc.cph.rbac.service;

import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;
import com.pranicdoc.cph.rbac.model.RolePermission;
import com.pranicdoc.cph.rbac.model.UserRole;
import com.pranicdoc.cph.rbac.repository.RolePermissionRepository;
import com.pranicdoc.cph.rbac.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RolePermissionLookupService — the live, per-request resolution of a
 * principal's effective grants across the union of every role they hold.
 * This is what RBAC-02 means by "dynamic, DB-backed": every call re-reads
 * {@code user_role} and {@code role_permission} directly, rather than
 * consulting any hardcoded role literal.
 *
 * There is deliberately no caching layer anywhere in this class — no
 * request-scoped memoization, no eviction hook, no invalidation step of
 * any kind. That absence is not an oversight; it is what makes RBAC-03
 * ("a revoked grant is denied on the very next request") true by
 * construction rather than by disciplined cache eviction, per this
 * phase's locked decision superseding the caching approach an earlier
 * phase's context document had sketched for permission checks. Phase 35's
 * context document is superseded here specifically for this lookup path.
 *
 * The lookup shape is one assignment-table read plus one grant-table read
 * per held role — an N+1 pattern by name, but an acceptable one at this
 * phase's real scale: a tiny internal admin/ops headcount, not a public
 * API. REQUIREMENTS.md already scopes a future requirement for adding a
 * caching layer back in if measured production latency ever demands it;
 * this class is not where that trade-off gets made.
 */
@Service
@RequiredArgsConstructor
public class RolePermissionLookupService {

    private static final String PRINCIPAL_TYPE_HEALER = "HEALER";

    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    /**
     * Resolves whether {@code healerId} is permitted to perform
     * {@code operation} on {@code resource}, as the union of grants across
     * every role currently assigned to that principal.
     *
     * @param healerId  the principal's healer id; {@code null} is always denied
     * @param resource  the resource being acted on
     * @param operation the verb being attempted
     * @return {@code true} only if at least one held role grants exactly
     *         this (resource, operation) pair
     */
    public boolean hasPermission(UUID healerId, Resource resource, Operation operation) {
        if (healerId == null) {
            return false;
        }

        List<UserRole> assignments =
                userRoleRepository.findAllByPrincipalTypeAndPrincipalId(PRINCIPAL_TYPE_HEALER, healerId);

        if (assignments.isEmpty()) {
            return false;
        }

        List<UUID> roleIds = assignments.stream()
                .map(UserRole::getRoleId)
                .distinct()
                .collect(Collectors.toList());

        return rolePermissionRepository.findAllByRoleIdIn(roleIds).stream()
                .anyMatch(grant -> grant.getResource() == resource && grant.getOperation() == operation);
    }

    /**
     * Every (resource, operation) pair {@code healerId} holds, as the union across all their
     * roles — the same data {@link #hasPermission} checks one pair against. Used to tell the UI
     * which screens to show; it decides nothing on the server.
     */
    public Map<Resource, Set<Operation>> effectiveGrants(UUID healerId) {
        Map<Resource, Set<Operation>> grants = new EnumMap<>(Resource.class);
        if (healerId == null) {
            return grants;
        }
        List<UUID> roleIds = userRoleRepository.findAllByPrincipalTypeAndPrincipalId(PRINCIPAL_TYPE_HEALER, healerId)
                .stream()
                .map(UserRole::getRoleId)
                .distinct()
                .toList();
        if (roleIds.isEmpty()) {
            return grants;
        }
        rolePermissionRepository.findAllByRoleIdIn(roleIds)
                .forEach(grant -> grants
                        .computeIfAbsent(grant.getResource(), r -> EnumSet.noneOf(Operation.class))
                        .add(grant.getOperation()));
        return grants;
    }
}
