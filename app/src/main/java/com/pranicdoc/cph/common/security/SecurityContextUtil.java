package com.pranicdoc.cph.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Helper to extract tenant/healer info from the SecurityContext.
 * After JwtAuthFilter authenticates, these values are available.
 */
@Component
public class SecurityContextUtil {

    /**
     * Returns the clinicId for the currently authenticated healer.
     */
    public UUID getCurrentClinicId() {
        return getTenantDetails().clinicId();
    }

    /**
     * Returns the healerId for the currently authenticated healer.
     */
    public UUID getCurrentHealerId() {
        return getTenantDetails().healerId();
    }

    /**
     * Returns the clinic code (e.g. "CHNY01") for the current healer.
     */
    public String getCurrentClinicCode() {
        return getTenantDetails().clinicCode();
    }

    /**
     * Returns the role string for the currently authenticated healer.
     */
    public String getCurrentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && !auth.getAuthorities().isEmpty()) {
            // Strip "ROLE_" prefix
            String authority = auth.getAuthorities().iterator().next().getAuthority();
            return authority.startsWith("ROLE_") ? authority.substring(5) : authority;
        }
        throw new IllegalStateException("No role found in SecurityContext");
    }

    private TenantDetails getTenantDetails() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof TenantDetails details) {
            return details;
        }
        throw new IllegalStateException("No tenant context found in SecurityContext — is the user authenticated?");
    }

    /**
     * Record carrying tenant details set by JwtAuthFilter.
     */
    public record TenantDetails(UUID clinicId, UUID healerId, String clinicCode) {}
}
