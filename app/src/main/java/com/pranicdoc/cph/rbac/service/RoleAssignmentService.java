package com.pranicdoc.cph.rbac.service;

import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.rbac.dto.RoleAssignmentRequest;
import com.pranicdoc.cph.rbac.dto.RoleAssignmentResponse;
import com.pranicdoc.cph.rbac.model.UserRole;
import com.pranicdoc.cph.rbac.repository.RbacRoleRepository;
import com.pranicdoc.cph.rbac.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * RoleAssignmentService — attaches and detaches a role to/from a principal
 * (ASSIGN-04, RBAC-05, D-03). These methods are the first code in the system
 * to write assignment rows: the table has held zero rows since Phase 35
 * created it. Once a principal holds one or more roles, effective
 * permissions are resolved elsewhere by taking the union of the grants of
 * every role that principal holds, so attaching a second role widens that
 * principal's access purely additively, with no further wiring needed here
 * (ASSIGN-04).
 * <p>
 * This class owns no authorization decision of its own. Whether a given
 * caller may attach or detach roles is decided entirely at whatever boundary
 * invokes these methods — the deploy-time bootstrap path today, an
 * admin-facing controller in a later phase.
 * <p>
 * The principal column is polymorphic by design (D-08). Every write today
 * uses the single healer literal; other principal kinds can be added later
 * with no schema change.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RoleAssignmentService {

    private static final String PRINCIPAL_TYPE_HEALER = "HEALER";
    private static final String DUPLICATE_ASSIGNMENT_MESSAGE = "This user is already assigned this role.";

    private final UserRoleRepository userRoleRepository;
    private final RbacRoleRepository rbacRoleRepository;
    private final HealerRepository healerRepository;
    private final RoleManagerGuard roleManagerGuard;
    private final SecurityContextUtil securityContextUtil;

    /**
     * Attaches one role to one account. Validates, in order, that the role
     * exists, that the target account exists, and that the pair is not
     * already assigned — every check runs before the write, so a rejected
     * request persists nothing. The account-existence check is done here
     * because the principal column carries no foreign key (D-08): without it
     * a mistyped identifier would create a permanently orphaned grant row
     * that still counts toward the self-lockout total. The whole request is
     * the sole argument so the audit payload records both identifiers.
     */
    @Auditable(action = "ASSIGN_ROLE")
    public RoleAssignmentResponse assign(RoleAssignmentRequest request) {
        if (!rbacRoleRepository.existsById(request.roleId())) {
            throw new ResourceNotFoundException("RbacRole", request.roleId());
        }
        if (!healerRepository.existsById(request.principalId())) {
            throw new ResourceNotFoundException("Healer", request.principalId());
        }
        if (userRoleRepository.findByPrincipalTypeAndPrincipalIdAndRoleId(
                PRINCIPAL_TYPE_HEALER, request.principalId(), request.roleId()).isPresent()) {
            throw new IllegalArgumentException(DUPLICATE_ASSIGNMENT_MESSAGE);
        }
        UserRole saved = userRoleRepository.save(UserRole.builder()
                .principalType(PRINCIPAL_TYPE_HEALER)
                .principalId(request.principalId())
                .roleId(request.roleId())
                .assignedAt(Instant.now())
                .assignedBy(securityContextUtil.getCurrentHealerIdOrNull())
                .build());
        return toResponse(saved);
    }

    @Auditable(action = "UNASSIGN_ROLE")
    public RoleAssignmentResponse unassign(RoleAssignmentRequest request) {
        throw new UnsupportedOperationException("not implemented");
    }

    private RoleAssignmentResponse toResponse(UserRole userRole) {
        return new RoleAssignmentResponse(
                userRole.getId(), userRole.getPrincipalId(), userRole.getRoleId(), userRole.getAssignedAt());
    }
}
