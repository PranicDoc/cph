package com.pranicdoc.cph.rbac.repository;

import com.pranicdoc.cph.rbac.model.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    /** The per-role grant set Phase 36's cached lookup and Phase 39's permission-diff view both read. */
    List<RolePermission> findAllByRoleId(UUID roleId);
}
