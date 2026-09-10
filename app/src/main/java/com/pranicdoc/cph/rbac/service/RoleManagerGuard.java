package com.pranicdoc.cph.rbac.service;

import com.pranicdoc.cph.common.exception.SelfLockoutException;
import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Permission;
import com.pranicdoc.cph.rbac.model.Resource;
import com.pranicdoc.cph.rbac.model.RolePermission;
import com.pranicdoc.cph.rbac.repository.RolePermissionRepository;
import com.pranicdoc.cph.rbac.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * RoleManagerGuard — RBAC-05's single server-side enforcement point for the
 * self-lockout rule. Every mutation that could strip the ability to manage
 * roles routes its check through this one class: {@code RbacRoleService.update}
 * (editing a role's grants), {@code RbacRoleService.delete} (deleting a role),
 * and {@code RoleAssignmentService.unassign} (removing a user's role). The pair
 * that defines "can manage roles" — the ROLES resource with the edit-a-role's-
 * grants verb (D-06) — is spelled out in exactly one physical location in the
 * codebase: {@link #ROLE_MANAGEMENT_PERMISSION} below. No call site re-derives
 * it.
 *
 * <p>The count deliberately reflects only capabilities actually held through
 * {@code user_role} assignment rows. The unconditional super-admin bypass is
 * not part of the count on either side of the comparison, and this needs no
 * special-casing here: by construction (D-02, Phase 35 D-02) those accounts
 * are never targets in {@code user_role} and never receive a permission grant,
 * so reading {@code role_permission} and {@code user_role} alone excludes them
 * naturally — there is no tier literal anywhere in this class.
 *
 * <p>This class performs no caching of any kind — no request-scoped
 * memoization, no eviction hook — mirroring {@code RolePermissionLookupService}'s
 * stated reason (PD-37-05): a stale count could either authorize a genuine
 * last-manager strip or block a legitimate edit. The count is read live on
 * every call.
 */
@Service
@RequiredArgsConstructor
public class RoleManagerGuard {

    private static final Permission ROLE_MANAGEMENT_PERMISSION =
            new Permission(Resource.ROLES, Operation.UPDATE);

    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;

    /**
     * Whether the given role currently carries a grant of the manage-roles
     * capability (D-06). True only if the role has a grant row matching
     * {@link #ROLE_MANAGEMENT_PERMISSION} exactly.
     */
    public boolean roleGrantsRoleManagement(UUID roleId) {
        return rolePermissionRepository.findAllByRoleId(roleId).stream()
                .anyMatch(rp -> rp.getResource() == ROLE_MANAGEMENT_PERMISSION.resource()
                        && rp.getOperation() == ROLE_MANAGEMENT_PERMISSION.operation());
    }

    /**
     * Whether a submitted permission set would grant the manage-roles
     * capability. Relies on {@link Permission} record equality.
     */
    public boolean grantSetIncludesRoleManagement(List<Permission> permissions) {
        return permissions != null && permissions.contains(ROLE_MANAGEMENT_PERMISSION);
    }

    /**
     * Total count of (role, assigned-user) pairs currently granting the
     * manage-roles capability, across roles held via {@code user_role} only.
     * Returns 0 without touching {@code user_role} when no role grants it —
     * an empty {@code IN ()} is both wasteful and dialect-dependent.
     */
    public long countRoleManagerPairs() {
        List<UUID> roleIds = rolePermissionRepository
                .findAllByResourceAndOperation(Resource.ROLES, Operation.UPDATE)
                .stream()
                .map(RolePermission::getRoleId)
                // No .distinct() — role_permission's unique (role_id, resource, operation)
                // constraint guarantees at most one row per role for this pair.
                .toList();
        if (roleIds.isEmpty()) {
            return 0;
        }
        return userRoleRepository.countByRoleIdIn(roleIds);
    }

    /**
     * Refuses an action that would drive the live manager-pair count to zero.
     * No-ops when the touched role does not currently grant the capability, or
     * when the action removes no pairs — RBAC-05 is only about losing the
     * capability entirely, not about any other grant change.
     *
     * @param roleCurrentlyGrantsRoleManagement whether the role being mutated grants manage-roles today
     * @param pairsAboutToBeRemoved             how many (role, user) pairs this action would remove
     * @throws SelfLockoutException if the action would leave no manager pair standing
     */
    public void assertActionPreservesRoleManagement(
            boolean roleCurrentlyGrantsRoleManagement, long pairsAboutToBeRemoved) {
        if (!roleCurrentlyGrantsRoleManagement || pairsAboutToBeRemoved <= 0) {
            return;
        }
        long totalPairs = countRoleManagerPairs();
        long remainingPairs = totalPairs - pairsAboutToBeRemoved;
        // PD-37-04: "would leave nobody" is a zero condition, never a one condition.
        if (remainingPairs <= 0) {
            throw new SelfLockoutException();
        }
    }
}
