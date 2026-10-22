package com.pranicdoc.cph.rbac.service;

import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.rbac.RbacAuthz;
import com.pranicdoc.cph.rbac.dto.MyPermissionsResponse;
import com.pranicdoc.cph.rbac.model.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The signed-in user's own permissions, read with the same rules as {@link RbacAuthz}:
 * the super admin may do everything, everyone else has the union of their roles' grants.
 */
@Service
@RequiredArgsConstructor
public class MyPermissionsService {

    private final RbacAuthz rbacAuthz;
    private final SecurityContextUtil securityContextUtil;
    private final RolePermissionLookupService lookupService;

    public MyPermissionsResponse current() {
        if (rbacAuthz.isSuperAdmin()) {
            return new MyPermissionsResponse(true, Map.of());
        }
        Map<String, List<String>> grants = new TreeMap<>();
        lookupService.effectiveGrants(securityContextUtil.getCurrentHealerId()).forEach((resource, operations) ->
                grants.put(resource.name(), operations.stream()
                        .sorted(Comparator.comparingInt(Operation::ordinal))
                        .map(Operation::name)
                        .toList()));
        return new MyPermissionsResponse(false, grants);
    }
}
