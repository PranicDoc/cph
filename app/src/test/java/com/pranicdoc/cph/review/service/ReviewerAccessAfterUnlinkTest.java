package com.pranicdoc.cph.review.service;

import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.rbac.RbacAuthz;
import com.pranicdoc.cph.review.dto.CommentRequest;
import com.pranicdoc.cph.review.dto.InboxItemResponse;
import com.pranicdoc.cph.review.event.ReviewEventPublisher;
import com.pranicdoc.cph.review.model.AuthorSide;
import com.pranicdoc.cph.review.model.ReviewComment;
import com.pranicdoc.cph.review.model.ReviewRevision;
import com.pranicdoc.cph.review.model.ReviewStage;
import com.pranicdoc.cph.review.model.ReviewStatus;
import com.pranicdoc.cph.review.model.ReviewSubmission;
import com.pranicdoc.cph.review.model.ReviewerAssignment;
import com.pranicdoc.cph.review.model.SubmittedVia;
import com.pranicdoc.cph.review.repository.ReviewCommentRepository;
import com.pranicdoc.cph.review.repository.ReviewRevisionRepository;
import com.pranicdoc.cph.review.repository.ReviewSubmissionRepository;
import com.pranicdoc.cph.review.repository.ReviewerAssignmentRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A reviewer who only has access because someone is assigned to them (no CASE:REVIEW) loses a healer's
 * submissions as soon as that healer is unlinked or moved, although those submissions still carry their
 * id. A reviewer holding CASE:REVIEW keeps them. Runs the real inbox and comment services over a mutable
 * assignment table.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewerAccessAfterUnlinkTest {

    @Mock private ReviewSubmissionRepository submissions;
    @Mock private ReviewRevisionRepository revisions;
    @Mock private ReviewCommentRepository comments;
    @Mock private CaseRepository cases;
    @Mock private HealerRepository healers;
    @Mock private ReviewFileStore files;
    @Mock private SecurityContextUtil security;
    @Mock private ReviewEventPublisher events;
    @Mock private RbacAuthz rbacAuthz;
    @Mock private ReviewerAssignmentRepository assignments;
    @Mock private ReviewSubmissionService healerSide;

    private ReviewInboxService inbox;
    private ReviewCommentService commentService;

    private final UUID me = UUID.randomUUID();
    private final UUID healerA = UUID.randomUUID();
    private final UUID healerB = UUID.randomUUID();
    private final UUID otherReviewer = UUID.randomUUID();

    /** healer id -> reviewer id, the live assignment table. */
    private final Map<UUID, UUID> links = new HashMap<>();

    private ReviewSubmission pendingA;
    private ReviewSubmission approvedA;
    private ReviewSubmission pendingB;

    @BeforeEach
    void setUp() {
        inbox = new ReviewInboxService(submissions, revisions, cases, healers, files, security, events,
                rbacAuthz, assignments);
        commentService = new ReviewCommentService(comments, revisions, healerSide, inbox, healers, security, events);
        when(security.getCurrentHealerId()).thenReturn(me);

        links.put(healerA, me);
        links.put(healerB, me);
        when(assignments.findByReviewerId(me)).thenAnswer(i -> links.entrySet().stream()
                .filter(e -> e.getValue().equals(me))
                .map(e -> ReviewerAssignment.builder().healerId(e.getKey()).reviewerId(e.getValue()).build())
                .toList());
        when(assignments.existsByHealerIdAndReviewerId(any(), any()))
                .thenAnswer(i -> i.getArgument(1).equals(links.get(i.<UUID>getArgument(0))));

        pendingA = submission(healerA, ReviewStatus.PENDING_REVIEW);
        approvedA = submission(healerA, ReviewStatus.APPROVED);
        pendingB = submission(healerB, ReviewStatus.PENDING_REVIEW);
        when(submissions.findByReviewerIdAndStage(me, ReviewStage.REVIEWER))
                .thenReturn(List.of(pendingA, approvedA, pendingB));
        when(submissions.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(files.presignedUrl(any())).thenReturn("https://r2/x");
        when(comments.save(any(ReviewComment.class))).thenAnswer(i -> i.getArgument(0));
    }

    private ReviewSubmission submission(UUID healer, ReviewStatus status) {
        ReviewSubmission s = ReviewSubmission.builder().caseId(UUID.randomUUID()).healerId(healer)
                .reviewerId(me).stage(ReviewStage.REVIEWER).status(status)
                .submittedVia(SubmittedVia.MANUAL).build();
        s.setId(UUID.randomUUID());
        ReviewRevision revision = ReviewRevision.builder().submissionId(s.getId()).revisionNo(1)
                .fileUrl("review/r1.pdf").submittedVia(SubmittedVia.MANUAL).submittedAt(Instant.now()).build();
        revision.setId(UUID.randomUUID());
        when(submissions.findById(s.getId())).thenReturn(Optional.of(s));
        when(revisions.findBySubmissionIdAndRevisionNo(s.getId(), 1)).thenReturn(Optional.of(revision));
        when(revisions.findTopBySubmissionIdOrderByRevisionNoDesc(s.getId())).thenReturn(Optional.of(revision));
        when(revisions.findBySubmissionIdOrderByRevisionNoAsc(s.getId())).thenReturn(List.of(revision));
        return s;
    }

    private ReviewComment threadOn(ReviewSubmission s) {
        ReviewComment c = ReviewComment.builder().submissionId(s.getId()).revisionId(UUID.randomUUID())
                .authorId(me).authorSide(AuthorSide.REVIEWER).body("x").build();
        c.setId(UUID.randomUUID());
        when(comments.findById(c.getId())).thenReturn(Optional.of(c));
        return c;
    }

    private List<UUID> inboxIds() {
        return inbox.inbox(null).stream().map(InboxItemResponse::submissionId).toList();
    }

    private List<ThrowingCallable> everyRowAction(ReviewSubmission s) {
        ReviewComment thread = threadOn(s);
        return List.of(
                () -> inbox.getItem(s.getId()),
                () -> inbox.getFileUrl(s.getId(), 1),
                () -> inbox.approve(s.getId(), 1),
                () -> inbox.requestChanges(s.getId(), 1),
                () -> commentService.list(s.getId(), AuthorSide.REVIEWER),
                () -> commentService.add(s.getId(), new CommentRequest(1, null, null, null, null, "hi"),
                        AuthorSide.REVIEWER),
                () -> commentService.resolve(thread.getId()));
    }

    private void assertEveryRowActionDenied(ReviewSubmission s) {
        everyRowAction(s).forEach(action -> assertThatThrownBy(action)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Submission is not assigned to you"));
    }

    @Test
    void withoutPermission_unlinkedHealerDisappearsFromEveryRoute() {
        links.remove(healerA);

        assertThat(inboxIds()).containsExactly(pendingB.getId());
        assertEveryRowActionDenied(pendingA);
        assertEveryRowActionDenied(approvedA);

        assertThat(pendingA.getStatus()).isEqualTo(ReviewStatus.PENDING_REVIEW);
        assertThat(pendingA.getReviewerId()).as("the stored reviewer is left alone").isEqualTo(me);
        verify(comments, never()).save(any());
        verify(submissions, never()).saveAndFlush(any());
        verify(files, never()).presignedUrl(any());
        verifyNoInteractions(events);
    }

    @Test
    void withoutPermission_theStillLinkedHealerKeepsWorking() {
        links.remove(healerA);

        everyRowAction(pendingB).stream().limit(3).forEach(action ->
                org.assertj.core.api.Assertions.assertThatCode(action).doesNotThrowAnyException());
        assertThat(pendingB.getStatus()).isEqualTo(ReviewStatus.APPROVED);
        org.assertj.core.api.Assertions.assertThatCode(() -> commentService.add(pendingB.getId(),
                new CommentRequest(1, null, null, null, null, "hi"), AuthorSide.REVIEWER)).doesNotThrowAnyException();
    }

    @Test
    void withoutPermission_afterReassignmentTheOldReviewerCannotReadTheApprovedSubmission() {
        links.put(healerA, otherReviewer);

        assertThat(inboxIds()).containsExactly(pendingB.getId());
        assertThatThrownBy(() -> inbox.getItem(approvedA.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> inbox.getFileUrl(approvedA.getId(), 1)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> commentService.add(approvedA.getId(),
                new CommentRequest(1, null, null, null, null, "hi"), AuthorSide.REVIEWER))
                .isInstanceOf(AccessDeniedException.class);
        verify(files, never()).presignedUrl(any());
        verify(comments, never()).save(any());
    }

    @Test
    void withoutPermission_theInboxLoadsAssignmentsOnceNotPerRow() {
        assertThat(inboxIds()).containsExactlyInAnyOrder(pendingA.getId(), approvedA.getId(), pendingB.getId());

        verify(assignments, times(1)).findByReviewerId(me);
        verify(assignments, never()).existsByHealerIdAndReviewerId(any(), any());
    }

    @Test
    void withoutPermission_aCurrentLinkDoesNotOpenASubmissionStoredForSomeoneElse() {
        pendingB.setReviewerId(otherReviewer);

        assertThatThrownBy(() -> inbox.getItem(pendingB.getId())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void withPermission_behaviourIsUnchangedAfterUnlinkAndReassignment() {
        when(rbacAuthz.hasPermission("CASE", "REVIEW")).thenReturn(true);
        links.remove(healerA);

        assertThat(inboxIds()).containsExactlyInAnyOrder(pendingA.getId(), approvedA.getId(), pendingB.getId());

        links.put(healerA, otherReviewer);

        org.assertj.core.api.Assertions.assertThatCode(() -> {
            inbox.getItem(approvedA.getId());
            inbox.getFileUrl(approvedA.getId(), 1);
            commentService.add(approvedA.getId(), new CommentRequest(1, null, null, null, null, "hi"),
                    AuthorSide.REVIEWER);
            inbox.approve(pendingA.getId(), 1);
        }).doesNotThrowAnyException();
        verifyNoInteractions(assignments);
    }
}
