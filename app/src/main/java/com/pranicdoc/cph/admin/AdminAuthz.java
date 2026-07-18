package com.pranicdoc.cph.admin;

import com.pranicdoc.cph.common.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * ABAC (Attribute-Based Access Control) helper for admin endpoints.
 *
 * Solo-healer world (D-26-02): the only remaining privilege tier is SUPADM
 * (cross-healer, unscoped) vs. an ordinary healer (own healerId only). There
 * is no clinic-scoped ADMIN tier anymore — the per-clinic ABAC this class used
 * to implement was retired as part of the clinic-removal cutover.
 *
 * SUPADM bypasses silently; an ordinary healer is only permitted to act on
 * their own healerId and throws 403 otherwise.
 */
@Component
@RequiredArgsConstructor
public class AdminAuthz {

    private final SecurityContextUtil securityContextUtil;

    /**
     * Returns the effective healerId filter for list endpoints:
     *   SUPADM  → null (no filter, see all healers)
     *   healer  → their own healerId
     */
    public UUID healerScopeFilter() {
        String role = securityContextUtil.getCurrentRole();
        if ("SUPADM".equals(role)) return null;
        return securityContextUtil.getCurrentHealerId();
    }

    /**
     * Asserts the calling user has access to the given healerId.
     * SUPADM always passes. An ordinary healer passes only when healerId
     * matches their own. Throws 403 otherwise.
     */
    public void assertHealerAccess(UUID healerId) {
        String role = securityContextUtil.getCurrentRole();
        if ("SUPADM".equals(role)) return;
        if (!healerId.equals(securityContextUtil.getCurrentHealerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: healer " + healerId + " is not you");
        }
    }
}
