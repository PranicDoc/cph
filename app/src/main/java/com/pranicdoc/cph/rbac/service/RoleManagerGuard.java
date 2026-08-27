package com.pranicdoc.cph.rbac.service;

import com.pranicdoc.cph.rbac.model.Permission;
import com.pranicdoc.cph.rbac.model.Resource;
import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.repository.RolePermissionRepository;
import com.pranicdoc.cph.rbac.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleManagerGuard {

    private static final Permission ROLE_MANAGEMENT_PERMISSION =
            new Permission(Resource.ROLES, Operation.UPDATE);

    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;

    public boolean roleGrantsRoleManagement(UUID roleId) {
        throw new UnsupportedOperationException("RED — not implemented yet");
    }

    public boolean grantSetIncludesRoleManagement(List<Permission> permissions) {
        throw new UnsupportedOperationException("RED — not implemented yet");
    }

    public long countRoleManagerPairs() {
        throw new UnsupportedOperationException("RED — not implemented yet");
    }

    public void assertActionPreservesRoleManagement(
            boolean roleCurrentlyGrantsRoleManagement, long pairsAboutToBeRemoved) {
        throw new UnsupportedOperationException("RED — not implemented yet");
    }
}
