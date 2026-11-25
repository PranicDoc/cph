package com.pranicdoc.cph.review.controller;

import com.pranicdoc.cph.admin.AdminAuthz;
import com.pranicdoc.cph.common.feature.RequiresCaseReview;
import com.pranicdoc.cph.review.dto.AdminReviewItemResponse;
import com.pranicdoc.cph.review.model.ReviewStatus;
import com.pranicdoc.cph.review.service.ReviewInboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Every reviewer-stage submission, whoever it is assigned to. SUPADM-only (no RBAC permission opens it),
 * enforced with {@link AdminAuthz#assertSuperAdmin()}; SecurityConfig also refuses patient tokens. Opening
 * and deciding a listed submission goes through the reviewer routes ({@link ReviewerController}).
 */
@RestController
@RequiresCaseReview
@RequestMapping("/api/v1/admin/reviews")
@RequiredArgsConstructor
public class AdminReviewController {

    private final ReviewInboxService service;
    private final AdminAuthz adminAuthz;

    @GetMapping
    public List<AdminReviewItemResponse> list(@RequestParam(required = false) ReviewStatus status,
                                              @RequestParam(required = false) UUID reviewerId,
                                              @RequestParam(required = false) UUID healerId) {
        adminAuthz.assertSuperAdmin();
        return service.allReviews(status, reviewerId, healerId);
    }
}
