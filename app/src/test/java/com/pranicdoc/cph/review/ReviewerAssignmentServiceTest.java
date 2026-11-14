package com.pranicdoc.cph.review;

import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;
import com.pranicdoc.cph.rbac.service.RolePermissionLookupService;
import com.pranicdoc.cph.review.dto.HealerOption;
import com.pranicdoc.cph.review.dto.ReviewerAssignmentResponse;
import com.pranicdoc.cph.review.model.ReviewerAssignment;
import com.pranicdoc.cph.review.repository.ReviewerAssignmentRepository;
import com.pranicdoc.cph.review.service.ReviewSubmissionService;
import com.pranicdoc.cph.review.service.ReviewerAssignmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewerAssignmentServiceTest {

    @Mock private ReviewerAssignmentRepository assignments;
    @Mock private HealerRepository healers;
    @Mock private RolePermissionLookupService permissions;
    @Mock private ReviewSubmissionService reviewSubmissions;

    @InjectMocks private ReviewerAssignmentService service;

    private final UUID healerId = UUID.randomUUID();
    private final UUID reviewerId = UUID.randomUUID();

    private Healer healer(UUID id, String name, boolean active) {
        Healer h = Healer.builder().fullName(name).email(name + "@x.org").active(active).build();
        h.setId(id);
        return h;
    }

    @Test
    void assign_rejectsReviewerWithoutCaseReview() {
        when(healers.findById(healerId)).thenReturn(Optional.of(healer(healerId, "h", true)));
        when(healers.findById(reviewerId)).thenReturn(Optional.of(healer(reviewerId, "r", true)));
        when(permissions.hasPermission(reviewerId, Resource.CASE, Operation.REVIEW)).thenReturn(false);

        assertThatThrownBy(() -> service.assign(healerId, reviewerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Reviewer must hold the CASE:REVIEW permission");
        verify(assignments, never()).save(any());
    }

    @Test
    void assign_rejectsSelfLink() {
        assertThatThrownBy(() -> service.assign(healerId, healerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A healer cannot be their own reviewer");
        verify(assignments, never()).save(any());
    }

    @Test
    void assign_replacesExistingLink() {
        ReviewerAssignment existing = ReviewerAssignment.builder()
                .healerId(healerId).reviewerId(UUID.randomUUID()).build();
        when(healers.findById(healerId)).thenReturn(Optional.of(healer(healerId, "h", true)));
        when(healers.findById(reviewerId)).thenReturn(Optional.of(healer(reviewerId, "r", true)));
        when(permissions.hasPermission(reviewerId, Resource.CASE, Operation.REVIEW)).thenReturn(true);
        when(assignments.findByHealerId(healerId)).thenReturn(Optional.of(existing));
        when(assignments.save(any(ReviewerAssignment.class))).thenAnswer(i -> i.getArgument(0));

        ReviewerAssignmentResponse response = service.assign(healerId, reviewerId);

        assertThat(existing.getReviewerId()).isEqualTo(reviewerId);
        assertThat(response.healerId()).isEqualTo(healerId);
        assertThat(response.reviewerId()).isEqualTo(reviewerId);
        assertThat(response.reviewerName()).isEqualTo("r");
        verify(assignments).save(existing);
    }

    @Test
    void assign_movesOpenSubmissions() {
        when(healers.findById(healerId)).thenReturn(Optional.of(healer(healerId, "h", true)));
        when(healers.findById(reviewerId)).thenReturn(Optional.of(healer(reviewerId, "r", true)));
        when(permissions.hasPermission(reviewerId, Resource.CASE, Operation.REVIEW)).thenReturn(true);
        when(assignments.findByHealerId(healerId)).thenReturn(Optional.empty());
        when(assignments.save(any(ReviewerAssignment.class))).thenAnswer(i -> i.getArgument(0));

        service.assign(healerId, reviewerId);

        verify(reviewSubmissions).reassignOpen(healerId, reviewerId);
    }

    @Test
    void assign_rejectsInactiveReviewer() {
        when(healers.findById(healerId)).thenReturn(Optional.of(healer(healerId, "h", true)));
        when(healers.findById(reviewerId)).thenReturn(Optional.of(healer(reviewerId, "r", false)));

        assertThatThrownBy(() -> service.assign(healerId, reviewerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Reviewer must be an active healer");
    }

    @Test
    void eligibleReviewers_returnsOnlyActiveHealersHoldingCaseReview() {
        UUID holder = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        when(healers.findByActiveTrue()).thenReturn(List.of(healer(holder, "holder", true), healer(other, "other", true)));
        when(permissions.hasPermission(holder, Resource.CASE, Operation.REVIEW)).thenReturn(true);
        when(permissions.hasPermission(other, Resource.CASE, Operation.REVIEW)).thenReturn(false);

        List<HealerOption> options = service.eligibleReviewers();

        assertThat(options).extracting(HealerOption::id).containsExactly(holder);
    }

    @Test
    void reviewerFor_returnsLinkedReviewer() {
        when(assignments.findByHealerId(healerId)).thenReturn(Optional.of(
                ReviewerAssignment.builder().healerId(healerId).reviewerId(reviewerId).build()));
        assertThat(service.reviewerFor(healerId)).contains(reviewerId);
    }

    @Test
    void list_fillsApprovedCountFromOneBatchedLookup() {
        UUID otherHealer = UUID.randomUUID();
        when(assignments.findAll()).thenReturn(List.of(
                ReviewerAssignment.builder().healerId(healerId).reviewerId(reviewerId).build(),
                ReviewerAssignment.builder().healerId(otherHealer).reviewerId(reviewerId).build()));
        when(healers.findAllById(any())).thenReturn(List.of(
                healer(healerId, "h", true), healer(otherHealer, "o", true), healer(reviewerId, "r", true)));
        when(reviewSubmissions.approvedCounts(any())).thenReturn(java.util.Map.of(healerId, 3L));

        List<ReviewerAssignmentResponse> rows = service.list();

        assertThat(rows).extracting(ReviewerAssignmentResponse::healerId, ReviewerAssignmentResponse::approvedCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(healerId, 3L),
                        org.assertj.core.groups.Tuple.tuple(otherHealer, 0L));
        verify(reviewSubmissions).approvedCounts(any());
        verify(reviewSubmissions, never()).approvedCount(any());
    }

    @Test
    void unassign_removesLink() {
        ReviewerAssignment existing = ReviewerAssignment.builder().healerId(healerId).reviewerId(reviewerId).build();
        when(assignments.findByHealerId(healerId)).thenReturn(Optional.of(existing));
        service.unassign(healerId);
        verify(assignments).delete(existing);
    }
}
