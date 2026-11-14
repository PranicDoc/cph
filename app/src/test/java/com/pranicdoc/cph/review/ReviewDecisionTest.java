package com.pranicdoc.cph.review;

import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.review.dto.SubmissionResponse;
import com.pranicdoc.cph.review.event.ReviewEvent;
import com.pranicdoc.cph.review.event.ReviewEventPublisher;
import com.pranicdoc.cph.review.event.ReviewEventType;
import com.pranicdoc.cph.review.model.ReviewRevision;
import com.pranicdoc.cph.review.model.ReviewStage;
import com.pranicdoc.cph.review.model.ReviewStatus;
import com.pranicdoc.cph.review.model.ReviewSubmission;
import com.pranicdoc.cph.review.model.SubmittedVia;
import com.pranicdoc.cph.review.repository.ReviewRevisionRepository;
import com.pranicdoc.cph.review.repository.ReviewSubmissionRepository;
import com.pranicdoc.cph.review.service.ReviewFileStore;
import com.pranicdoc.cph.review.service.ReviewInboxService;
import com.pranicdoc.cph.review.service.ReviewRefusedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Approve and Request changes act on the latest revision of an open submission (pending review or
 * changes requested) assigned to the caller. A stale revision number, a closed submission, or the
 * caller's own case is refused and nothing is written or published.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewDecisionTest {

    @Mock private ReviewSubmissionRepository submissions;
    @Mock private ReviewRevisionRepository revisions;
    @Mock private CaseRepository cases;
    @Mock private HealerRepository healers;
    @Mock private ReviewFileStore files;
    @Mock private SecurityContextUtil security;
    @Mock private ReviewEventPublisher events;

    private ReviewInboxService service;

    private final UUID me = UUID.randomUUID();
    private final UUID healer = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ReviewInboxService(submissions, revisions, cases, healers, files, security, events);
        when(security.getCurrentHealerId()).thenReturn(me);
        when(submissions.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ReviewSubmission submission(UUID healerId, ReviewStatus status, int latestRevision) {
        ReviewSubmission s = ReviewSubmission.builder().caseId(UUID.randomUUID()).healerId(healerId)
                .reviewerId(me).stage(ReviewStage.REVIEWER).status(status)
                .submittedVia(SubmittedVia.MANUAL).build();
        s.setId(UUID.randomUUID());
        when(submissions.findById(s.getId())).thenReturn(Optional.of(s));
        List<ReviewRevision> revs = new java.util.ArrayList<>();
        for (int n = 1; n <= latestRevision; n++) {
            revs.add(ReviewRevision.builder().submissionId(s.getId()).revisionNo(n).fileUrl("review/r" + n + ".pdf")
                    .submittedVia(SubmittedVia.MANUAL).submittedAt(Instant.now()).build());
        }
        when(revisions.findBySubmissionIdOrderByRevisionNoAsc(s.getId())).thenReturn(revs);
        when(revisions.findTopBySubmissionIdOrderByRevisionNoDesc(s.getId()))
                .thenReturn(revs.isEmpty() ? Optional.empty() : Optional.of(revs.get(revs.size() - 1)));
        return s;
    }

    @Test
    void approveLatest_setsApproved() {
        ReviewSubmission s = submission(healer, ReviewStatus.PENDING_REVIEW, 2);

        SubmissionResponse response = service.approve(s.getId(), 2);

        assertThat(response.status()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(s.getStatus()).isEqualTo(ReviewStatus.APPROVED);
        verify(submissions).saveAndFlush(s);
        verify(events).publish(new ReviewEvent(s.getId(), healer, me, ReviewEventType.STATUS_CHANGED, 2));
    }

    @Test
    void requestChangesOnLatest_setsChangesRequested() {
        ReviewSubmission s = submission(healer, ReviewStatus.PENDING_REVIEW, 1);

        SubmissionResponse response = service.requestChanges(s.getId(), 1);

        assertThat(response.status()).isEqualTo(ReviewStatus.CHANGES_REQUESTED);
        verify(events).publish(new ReviewEvent(s.getId(), healer, me, ReviewEventType.STATUS_CHANGED, 1));
    }

    @Test
    void approveAfterChangesRequested_isAllowed() {
        ReviewSubmission s = submission(healer, ReviewStatus.CHANGES_REQUESTED, 1);

        assertThat(service.approve(s.getId(), 1).status()).isEqualTo(ReviewStatus.APPROVED);
    }

    @Test
    void approveStaleRevision_409() {
        ReviewSubmission s = submission(healer, ReviewStatus.PENDING_REVIEW, 3);

        assertThatThrownBy(() -> service.approve(s.getId(), 2))
                .isInstanceOf(ReviewRefusedException.class)
                .hasMessage("A newer version was submitted. Refresh to review it.");
        assertThatThrownBy(() -> service.requestChanges(s.getId(), 2))
                .isInstanceOf(ReviewRefusedException.class)
                .hasMessage("A newer version was submitted. Refresh to review it.");
        assertThat(s.getStatus()).isEqualTo(ReviewStatus.PENDING_REVIEW);
        verify(submissions, never()).saveAndFlush(any());
        verifyNoInteractions(events);
    }

    @Test
    void decisionOnDismissedOrApproved_409() {
        ReviewSubmission dismissed = submission(healer, ReviewStatus.DISMISSED, 1);
        ReviewSubmission approved = submission(healer, ReviewStatus.APPROVED, 1);

        assertThatThrownBy(() -> service.approve(dismissed.getId(), 1)).isInstanceOf(ReviewRefusedException.class);
        assertThatThrownBy(() -> service.requestChanges(approved.getId(), 1)).isInstanceOf(ReviewRefusedException.class);
        verify(submissions, never()).saveAndFlush(any());
        verifyNoInteractions(events);
    }

    @Test
    void reviewerCannotApproveOwnCase() {
        ReviewSubmission own = submission(me, ReviewStatus.PENDING_REVIEW, 1);

        assertThatThrownBy(() -> service.approve(own.getId(), 1)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.requestChanges(own.getId(), 1)).isInstanceOf(AccessDeniedException.class);
        assertThat(own.getStatus()).isEqualTo(ReviewStatus.PENDING_REVIEW);
        verifyNoInteractions(events);
    }

    @Test
    void reassignedReviewerCannotDecide() {
        ReviewSubmission moved = submission(healer, ReviewStatus.PENDING_REVIEW, 1);
        moved.setReviewerId(UUID.randomUUID());

        assertThatThrownBy(() -> service.approve(moved.getId(), 1)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(events);
    }
}
