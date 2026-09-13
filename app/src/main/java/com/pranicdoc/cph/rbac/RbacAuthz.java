package com.pranicdoc.cph.rbac;

import com.pranicdoc.cph.common.enums.MasterdataType;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;
import com.pranicdoc.cph.rbac.service.RolePermissionLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
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
     * compile-time string literals baked into an annotation at every call
     * site in this codebase except through {@link #hasImportPermission},
     * which resolves {@code resourceKey} from parsed request data before
     * delegating here — never a raw user string reaching {@link
     * Resource#valueOf} directly.
     *
     * <p><b>Fail-closed contract:</b> this method must never let an
     * exception escape. An exception thrown while a {@code @PreAuthorize}
     * SpEL expression is being evaluated surfaces to the caller as an HTTP
     * 500 — an unhandled state, not a denial. An incomplete security context
     * or a malformed resource/verb literal is therefore caught here and
     * translated into a plain {@code false} return, never a propagated
     * exception. (Contrast {@link #hasImportPermission}, which deliberately
     * does NOT extend this contract to an unrecognized {@code type}.)
     */
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

    /**
     * D-13: fixed, exhaustive, compile-time type-to-resource map for the
     * unified masterdata import endpoint (Phase 02). {@code
     * PROTOCOL_COLOR_MAPPING} deliberately reuses {@code
     * MASTER_DATA_PROTOCOLS} (no dedicated resource exists for it) —
     * consistent with how its existing CRUD endpoints are already gated on
     * {@code MasterDataController}. Lives here, not on {@code
     * MasterDataImportController}, because resolving a masterdata type to an
     * RBAC {@link Resource} is an authorization concern, not a web-layer one.
     */
    private static final Map<MasterdataType, Resource> MASTERDATA_TYPE_TO_RESOURCE = Map.of(
            MasterdataType.PROTOCOL, Resource.MASTER_DATA_PROTOCOLS,
            MasterdataType.PRANA_COLOR, Resource.MASTER_DATA_COLORS,
            MasterdataType.CHAKRA, Resource.MASTER_DATA_CHAKRAS,
            MasterdataType.PROTOCOL_COLOR_MAPPING, Resource.MASTER_DATA_PROTOCOLS);

    /**
     * Decision function for the unified masterdata import endpoint,
     * referenced from {@code @PreAuthorize} SpEL as {@code
     * @rbacAuthz.hasImportPermission(#type, 'CREATE')} on {@code
     * MasterDataImportController.importMasterData}, and called again in
     * that method's body with verb {@code "ADMIN"} to resolve {@code
     * isSuperAdmin} for the row-ownership override (PD-40-04) — one decision
     * function, not two call sites maintaining the same type→resource
     * knowledge.
     *
     * <p>D-12's critical ordering, never reordered: (1) parse {@code type}
     * via {@link MasterdataType#fromParam} — an unknown word throws {@link
     * IllegalArgumentException} (400 via {@code GlobalExceptionHandler})
     * before any RBAC lookup ever runs; (2) resolve the resource from the
     * fixed {@link #MASTERDATA_TYPE_TO_RESOURCE} map — never {@link
     * Resource#valueOf} on a raw user string; (3) delegate to {@link
     * #hasPermission(String, String)} for the actual permission check.
     *
     * <p><b>Deliberately does not extend {@code hasPermission}'s
     * fail-closed contract:</b> an unrecognized {@code type} is allowed to
     * throw {@code IllegalArgumentException} out of this method (surfacing
     * as 400, not 403 or 500) — that is the entire point of D-12's ordering,
     * distinguishing "this isn't a real masterdata type" from "you don't
     * have permission for this real one." Swallowing it here into a plain
     * {@code false} would collapse that distinction and regress the
     * behavior {@code MasterDataImportControllerAuthorizationTest}'s
     * {@code unknownType_returns400_withZeroLookupServiceInteraction} pins.
     */
    public boolean hasImportPermission(String type, String verb) {
        MasterdataType kind = MasterdataType.fromParam(type);
        Resource resource = MASTERDATA_TYPE_TO_RESOURCE.get(kind);
        return hasPermission(resource.name(), verb);
    }
}
