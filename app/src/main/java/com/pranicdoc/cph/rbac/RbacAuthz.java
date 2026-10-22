package com.pranicdoc.cph.rbac;

import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;
import com.pranicdoc.cph.rbac.service.RolePermissionLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * RbacAuthz — the {@code @PreAuthorize} bean-reference enforcement point for
 * every dynamic RBAC decision in this application (RBAC-02). Its default
 * Spring bean name is {@code rbacAuthz} (this class sits at the {@code rbac}
 * package root, not a sub-package), which is exactly the identifier
 * controller-layer SpEL expressions reference.
 *
 * The SUPADM bypass lives here, and only here: it is the first statement of
 * {@link #hasPermission(String, String)}, before any other work and before
 * any database read. No other class anywhere under the {@code rbac} package
 * may contain the SUPADM role literal — a source-sweep test in
 * {@code RbacAuthzTest} enforces that as a build gate. This is RBAC-04's
 * unconditional bypass: a SUPADM caller is permitted for every resource and
 * verb without ever needing an {@code rbac_role} row or a {@code user_role}
 * grant.
 *
 * Every other caller is resolved by delegating to
 * {@link RolePermissionLookupService}, which reads {@code user_role} and
 * {@code role_permission} live on every call.
 *
 * Fail-closed contract: this method must never let an exception escape.
 * An exception thrown while a {@code @PreAuthorize} SpEL expression is being
 * evaluated surfaces to the caller as an HTTP 500 — an unhandled state, not
 * a denial. An incomplete security context or a malformed resource/verb
 * literal is therefore caught here and translated into a plain
 * {@code false} return, never a propagated exception.
 */
@Component
@RequiredArgsConstructor
public class RbacAuthz {

    private static final String ROLE_SUPADM = "SUPADM";

    private final SecurityContextUtil securityContextUtil;
    private final RolePermissionLookupService lookupService;

    /**
     * Decision function referenced from {@code @PreAuthorize} SpEL as
     * {@code @rbacAuthz.hasPermission(resourceKey, verb)}. Both arguments are
     * always compile-time string literals baked into an annotation in this
     * codebase — never request-derived data.
     */
    /** True for SUPADM, who passes every {@link #hasPermission} check; false when no role is on the context. */
    public boolean isSuperAdmin() {
        try {
            return ROLE_SUPADM.equals(securityContextUtil.getCurrentRole());
        } catch (IllegalStateException noRoleInContext) {
            return false;
        }
    }

    public boolean hasPermission(String resourceKey, String verb) {
        try {
            if (ROLE_SUPADM.equals(securityContextUtil.getCurrentRole())) {
                return true;
            }
        } catch (IllegalStateException noRoleInContext) {
            // No authenticated principal / no authority on the SecurityContext.
            // Fail closed rather than let this propagate out of SpEL evaluation.
            return false;
        }

        Resource resource;
        Operation operation;
        try {
            // Reachable only via a typo in this codebase's own @PreAuthorize
            // annotation, never via caller input — still must fail closed.
            resource = Resource.valueOf(resourceKey);
            operation = Operation.valueOf(verb);
        } catch (IllegalArgumentException badLiteral) {
            return false;
        }

        UUID healerId;
        try {
            healerId = securityContextUtil.getCurrentHealerId();
        } catch (IllegalStateException noTenantInContext) {
            return false;
        }
        if (healerId == null) {
            return false;
        }

        return lookupService.hasPermission(healerId, resource, operation);
    }
}
