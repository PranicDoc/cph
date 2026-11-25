package com.pranicdoc.cph.review.service;

import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.rbac.RbacAuthz;
import com.pranicdoc.cph.review.repository.ReviewerAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The {@code @PreAuthorize} gate of the instructor routes ({@code @reviewAuthz.canReview()}): open to a
 * caller holding CASE:REVIEW (SUPADM included, by {@link RbacAuthz}'s bypass) or assigned as at least one
 * healer's reviewer. Read live on every request, so an unassignment takes effect immediately. It only
 * opens the routes; which submissions the caller may see is the services' row-level check. Patient tokens
 * never get here: SecurityConfig refuses them on these routes.
 */
@Component("reviewAuthz")
@RequiredArgsConstructor
public class ReviewAuthz {

    private final RbacAuthz rbacAuthz;
    private final SecurityContextUtil securityContextUtil;
    private final ReviewerAssignmentRepository assignments;

    /** Fails closed: false, never an exception, without a healer principal. */
    public boolean canReview() {
        if (rbacAuthz.hasPermission("CASE", "REVIEW")) {
            return true;
        }
        UUID healerId;
        try {
            healerId = securityContextUtil.getCurrentHealerId();
        } catch (IllegalStateException noTenantInContext) {
            return false;
        }
        return healerId != null && assignments.existsByReviewerId(healerId);
    }
}
