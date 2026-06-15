package com.pranicdoc.cph.admin;

import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * ABAC (Attribute-Based Access Control) helper for admin endpoints.
 *
 * SUPADM: cross-clinic, no scoping applied.
 * ADMIN:  clinic-scoped — can only read/write data belonging to their own clinicId.
 *
 * Call assertClinicAccess() on every admin endpoint that operates on a specific clinicId
 * or ownerId. SUPADM bypasses silently; ADMIN throws 403 if the clinicId doesn't match.
 */
@Component
@RequiredArgsConstructor
public class AdminAuthz {

    private final SecurityContextUtil securityContextUtil;
    private final HealerRepository healerRepository;

    /**
     * Returns the effective clinicId filter for list endpoints:
     *   SUPADM → null (no filter, see all clinics)
     *   ADMIN  → their own clinicId
     */
    public UUID effectiveClinicFilter() {
        String role = securityContextUtil.getCurrentRole();
        if ("SUPADM".equals(role)) return null;
        return securityContextUtil.getCurrentClinicId();
    }

    /**
     * Asserts the calling user has access to the given clinicId.
     * SUPADM always passes. ADMIN passes only when clinicId matches their own.
     * Throws 403 otherwise.
     */
    public void assertClinicAccess(UUID clinicId) {
        String role = securityContextUtil.getCurrentRole();
        if ("SUPADM".equals(role)) return;
        UUID myClinicId = securityContextUtil.getCurrentClinicId();
        if (!myClinicId.equals(clinicId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: clinic " + clinicId + " is not your clinic");
        }
    }

    /**
     * Asserts the calling ADMIN has access to the given healerId by verifying the healer
     * belongs to their clinic. SUPADM always passes.
     * Throws 403 if the healer does not belong to the admin's clinic.
     */
    public void assertHealerAccess(UUID healerId) {
        String role = securityContextUtil.getCurrentRole();
        if ("SUPADM".equals(role)) return;
        UUID myClinicId = securityContextUtil.getCurrentClinicId();
        if (!healerRepository.existsByIdAndClinicId(healerId, myClinicId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: healer " + healerId + " is not in your clinic");
        }
    }
}
