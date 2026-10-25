package com.pranicdoc.cph.rbac.baseline;

import com.pranicdoc.cph.rbac.model.RbacRole;
import com.pranicdoc.cph.rbac.model.RolePermission;
import com.pranicdoc.cph.rbac.repository.RbacRoleRepository;
import com.pranicdoc.cph.rbac.repository.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Ensures the {@link BaselineRole} exists on startup (idempotent). Writes through the
 * repositories directly rather than {@code RbacRoleService.create} — this is a system
 * action with no audit actor, and the seeder does its own existence check.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BaselineRoleSeeder implements CommandLineRunner {

    private final RbacRoleRepository rbacRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @Override
    public void run(String... args) {
        if (rbacRoleRepository.findByName(BaselineRole.NAME).isPresent()) {
            log.info("Baseline role '{}' already present, skipping seed", BaselineRole.NAME);
            return;
        }

        log.info("Seeding baseline role '{}'...", BaselineRole.NAME);

        RbacRole role = rbacRoleRepository.save(RbacRole.builder()
                .name(BaselineRole.NAME)
                .description(BaselineRole.DESCRIPTION)
                .build());

        UUID roleId = role.getId();
        List<RolePermission> grants = BaselineRole.GRANTS.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(operation -> RolePermission.builder()
                                .roleId(roleId)
                                .resource(entry.getKey())
                                .operation(operation)
                                .build()))
                .toList();
        rolePermissionRepository.saveAll(grants);

        log.info("Seeded baseline role '{}' with {} grant rows", BaselineRole.NAME, grants.size());
    }
}
