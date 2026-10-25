package com.pranicdoc.cph.rbac.service;

import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.masterdata.service.MasterDataElevationService;
import com.pranicdoc.cph.rbac.RbacAuthz;
import com.pranicdoc.cph.rbac.dto.MyPermissionsResponse;
import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The signed-in user's own permissions, read with the same rules as {@link RbacAuthz}: the
 * super admin holds every supported verb of every resource, everyone else the union of their
 * roles' grants. Returned as plain grants so the UI only ever checks permissions.
 */
@Service
@RequiredArgsConstructor
public class MyPermissionsService {

    /**
     * The super admin's grants: every supported verb of every resource. Built once from the
     * {@link Resource} enum when the class loads; adding a resource is a code change and a
     * deploy anyway, so there is nothing to recompute per request.
     */
    private static final Map<String, List<String>> ALL_GRANTS = allGrants();

    private final RbacAuthz rbacAuthz;
    private final SecurityContextUtil securityContextUtil;
    private final RolePermissionLookupService lookupService;
    private final MasterDataElevationService elevationService;

    public MyPermissionsResponse current() {
        if (rbacAuthz.isSuperAdmin()) {
            return new MyPermissionsResponse(ALL_GRANTS, Map.of());
        }
        UUID healerId = securityContextUtil.getCurrentHealerId();
        Map<String, List<String>> grants = new TreeMap<>();
        lookupService.effectiveGrants(healerId)
                .forEach((resource, operations) -> grants.put(resource.name(), names(operations)));
        return new MyPermissionsResponse(grants, elevationService.activeFor(healerId));
    }

    private static Map<String, List<String>> allGrants() {
        Map<String, List<String>> grants = new TreeMap<>();
        for (Resource resource : Resource.values()) {
            grants.put(resource.name(), names(resource.getSupportedOperations()));
        }
        return Collections.unmodifiableMap(grants);
    }

    private static List<String> names(Collection<Operation> operations) {
        return operations.stream()
                .sorted(Comparator.comparingInt(Operation::ordinal))
                .map(Operation::name)
                .toList();
    }
}
