package com.pranicdoc.cph.rbac.controller;

import com.pranicdoc.cph.rbac.dto.RoleAssignedUserResponse;
import com.pranicdoc.cph.rbac.dto.RoleAssignmentRequest;
import com.pranicdoc.cph.rbac.dto.RoleAssignmentResponse;
import com.pranicdoc.cph.rbac.dto.RoleUserAssignmentBody;
import com.pranicdoc.cph.rbac.service.RoleAssignmentQueryService;
import com.pranicdoc.cph.rbac.service.RoleAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * RoleUserAssignmentController — the {@code /api/admin/roles/{id}/users}
 * sub-resource: list, add, and remove the accounts assigned to one role
 * (ASSIGN-01/02/03, 38-CONTEXT D-05).
 * <p>
 * This controller owns no business logic. Both mutations delegate straight
 * to {@link RoleAssignmentService}, which already validates that the role
 * exists, that the target account exists, and that the pair is not already
 * assigned, and — on unassign — consults the self-lockout guard. A 404
 * {@code ResourceNotFoundException} and a 409 {@code SelfLockoutException}
 * propagate untouched to {@code GlobalExceptionHandler}; this class adds no
 * try/catch and performs no existence checks of its own. The read path
 * delegates to {@link RoleAssignmentQueryService}, which is not
 * {@code @Auditable} — a list read is not an authorization event.
 * <p>
 * Verb mapping: {@code GET} carries the {@code ROLES:READ} gate; {@code POST}
 * and {@code DELETE} carry {@code ROLES:UPDATE}. "Manage roles" is defined as
 * {@code ROLES:UPDATE} (37-CONTEXT D-06), and changing which accounts hold a
 * role changes that role's reach — it is managing the role. All three
 * authorization literals are byte-identical to {@code RbacRoleController}'s.
 * <p>
 * Each mutation hands {@link RoleAssignmentService} a whole
 * {@link RoleAssignmentRequest} record, never two loose UUIDs: the audit
 * trail serialises only {@code args[0]}, so splitting the identifiers would
 * drop one of them from every assignment audit entry. The record's component
 * order is {@code principalId} first, {@code roleId} second — hence
 * {@code new RoleAssignmentRequest(body.userId(), id)}.
 */
@RestController
@RequestMapping("/api/admin/roles/{id}/users")
@RequiredArgsConstructor
public class RoleUserAssignmentController {

    private final RoleAssignmentService roleAssignmentService;
    private final RoleAssignmentQueryService roleAssignmentQueryService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPADM') and @rbacAuthz.hasPermission('ROLES','READ')")
    public ResponseEntity<List<RoleAssignedUserResponse>> list(@PathVariable UUID id) {
        return ResponseEntity.ok(roleAssignmentQueryService.listAssignedUsers(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPADM') and @rbacAuthz.hasPermission('ROLES','UPDATE')")
    public ResponseEntity<RoleAssignmentResponse> assign(
            @PathVariable UUID id, @Valid @RequestBody RoleUserAssignmentBody body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(roleAssignmentService.assign(new RoleAssignmentRequest(body.userId(), id)));
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPADM') and @rbacAuthz.hasPermission('ROLES','UPDATE')")
    public ResponseEntity<Void> unassign(@PathVariable UUID id, @PathVariable UUID userId) {
        roleAssignmentService.unassign(new RoleAssignmentRequest(userId, id));
        return ResponseEntity.noContent().build();
    }
}
