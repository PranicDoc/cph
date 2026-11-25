package com.pranicdoc.cph.review.service;

import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.rbac.RbacAuthz;
import com.pranicdoc.cph.review.repository.ReviewerAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewAuthzTest {

    @Mock private RbacAuthz rbacAuthz;
    @Mock private SecurityContextUtil securityContextUtil;
    @Mock private ReviewerAssignmentRepository assignments;

    @InjectMocks private ReviewAuthz reviewAuthz;

    private final UUID caller = UUID.randomUUID();

    @Test
    void canReviewByPermission() {
        when(rbacAuthz.hasPermission("CASE", "REVIEW")).thenReturn(true);

        assertThat(reviewAuthz.canReview()).isTrue();
        verifyNoInteractions(assignments);
    }

    @Test
    void canReviewByAssignment() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(caller);
        when(assignments.existsByReviewerId(caller)).thenReturn(true);

        assertThat(reviewAuthz.canReview()).isTrue();
    }

    @Test
    void cannotReviewWithNeitherPermissionNorAssignment() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(caller);
        when(assignments.existsByReviewerId(caller)).thenReturn(false);

        assertThat(reviewAuthz.canReview()).isFalse();
    }

    @Test
    void cannotReviewWithoutATenantContext() {
        when(securityContextUtil.getCurrentHealerId()).thenThrow(new IllegalStateException("No tenant context"));

        assertThat(reviewAuthz.canReview()).isFalse();
        verify(assignments, never()).existsByReviewerId(any());
    }

    @Test
    void cannotReviewWhenThePrincipalHasNoHealer() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(null);

        assertThat(reviewAuthz.canReview()).isFalse();
        verify(assignments, never()).existsByReviewerId(any());
    }
}
