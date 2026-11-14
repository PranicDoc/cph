package com.pranicdoc.cph.review.service;

import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;
import com.pranicdoc.cph.rbac.service.RolePermissionLookupService;
import com.pranicdoc.cph.review.dto.HealerOption;
import com.pranicdoc.cph.review.dto.ReviewerAssignmentResponse;
import com.pranicdoc.cph.review.model.ReviewerAssignment;
import com.pranicdoc.cph.review.repository.ReviewerAssignmentRepository;
import lombok.RequiredArgsConstructor;
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
 * the controller. Eligibility uses the real role grants (RolePermissionLookupService), not
 * MyPermissionsService, because SUPADM accounts hold every permission there by a bypass.
 */
@Service
@RequiredArgsConstructor
public class ReviewerAssignmentService {

    private final ReviewerAssignmentRepository assignments;
    private final HealerRepository healers;
    private final RolePermissionLookupService permissions;

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
        return all.stream()
                .map(a -> toResponse(a, byId.get(a.getHealerId()), byId.get(a.getReviewerId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HealerOption> eligibleReviewers() {
        return healers.findByActiveTrue().stream()
                .filter(h -> permissions.hasPermission(h.getId(), Resource.CASE, Operation.REVIEW))
                .sorted(Comparator.comparing(Healer::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(h -> new HealerOption(h.getId(), h.getFullName(), h.getEmail()))
                .toList();
    }

    @Auditable(action = "ASSIGN_REVIEWER")
    @Transactional
    public ReviewerAssignmentResponse assign(UUID healerId, UUID reviewerId) {
        if (healerId.equals(reviewerId)) {
            throw new IllegalArgumentException("A healer cannot be their own reviewer");
        }
        Healer healer = healers.findById(healerId)
                .orElseThrow(() -> new IllegalArgumentException("Healer not found"));
        Healer reviewer = healers.findById(reviewerId)
                .orElseThrow(() -> new IllegalArgumentException("Reviewer not found"));
        if (!reviewer.isActive()) {
            throw new IllegalArgumentException("Reviewer must be an active healer");
        }
        if (!permissions.hasPermission(reviewerId, Resource.CASE, Operation.REVIEW)) {
            throw new IllegalArgumentException("Reviewer must hold the CASE:REVIEW permission");
        }
        ReviewerAssignment link = assignments.findByHealerId(healerId)
                .orElseGet(() -> ReviewerAssignment.builder().healerId(healerId).build());
        link.setReviewerId(reviewerId);
        // S3 will move any open submissions of this healer to the new reviewer here.
        return toResponse(assignments.save(link), healer, reviewer);
    }

    @Auditable(action = "UNASSIGN_REVIEWER")
    @Transactional
    public void unassign(UUID healerId) {
        assignments.findByHealerId(healerId).ifPresent(assignments::delete);
    }

    private static ReviewerAssignmentResponse toResponse(ReviewerAssignment a, Healer healer, Healer reviewer) {
        return new ReviewerAssignmentResponse(
                a.getHealerId(), healer == null ? null : healer.getFullName(),
                a.getReviewerId(), reviewer == null ? null : reviewer.getFullName());
    }
}
