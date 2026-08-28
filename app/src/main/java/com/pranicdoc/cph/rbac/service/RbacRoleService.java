package com.pranicdoc.cph.rbac.service;

import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.dto.PageResponse;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.rbac.dto.ResourceGrantDto;
import com.pranicdoc.cph.rbac.dto.RoleCreateRequest;
import com.pranicdoc.cph.rbac.dto.RoleResponse;
import com.pranicdoc.cph.rbac.dto.RoleSummaryResponse;
import com.pranicdoc.cph.rbac.dto.RoleUpdateRequest;
import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Permission;
import com.pranicdoc.cph.rbac.model.RbacRole;
import com.pranicdoc.cph.rbac.model.Resource;
import com.pranicdoc.cph.rbac.model.RolePermission;
import com.pranicdoc.cph.rbac.repository.RbacRoleRepository;
import com.pranicdoc.cph.rbac.repository.RolePermissionRepository;
import com.pranicdoc.cph.rbac.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RbacRoleService — role CRUD business logic (ROLE-10, D-03, AUDIT-01).
 * <p>
 * This class owns no authorization decision of any kind: whether a caller
 * may invoke these methods at all is decided entirely at the controller
 * boundary in a later plan. What this class guarantees is that whatever
 * reaches it is safe to persist — a duplicate role name (case-insensitive)
 * is rejected with a friendly message before any write, and every submitted
 * grant is re-validated against the resource/operation catalog by
 * constructing a {@link Permission} for it, so a client that bypasses the
 * UI's toggle grid cannot smuggle in a combination the catalog forbids.
 * <p>
 * Every mutation here is recorded to the audit log with a payload that
 * captures what actually changed, not merely which row changed — see the
 * parameter-order note on {@link #update(RoleUpdateRequest, UUID)} for the
 * mechanism that makes that true.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RbacRoleService {

    private final RbacRoleRepository rbacRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleManagerGuard roleManagerGuard;

    private static final String DUPLICATE_NAME_MESSAGE =
            "A role with that name already exists. Please choose a different name.";

    /**
     * Persists a new role and its grant set in one transaction. Grants are
     * parsed and re-validated against the catalog before any repository
     * write, so an invalid literal never produces a partially-written role.
     */
    @Auditable(action = "CREATE_ROLE")
    public RoleResponse create(RoleCreateRequest request) {
        List<Permission> permissions = toPermissions(request.grants());

        if (rbacRoleRepository.existsByNameIgnoreCase(request.name())) {
            throw new IllegalArgumentException(DUPLICATE_NAME_MESSAGE);
        }

        RbacRole role = RbacRole.builder()
                .name(request.name())
                .description(request.description())
                .build();
        RbacRole savedRole = rbacRoleRepository.save(role);

        List<RolePermission> grantRows = permissions.stream()
                .map(permission -> RolePermission.builder()
                        .roleId(savedRole.getId())
                        .resource(permission.resource())
                        .operation(permission.operation())
                        .build())
                .toList();
        List<RolePermission> persistedGrants = rolePermissionRepository.saveAll(grantRows);

        return toResponse(savedRole, persistedGrants);
    }

    /**
     * Full-replace edit of name, description and the grant set.
     * <p>
     * DELIBERATE PARAMETER ORDER — the request payload comes first, the
     * identifier second. Do not "normalize" this to match the more common
     * id-first convention used elsewhere in this codebase: the audit
     * mechanism serializes only the first method argument into the audit
     * trail, so putting the identifier first would leave the audit record
     * unable to show what was actually changed, only which row was touched.
     * The identifier is still recoverable for the audit entity reference —
     * that resolution scans every argument for a UUID rather than assuming
     * position, so it stays correct regardless of where the id sits in the
     * parameter list.
     * <p>
     * SELF-LOCKOUT GUARD (RBAC-05, D-01) — this method refuses an edit that
     * would remove the manage-roles capability from its last remaining
     * holders. The check runs before any repository write, so a refused edit
     * persists nothing: the role row and every grant row are left
     * byte-identical. The definition of what "manage roles" means and the
     * rule for counting who still holds it both live in
     * {@link RoleManagerGuard}, not here — this method only asks whether this
     * particular edit drops the capability and, if so, how many
     * (role, assigned-user) pairs it is about to remove. An edit that keeps
     * the capability never consults the count at all.
     */
    @Auditable(action = "UPDATE_ROLE")
    public RoleResponse update(RoleUpdateRequest request, UUID id) {
        RbacRole role = loadRole(id);

        List<Permission> permissions = toPermissions(request.grants());

        boolean isRename = !request.name().equalsIgnoreCase(role.getName());
        if (isRename && rbacRoleRepository.existsByNameIgnoreCase(request.name())) {
            throw new IllegalArgumentException(DUPLICATE_NAME_MESSAGE);
        }

        // Only a grant set that actually drops the manage-roles capability is
        // guarded (PD-37-11). A capability-preserving edit — renaming, adding
        // grants, changing unrelated verbs — must never be able to fail on the
        // count, even when this role happens to be the last manager.
        boolean losesRoleManagement = roleManagerGuard.roleGrantsRoleManagement(id)
                && !roleManagerGuard.grantSetIncludesRoleManagement(permissions);
        if (losesRoleManagement) {
            roleManagerGuard.assertActionPreservesRoleManagement(true, userRoleRepository.countByRoleId(id));
        }

        role.setName(request.name());
        role.setDescription(request.description());
        RbacRole savedRole = rbacRoleRepository.save(role);

        // Physical delete, not soft-delete: a soft-deleted grant row still
        // occupies its unique (role, resource, operation) slot, and would
        // collide the moment the same pair is re-granted on this same role.
        // History of the outgoing rows is retained by the Envers shadow table.
        //
        // The explicit flush() is load-bearing, not defensive boilerplate:
        // Hibernate's default action-queue order executes queued INSERTs
        // before queued DELETEs within a single flush, regardless of the
        // order these repository calls were made in. Without forcing the
        // DELETE to hit the DB first, re-granting any (resource, operation)
        // pair that was already active on this role — the common case where
        // an edit adds one permission while keeping the rest unchanged —
        // throws a DataIntegrityViolationException on uq_role_permission
        // (confirmed 2026-08-26 during the 36-05 live-verification re-run:
        // every edit that preserved an existing grant failed with a 409).
        List<RolePermission> outgoing = rolePermissionRepository.findAllByRoleId(id);
        rolePermissionRepository.deleteAll(outgoing);
        rolePermissionRepository.flush();

        List<RolePermission> incoming = permissions.stream()
                .map(permission -> RolePermission.builder()
                        .roleId(id)
                        .resource(permission.resource())
                        .operation(permission.operation())
                        .build())
                .toList();
        List<RolePermission> persistedGrants = rolePermissionRepository.saveAll(incoming);

        return toResponse(savedRole, persistedGrants);
    }

    /**
     * Soft-deletes the role AND every grant it holds, in the same
     * transaction. The effective-permission lookup resolves grants purely by
     * role id and never consults the role row itself, so a role that was
     * soft-deleted without also retiring its grants would keep granting
     * access through any surviving assignment — a real, durable privilege
     * retention bug.
     * <p>
     * SELF-LOCKOUT GUARD (RBAC-05, D-01) — deletion is refused when this
     * role's assignment pairs are the last ones granting the manage-roles
     * capability. The check runs before any soft-delete, so a refused
     * deletion retires nothing. This closes the "does not block deleting a
     * role that still has assigned users" caveat that used to sit in this
     * Javadoc, but only for the hard last-manager block: a later phase's
     * warning about deleting a role that has <em>any</em> assigned user
     * remains a separate, softer concern. Deleting a role always removes
     * every one of its assignment pairs, so the guard is consulted
     * unconditionally (PD-37-12) — its own early return covers a role that
     * grants no manage-roles capability or has no assigned users.
     */
    @Auditable(action = "DELETE_ROLE")
    public void delete(UUID id) {
        RbacRole role = loadRole(id);

        roleManagerGuard.assertActionPreservesRoleManagement(
                roleManagerGuard.roleGrantsRoleManagement(id), userRoleRepository.countByRoleId(id));

        List<RolePermission> grants = rolePermissionRepository.findAllByRoleId(id);
        grants.forEach(RolePermission::softDelete);
        rolePermissionRepository.saveAll(grants);

        role.softDelete();
        rbacRoleRepository.save(role);
    }

    /**
     * Role list rows, server-side searched + sorted + paginated (D-08,
     * Phase 38 ROLE-01). {@code search} is a case-insensitive substring match
     * on role name; an empty string matches every non-soft-deleted role.
     * Sort and page size come from the controller's {@code @PageableDefault}
     * (name ascending, size 20) unless the caller overrides them.
     * <p>
     * The per-row grant/user-count reads remain an accepted N+1 — now bounded
     * to a single page's worth of roles rather than the whole table.
     */
    @Transactional(readOnly = true)
    public PageResponse<RoleSummaryResponse> list(String search, Pageable pageable) {
        Page<RoleSummaryResponse> page = rbacRoleRepository
                .findByNameContainingIgnoreCase(search, pageable)
                .map(role -> {
                    List<RolePermission> grants = rolePermissionRepository.findAllByRoleId(role.getId());
                    int resourceCount = (int) grants.stream()
                            .map(RolePermission::getResource)
                            .distinct()
                            .count();
                    long userCount = userRoleRepository.countByRoleId(role.getId());
                    return new RoleSummaryResponse(
                            role.getId(), role.getName(), role.getDescription(),
                            resourceCount, userCount, role.getUpdatedAt());
                });
        return PageResponse.from(page);
    }

    /** Public role detail read. */
    @Transactional(readOnly = true)
    public RoleResponse getById(UUID id) {
        RbacRole role = loadRole(id);
        List<RolePermission> grants = rolePermissionRepository.findAllByRoleId(id);
        return toResponse(role, grants);
    }

    private RbacRole loadRole(UUID id) {
        return rbacRoleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RbacRole", id));
    }

    /**
     * Parses every submitted grant literal and re-validates each
     * (resource, operation) pair by constructing a {@link Permission} for
     * it — that constructor is the single allow-list check for this whole
     * class; nothing here reimplements it. A resource or operation literal
     * absent from the catalog, or a pair the catalog does not allow, raises
     * an {@link IllegalArgumentException} naming the offending literal
     * before any repository write happens. Identical pairs submitted more
     * than once are de-duplicated so a later save never collides with the
     * role/resource/operation uniqueness constraint.
     */
    private List<Permission> toPermissions(List<ResourceGrantDto> grants) {
        LinkedHashSet<Permission> permissions = new LinkedHashSet<>();

        for (ResourceGrantDto grant : grants) {
            Resource resource;
            try {
                resource = Resource.valueOf(grant.resource());
            } catch (IllegalArgumentException notARecognisedResource) {
                throw new IllegalArgumentException(
                        "'%s' is not a recognised resource.".formatted(grant.resource()));
            }

            for (String operationLiteral : grant.operations()) {
                Operation operation;
                try {
                    operation = Operation.valueOf(operationLiteral);
                } catch (IllegalArgumentException notARecognisedOperation) {
                    throw new IllegalArgumentException(
                            "'%s' is not a recognised operation.".formatted(operationLiteral));
                }

                try {
                    permissions.add(new Permission(resource, operation));
                } catch (IllegalArgumentException unsupportedCombination) {
                    throw new IllegalArgumentException(unsupportedCombination.getMessage());
                }
            }
        }

        return List.copyOf(permissions);
    }

    /**
     * Regroups the flat grant rows back into one entry per distinct
     * resource, mirroring the request shape, with resources and operations
     * both sorted by enum name for a stable, testable ordering.
     */
    private RoleResponse toResponse(RbacRole role, List<RolePermission> grants) {
        Map<Resource, List<Operation>> grouped = grants.stream()
                .collect(Collectors.groupingBy(
                        RolePermission::getResource,
                        Collectors.mapping(RolePermission::getOperation, Collectors.toList())));

        List<ResourceGrantDto> grantDtos = grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new ResourceGrantDto(
                        entry.getKey().name(),
                        entry.getValue().stream()
                                .sorted(Comparator.comparing(Enum::name))
                                .map(Enum::name)
                                .toList()))
                .toList();

        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDescription(),
                grantDtos,
                role.getCreatedAt(),
                role.getUpdatedAt());
    }
}
