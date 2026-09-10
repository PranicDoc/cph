package com.pranicdoc.cph.rbac.baseline;

import com.pranicdoc.cph.rbac.dto.RoleAssignmentRequest;
import com.pranicdoc.cph.rbac.repository.RbacRoleRepository;
import com.pranicdoc.cph.rbac.service.RoleAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Attaches the {@link BaselineRole} to a healer during onboarding.
 *
 * <p>Fail-soft by contract: a missing baseline role or an assignment error
 * (including the "already assigned" case) is logged and swallowed — losing a
 * baseline grant must never abort account creation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BaselineRoleAssigner {

    private final RbacRoleRepository rbacRoleRepository;
    private final RoleAssignmentService roleAssignmentService;

    public void assignTo(UUID healerId) {
        try {
            rbacRoleRepository.findByName(BaselineRole.NAME).ifPresentOrElse(
                    role -> roleAssignmentService.assign(
                            new RoleAssignmentRequest(healerId, role.getId())),
                    () -> log.warn("Baseline role '{}' not found — healer {} onboarded with no baseline grants",
                            BaselineRole.NAME, healerId));
        } catch (Exception e) {
            log.warn("Failed to assign baseline role '{}' to healer {} (onboarding continues): {}",
                    BaselineRole.NAME, healerId, e.getMessage());
        }
    }
}
