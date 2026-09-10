package com.pranicdoc.cph.rbac.repository;

import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;
import com.pranicdoc.cph.rbac.model.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    /** The per-role grant set Phase 36's cached lookup and Phase 39's permission-diff view both read. */
    List<RolePermission> findAllByRoleId(UUID roleId);

    /**
     * Phase 37's RoleManagerGuard — every role currently granting a given (resource, verb) pair.
     * role_permission's unique (role_id, resource, operation) constraint means this returns at most
     * one row per role, so its results need no de-duplication before being fed to countByRoleIdIn.
     */
    List<RolePermission> findAllByResourceAndOperation(Resource resource, Operation operation);
}
