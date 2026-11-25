package com.pranicdoc.cph.review.service;

import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.enums.Role;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;
import com.pranicdoc.cph.rbac.service.RolePermissionLookupService;
import com.pranicdoc.cph.review.dto.HealerOption;
import com.pranicdoc.cph.review.dto.ReviewerAssignmentResponse;
import com.pranicdoc.cph.review.model.ReviewerAssignment;
import com.pranicdoc.cph.review.repository.ReviewerAssignmentRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Which instructor reviews which healer. SUPADM-only administration; the HTTP gate lives in
 * the controller. Any active account can be assigned, because being assigned itself grants review
 * access ({@link ReviewAuthz}). Only a SUPADM account may be its own reviewer; this service is the sole
 * guard of that rule (the database does not constrain it). The picker's "has Review permission" flag uses
 * the real role grants (RolePermissionLookupService), not MyPermissionsService, because SUPADM accounts
 * hold every permission there by a bypass.
 */
@Service
public class ReviewerAssignmentService {

    private final ReviewerAssignmentRepository assignments;
    private final HealerRepository healers;
    private final RolePermissionLookupService permissions;
    private final ReviewSubmissionService reviewSubmissions;

    /** {@code @Lazy}: ReviewSubmissionService reads {@link #reviewerFor}, so the two would otherwise be a cycle. */
    public ReviewerAssignmentService(ReviewerAssignmentRepository assignments, HealerRepository healers,
                                     RolePermissionLookupService permissions,
                                     @Lazy ReviewSubmissionService reviewSubmissions) {
        this.assignments = assignments;
        this.healers = healers;
        this.permissions = permissions;
        this.reviewSubmissions = reviewSubmissions;
    }

    @Transactional(readOnly = true)
    public Optional<UUID> reviewerFor(UUID healerId) {
        return assignments.findByHealerId(healerId).map(ReviewerAssignment::getReviewerId);
    }

    @Transactional(readOnly = true)
    public List<ReviewerAssignmentResponse> list() {
        List<ReviewerAssignment> all = assignments.findAll();
        Set<UUID> ids = new HashSet<>();
        all.forEach(a -> {
            ids.add(a.getHealerId());
            ids.add(a.getReviewerId());
        });
        Map<UUID, Healer> byId = healers.findAllById(ids).stream()
                .collect(Collectors.toMap(Healer::getId, Function.identity()));
        Map<UUID, Long> approved = reviewSubmissions.approvedCounts(
                all.stream().map(ReviewerAssignment::getHealerId).toList());
        return all.stream()
                .map(a -> toResponse(a, byId.get(a.getHealerId()), byId.get(a.getReviewerId()),
                        approved.getOrDefault(a.getHealerId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HealerOption> eligibleReviewers() {
        return healers.findByActiveTrue().stream()
                .sorted(Comparator.comparing(Healer::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(h -> new HealerOption(h.getId(), h.getFullName(), h.getEmail(),
                        permissions.hasPermission(h.getId(), Resource.CASE, Operation.REVIEW),
                        h.getRole() == Role.SUPADM))
                .toList();
    }

    @Auditable(action = "ASSIGN_REVIEWER")
    @Transactional
    public ReviewerAssignmentResponse assign(UUID healerId, UUID reviewerId) {
        Healer healer = healers.findById(healerId)
                .orElseThrow(() -> new ResourceNotFoundException("Healer", healerId));
        Healer reviewer = healers.findById(reviewerId)
                .orElseThrow(() -> new ResourceNotFoundException("Reviewer", reviewerId));
        if (!reviewer.isActive()) {
            throw new IllegalArgumentException("Reviewer must be an active healer");
        }
        if (healerId.equals(reviewerId) && reviewer.getRole() != Role.SUPADM) {
            throw new IllegalArgumentException("A healer cannot be their own reviewer");
        }
        ReviewerAssignment link = assignments.findByHealerId(healerId)
                .orElseGet(() -> ReviewerAssignment.builder().healerId(healerId).build());
        link.setReviewerId(reviewerId);
        ReviewerAssignment saved = assignments.save(link);
        reviewSubmissions.reassignOpen(healerId, reviewerId);
        return toResponse(saved, healer, reviewer, reviewSubmissions.approvedCount(healerId));
    }

    @Auditable(action = "UNASSIGN_REVIEWER")
    @Transactional
    public void unassign(UUID healerId) {
        assignments.findByHealerId(healerId).ifPresent(assignments::delete);
    }

    private static ReviewerAssignmentResponse toResponse(ReviewerAssignment a, Healer healer, Healer reviewer,
                                                        long approvedCount) {
        return new ReviewerAssignmentResponse(
                a.getHealerId(), healer == null ? null : healer.getFullName(),
                a.getReviewerId(), reviewer == null ? null : reviewer.getFullName(), approvedCount);
    }
}
