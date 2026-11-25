package com.pranicdoc.cph.review.service;

import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.exception.ReviewRefusedException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.rbac.RbacAuthz;
import com.pranicdoc.cph.review.dto.AdminReviewItemResponse;
import com.pranicdoc.cph.review.dto.CommentRequest;
import com.pranicdoc.cph.review.dto.CommentResponse;
import com.pranicdoc.cph.review.dto.InboxItemResponse;
import com.pranicdoc.cph.review.event.ReviewEvent;
import com.pranicdoc.cph.review.event.ReviewEventPublisher;
import com.pranicdoc.cph.review.event.ReviewEventType;
import com.pranicdoc.cph.review.model.AuthorSide;
import com.pranicdoc.cph.review.model.ReviewComment;
import com.pranicdoc.cph.review.model.ReviewRevision;
import com.pranicdoc.cph.review.model.ReviewStage;
import com.pranicdoc.cph.review.model.ReviewStatus;
import com.pranicdoc.cph.review.model.ReviewSubmission;
import com.pranicdoc.cph.review.model.SubmittedVia;
import com.pranicdoc.cph.review.repository.ReviewCommentRepository;
import com.pranicdoc.cph.review.repository.ReviewRevisionRepository;
import com.pranicdoc.cph.review.repository.ReviewSubmissionRepository;
import com.pranicdoc.cph.review.repository.ReviewerAssignmentRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A super admin may open and decide any reviewer-stage submission: someone else's, one with no reviewer,
 * and their own. Nobody else gains anything, including an account that was its own reviewer while it was a
 * super admin. Also covers the super admin's "All reviews" list. Runs the real inbox and comment services.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SuperAdminReviewTest {

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
    private final UUID healer = UUID.randomUUID();
    private final UUID otherReviewer = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        inbox = new ReviewInboxService(submissions, revisions, cases, healers, files, security, events,
                rbacAuthz, assignments);
        commentService = new ReviewCommentService(comments, revisions, healerSide, inbox, healers, security, events);
        when(security.getCurrentHealerId()).thenReturn(me);
        when(submissions.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(files.presignedUrl(any())).thenReturn("https://r2/x");
        when(comments.save(any(ReviewComment.class))).thenAnswer(i -> i.getArgument(0));
    }

    /** What RbacAuthz answers for a SUPADM: every permission, and super admin. */
    private void asSuperAdmin() {
        when(rbacAuthz.isSuperAdmin()).thenReturn(true);
        when(rbacAuthz.hasPermission("CASE", "REVIEW")).thenReturn(true);
    }

    private ReviewSubmission submission(UUID healerId, UUID reviewerId, ReviewStatus status) {
        return submission(healerId, reviewerId, status, ReviewStage.REVIEWER);
    }

    private ReviewSubmission submission(UUID healerId, UUID reviewerId, ReviewStatus status, ReviewStage stage) {
        ReviewSubmission s = ReviewSubmission.builder().caseId(UUID.randomUUID()).healerId(healerId)
                .reviewerId(reviewerId).stage(stage).status(status).submittedVia(SubmittedVia.MANUAL).build();
        s.setId(UUID.randomUUID());
        ReviewRevision revision = revision(s, 1, Instant.parse("2026-11-01T10:00:00Z"));
        when(submissions.findById(s.getId())).thenReturn(Optional.of(s));
        when(revisions.findBySubmissionIdAndRevisionNo(s.getId(), 1)).thenReturn(Optional.of(revision));
        when(revisions.findTopBySubmissionIdOrderByRevisionNoDesc(s.getId())).thenReturn(Optional.of(revision));
        when(revisions.findBySubmissionIdOrderByRevisionNoAsc(s.getId())).thenReturn(List.of(revision));
        return s;
    }

    private static ReviewRevision revision(ReviewSubmission s, int no, Instant at) {
        ReviewRevision r = ReviewRevision.builder().submissionId(s.getId()).revisionNo(no)
                .fileUrl("review/r" + no + ".pdf").submittedVia(SubmittedVia.MANUAL).submittedAt(at).build();
        r.setId(UUID.randomUUID());
        return r;
    }

    private ReviewComment threadOn(ReviewSubmission s) {
        ReviewComment c = ReviewComment.builder().submissionId(s.getId()).revisionId(UUID.randomUUID())
                .authorId(me).authorSide(AuthorSide.REVIEWER).body("x").build();
        c.setId(UUID.randomUUID());
        when(comments.findById(c.getId())).thenReturn(Optional.of(c));
        return c;
    }

    private static CommentRequest generalComment() {
        return new CommentRequest(1, null, null, null, null, "hi");
    }

    /** Reads first, then comments, then the two decisions. */
    private List<ThrowingCallable> everyRowAction(ReviewSubmission s) {
        ReviewComment thread = threadOn(s);
        return List.of(
                () -> inbox.getItem(s.getId()),
                () -> inbox.getFileUrl(s.getId(), 1),
                () -> commentService.list(s.getId(), AuthorSide.REVIEWER),
                () -> commentService.add(s.getId(), generalComment(), AuthorSide.REVIEWER),
                () -> commentService.resolve(thread.getId()),
                () -> inbox.requestChanges(s.getId(), 1),
                () -> inbox.approve(s.getId(), 1));
    }

    private ReviewEvent publishedEvent() {
        ArgumentCaptor<ReviewEvent> captor = ArgumentCaptor.forClass(ReviewEvent.class);
        verify(events).publish(captor.capture());
        return captor.getValue();
    }

    @Test
    void superAdminOpensAndDecidesAnotherReviewersSubmission() {
        asSuperAdmin();
        ReviewSubmission s = submission(healer, otherReviewer, ReviewStatus.PENDING_REVIEW);

        assertThat(inbox.getItem(s.getId()).reviewerId()).isEqualTo(otherReviewer);
        assertThat(inbox.getFileUrl(s.getId(), 1).url()).isEqualTo("https://r2/x");
        assertThat(inbox.requestChanges(s.getId(), 1).status()).isEqualTo(ReviewStatus.CHANGES_REQUESTED);

        assertThat(s.getReviewerId()).as("a decision does not take the submission over").isEqualTo(otherReviewer);
        assertThat(publishedEvent()).isEqualTo(new ReviewEvent(s.getId(), healer, otherReviewer,
                ReviewEventType.STATUS_CHANGED, 1, null, ReviewStatus.CHANGES_REQUESTED, me));
    }

    @Test
    void superAdminOpensAndDecidesASubmissionWithNoReviewer() {
        asSuperAdmin();
        ReviewSubmission s = submission(healer, null, ReviewStatus.PENDING_REVIEW);

        assertThat(inbox.getItem(s.getId()).reviewerId()).isNull();
        assertThat(inbox.getFileUrl(s.getId(), 1).url()).isEqualTo("https://r2/x");
        assertThat(inbox.approve(s.getId(), 1).status()).isEqualTo(ReviewStatus.APPROVED);

        assertThat(s.getReviewerId()).isNull();
        ReviewEvent event = publishedEvent();
        assertThat(event.reviewerId()).isNull();
        assertThat(event.standInId()).isEqualTo(me);
        assertThat(event.targetUserIds()).containsExactly(healer);
    }

    @Test
    void superAdminDecidesTheirOwnSubmission() {
        asSuperAdmin();
        ReviewSubmission selfAssigned = submission(me, me, ReviewStatus.PENDING_REVIEW);
        ReviewSubmission elsewhere = submission(me, otherReviewer, ReviewStatus.PENDING_REVIEW);

        assertThat(inbox.approve(selfAssigned.getId(), 1).status()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(inbox.approve(elsewhere.getId(), 1).status()).isEqualTo(ReviewStatus.APPROVED);

        ArgumentCaptor<ReviewEvent> captor = ArgumentCaptor.forClass(ReviewEvent.class);
        verify(events, times(2)).publish(captor.capture());
        assertThat(captor.getAllValues()).extracting(ReviewEvent::reviewerId, ReviewEvent::standInId)
                .containsExactly(tuple(me, null), tuple(otherReviewer, me));
    }

    @Test
    void superAdminHasEveryRowActionOnAnySubmissionWithoutReadingAssignments() {
        asSuperAdmin();
        for (UUID reviewer : Arrays.asList(otherReviewer, null)) {
            ReviewSubmission s = submission(healer, reviewer, ReviewStatus.PENDING_REVIEW);

            assertThat(everyRowAction(s)).allSatisfy(action -> assertThat(catchThrowable(action)).isNull());
        }
        verifyNoInteractions(assignments);
    }

    @Test
    void superAdminCommentIsOnTheReviewerSideAndNamesThemAsStandIn() {
        asSuperAdmin();
        ReviewSubmission s = submission(healer, otherReviewer, ReviewStatus.PENDING_REVIEW);

        CommentResponse comment = commentService.add(s.getId(), generalComment(), AuthorSide.REVIEWER);

        assertThat(comment.authorId()).isEqualTo(me);
        assertThat(comment.authorSide()).isEqualTo(AuthorSide.REVIEWER);
        assertThat(publishedEvent()).isEqualTo(new ReviewEvent(s.getId(), healer, otherReviewer,
                ReviewEventType.COMMENT_ADDED, 1, AuthorSide.REVIEWER, ReviewStatus.PENDING_REVIEW, me));
    }

    @Test
    void healerSideCommentHasNoStandIn() {
        ReviewSubmission s = submission(me, null, ReviewStatus.PENDING_REVIEW);
        when(healerSide.ownSubmission(s.getId())).thenReturn(s);

        commentService.add(s.getId(), generalComment(), AuthorSide.HEALER);

        assertThat(publishedEvent().standInId()).isNull();
    }

    @Test
    void superAdminKeepsTheStaleRevisionAndStatusRules() {
        asSuperAdmin();
        ReviewSubmission pending = submission(healer, null, ReviewStatus.PENDING_REVIEW);
        ReviewSubmission approved = submission(healer, otherReviewer, ReviewStatus.APPROVED);
        ReviewSubmission dismissed = submission(me, me, ReviewStatus.DISMISSED);

        assertThatThrownBy(() -> inbox.approve(pending.getId(), 7))
                .isInstanceOf(ReviewRefusedException.class)
                .hasMessage(ReviewInboxService.STALE_REVISION_MESSAGE);
        assertThatThrownBy(() -> inbox.requestChanges(approved.getId(), 1)).isInstanceOf(ReviewRefusedException.class);
        assertThatThrownBy(() -> inbox.approve(dismissed.getId(), 1)).isInstanceOf(ReviewRefusedException.class);
        verify(submissions, never()).saveAndFlush(any());
        verifyNoInteractions(events);
    }

    @Test
    void superAdminDoesNotGetOtherStages() {
        asSuperAdmin();
        ReviewSubmission federation =
                submission(healer, null, ReviewStatus.PENDING_REVIEW, ReviewStage.FEDERATION);

        everyRowAction(federation).forEach(action -> assertThatThrownBy(action)
                .isInstanceOf(AccessDeniedException.class));
    }

    @Test
    void permissionHolderWhoIsNotSuperAdminStaysLimitedToTheirOwnQueue() {
        when(rbacAuthz.hasPermission("CASE", "REVIEW")).thenReturn(true);

        assertDeniedOnUnassignedOthersAndOwn();
    }

    @Test
    void assignmentOnlyReviewerStaysLimitedEvenWithALeftoverSelfAssignment() {
        // The account was its own reviewer while it was a super admin; the link is still there.
        when(assignments.existsByHealerIdAndReviewerId(any(), any())).thenReturn(true);

        assertDeniedOnUnassignedOthersAndOwn();
    }

    private void assertDeniedOnUnassignedOthersAndOwn() {
        for (ReviewSubmission s : List.of(
                submission(healer, null, ReviewStatus.PENDING_REVIEW),
                submission(healer, otherReviewer, ReviewStatus.PENDING_REVIEW),
                submission(me, me, ReviewStatus.PENDING_REVIEW),
                submission(me, null, ReviewStatus.PENDING_REVIEW))) {
            everyRowAction(s).forEach(action -> assertThatThrownBy(action)
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Submission is not assigned to you"));
        }
        verify(comments, never()).save(any());
        verify(submissions, never()).saveAndFlush(any());
        verify(files, never()).presignedUrl(any());
        verifyNoInteractions(events);
    }

    @Test
    void inboxListsOwnSubmissionsOnlyForASuperAdmin() {
        ReviewSubmission own = submission(me, me, ReviewStatus.PENDING_REVIEW);
        ReviewSubmission theirs = submission(healer, me, ReviewStatus.PENDING_REVIEW);
        when(submissions.findByReviewerIdAndStage(me, ReviewStage.REVIEWER)).thenReturn(List.of(own, theirs));

        when(rbacAuthz.hasPermission("CASE", "REVIEW")).thenReturn(true);
        assertThat(inbox.inbox(null)).extracting(InboxItemResponse::submissionId).containsExactly(theirs.getId());

        asSuperAdmin();
        assertThat(inbox.inbox(null)).extracting(InboxItemResponse::submissionId)
                .containsExactlyInAnyOrder(own.getId(), theirs.getId());
        verify(submissions, never()).findByStage(any());
    }

    // --- All reviews ---

    private final UUID healerA = UUID.randomUUID();
    private final UUID healerB = UUID.randomUUID();
    private ReviewSubmission newestA;
    private ReviewSubmission oldestB;
    private ReviewSubmission unassignedA;

    private void threeReviewsOnFile() {
        Instant t0 = Instant.parse("2026-11-01T10:00:00Z");
        newestA = submission(healerA, otherReviewer, ReviewStatus.PENDING_REVIEW);
        oldestB = submission(healerB, me, ReviewStatus.APPROVED);
        unassignedA = submission(healerA, null, ReviewStatus.PENDING_REVIEW);
        when(submissions.findByStage(ReviewStage.REVIEWER)).thenReturn(List.of(newestA, oldestB, unassignedA));
        List<ReviewRevision> latest = List.of(
                revision(newestA, 1, t0.minusSeconds(900)),
                revision(newestA, 2, t0.plusSeconds(900)),
                revision(oldestB, 1, t0),
                revision(unassignedA, 1, t0.plusSeconds(100)));
        when(revisions.findBySubmissionIdIn(any())).thenReturn(latest);
        List<Healer> people = List.of(person(healerA, "Asha"), person(healerB, "Bala"),
                person(otherReviewer, "Ravi"), person(me, "Sana"));
        when(healers.findAllById(any())).thenReturn(people);
        HealingCase c = HealingCase.builder().medicalCondition("Asthma").build();
        c.setId(oldestB.getCaseId());
        when(cases.findAllById(any())).thenReturn(List.of(c));
    }

    private static Healer person(UUID id, String name) {
        Healer h = new Healer();
        h.setId(id);
        h.setFullName(name);
        return h;
    }

    @Test
    void allReviews_listsEveryReviewOldestLatestRevisionFirstWithBothNames() {
        threeReviewsOnFile();

        List<AdminReviewItemResponse> all = inbox.allReviews(null, null, null);

        assertThat(all).extracting(AdminReviewItemResponse::submissionId)
                .containsExactly(oldestB.getId(), unassignedA.getId(), newestA.getId());
        assertThat(all.get(0)).isEqualTo(new AdminReviewItemResponse(oldestB.getId(), healerB, "Bala", me, "Sana",
                "Asthma", 1, ReviewStatus.APPROVED, Instant.parse("2026-11-01T10:00:00Z")));
        assertThat(all.get(1).reviewerId()).isNull();
        assertThat(all.get(1).reviewerName()).isNull();
        assertThat(all.get(2).reviewerName()).isEqualTo("Ravi");
        assertThat(all.get(2).latestRevisionNo()).isEqualTo(2);
        verify(healers, times(1)).findAllById(any());
        verify(healers, never()).findById(any());
        verify(cases, times(1)).findAllById(any());
        verify(revisions, times(1)).findBySubmissionIdIn(any());
    }

    @Test
    void allReviews_filtersByStatusReviewerAndHealer() {
        threeReviewsOnFile();

        assertThat(inbox.allReviews(ReviewStatus.PENDING_REVIEW, null, null))
                .extracting(AdminReviewItemResponse::submissionId)
                .containsExactly(unassignedA.getId(), newestA.getId());
        assertThat(inbox.allReviews(null, otherReviewer, null))
                .extracting(AdminReviewItemResponse::submissionId).containsExactly(newestA.getId());
        assertThat(inbox.allReviews(null, null, healerB))
                .extracting(AdminReviewItemResponse::submissionId).containsExactly(oldestB.getId());
        assertThat(inbox.allReviews(ReviewStatus.APPROVED, null, healerA)).isEmpty();
    }

    @Test
    void allReviews_withNothingToListLoadsNoNames() {
        when(submissions.findByStage(ReviewStage.REVIEWER)).thenReturn(List.of());

        assertThat(inbox.allReviews(null, null, null)).isEmpty();
        verifyNoInteractions(healers, cases, revisions);
    }

    /** The audit row is written under the signed-in account, so a stand-in decision is attributed to them. */
    @Test
    void decisionsStayAudited() throws NoSuchMethodException {
        assertThat(ReviewInboxService.class.getMethod("approve", UUID.class, int.class)
                .getAnnotation(Auditable.class).action()).isEqualTo("APPROVE_REVIEW");
        assertThat(ReviewInboxService.class.getMethod("requestChanges", UUID.class, int.class)
                .getAnnotation(Auditable.class).action()).isEqualTo("REQUEST_CHANGES");
    }
}
