package com.pranicdoc.cph.review.controller;

import com.pranicdoc.cph.admin.AdminAuthz;
import com.pranicdoc.cph.review.dto.AssignReviewerRequest;
import com.pranicdoc.cph.review.dto.HealerOption;
import com.pranicdoc.cph.review.dto.ReviewerAssignmentResponse;
import com.pranicdoc.cph.review.service.ReviewerAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * SUPADM links healers to the instructors who review their cases. Every route is SUPADM-only
 * (no RBAC permission opens it), enforced with {@link AdminAuthz#assertSuperAdmin()}.
 */
@RestController
@RequestMapping("/api/v1/admin/reviewer-assignments")
@RequiredArgsConstructor
public class AdminReviewerAssignmentController {

    private final ReviewerAssignmentService service;
    private final AdminAuthz adminAuthz;

    @GetMapping
    public List<ReviewerAssignmentResponse> list() {
        adminAuthz.assertSuperAdmin();
        return service.list();
    }

    @GetMapping("/eligible-reviewers")
    public List<HealerOption> eligibleReviewers() {
        adminAuthz.assertSuperAdmin();
        return service.eligibleReviewers();
    }

    @PutMapping("/{healerId}")
    public ReviewerAssignmentResponse assign(@PathVariable UUID healerId,
                                             @Valid @RequestBody AssignReviewerRequest request) {
        adminAuthz.assertSuperAdmin();
        return service.assign(healerId, request.reviewerId());
    }

    @DeleteMapping("/{healerId}")
    public ResponseEntity<Void> unassign(@PathVariable UUID healerId) {
        adminAuthz.assertSuperAdmin();
        service.unassign(healerId);
        return ResponseEntity.noContent().build();
    }
}
