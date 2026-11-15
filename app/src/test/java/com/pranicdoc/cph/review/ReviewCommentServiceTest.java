package com.pranicdoc.cph.review;

import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.review.dto.CommentRequest;
import com.pranicdoc.cph.review.dto.CommentResponse;
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
import com.pranicdoc.cph.review.service.ReviewCommentService;
import com.pranicdoc.cph.review.service.ReviewInboxService;
import com.pranicdoc.cph.review.service.ReviewSubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ReviewCommentServiceTest {

    @Mock private ReviewCommentRepository comments;
    @Mock private ReviewRevisionRepository revisions;
    @Mock private ReviewSubmissionService healerSide;
    @Mock private ReviewInboxService reviewerSide;
    @Mock private HealerRepository healers;
    @Mock private SecurityContextUtil security;
    @Mock private ReviewEventPublisher events;

    private ReviewCommentService service;

    private final UUID me = UUID.randomUUID();
    private ReviewSubmission submission;
    private ReviewRevision rev1;
    private ReviewRevision rev2;

    @BeforeEach
    void setUp() {
        service = new ReviewCommentService(comments, revisions, healerSide, reviewerSide, healers, security, events);
        submission = ReviewSubmission.builder().caseId(UUID.randomUUID()).healerId(UUID.randomUUID())
                .reviewerId(me).stage(ReviewStage.REVIEWER).status(ReviewStatus.PENDING_REVIEW)
                .submittedVia(SubmittedVia.MANUAL).build();
        submission.setId(UUID.randomUUID());
        rev1 = revision(1);
        rev2 = revision(2);
    }

    private ReviewRevision revision(int no) {
        ReviewRevision r = ReviewRevision.builder().submissionId(submission.getId()).revisionNo(no)
                .fileUrl("review/x/r" + no + ".pdf").submittedVia(SubmittedVia.MANUAL)
                .submittedAt(Instant.now()).build();
        r.setId(UUID.randomUUID());
        return r;
    }

    /** Reviewer-side caller with two revisions on file. */
    private void asReviewerWithTwoRevisions() {
        when(reviewerSide.assignedSubmission(submission.getId())).thenReturn(submission);
        when(security.getCurrentHealerId()).thenReturn(me);
        when(revisions.findBySubmissionIdAndRevisionNo(submission.getId(), 1)).thenReturn(Optional.of(rev1));
        when(revisions.findBySubmissionIdAndRevisionNo(submission.getId(), 2)).thenReturn(Optional.of(rev2));
        when(revisions.findTopBySubmissionIdOrderByRevisionNoDesc(submission.getId())).thenReturn(Optional.of(rev2));
        when(comments.save(any(ReviewComment.class))).thenAnswer(inv -> {
            ReviewComment c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
    }

    private CommentRequest pin(int revisionNo, double x, double y, String body) {
        return new CommentRequest(revisionNo, null, 1, x, y, body);
    }

    @Test
    void pinOnOldRevision_rejected() {
        asReviewerWithTwoRevisions();

        assertThatThrownBy(() -> service.add(submission.getId(), pin(1, 0.5, 0.5, "Look here"), AuthorSide.REVIEWER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("latest version");
        verify(comments, never()).save(any());
    }

    @Test
    void replyOnOldRevision_allowed() {
        asReviewerWithTwoRevisions();
        ReviewComment parent = ReviewComment.builder().submissionId(submission.getId()).revisionId(rev1.getId())
                .authorId(me).authorSide(AuthorSide.REVIEWER).body("old").build();
        parent.setId(UUID.randomUUID());
        when(comments.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(revisions.findById(rev1.getId())).thenReturn(Optional.of(rev1));

        CommentResponse reply = service.add(submission.getId(),
                new CommentRequest(null, parent.getId(), null, null, null, "  thanks  "), AuthorSide.REVIEWER);

        assertThat(reply.revisionNo()).isEqualTo(1);
        assertThat(reply.parentId()).isEqualTo(parent.getId());
        assertThat(reply.body()).isEqualTo("thanks");
        assertThat(reply.page()).isNull();
    }

    @Test
    void partialPin_rejected() {
        asReviewerWithTwoRevisions();

        assertThatThrownBy(() -> service.add(submission.getId(),
                new CommentRequest(2, null, 1, 0.5, null, "x"), AuthorSide.REVIEWER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A pin needs page, x and y");
        verify(comments, never()).save(any());
    }

    @Test
    void healerCannotResolve() {
        ReviewComment c = ReviewComment.builder().submissionId(submission.getId()).revisionId(rev1.getId())
                .authorId(me).authorSide(AuthorSide.REVIEWER).body("x").build();
        c.setId(UUID.randomUUID());
        when(comments.findById(c.getId())).thenReturn(Optional.of(c));
        // A healer fails the reviewer-side row check (reviewerId != caller).
        when(reviewerSide.assignedSubmission(submission.getId()))
                .thenThrow(new AccessDeniedException("Submission is not assigned to you"));

        assertThatThrownBy(() -> service.resolve(c.getId())).isInstanceOf(AccessDeniedException.class);
        assertThat(c.getResolvedAt()).isNull();
        verify(comments, never()).save(any());
    }

    @Test
    void reviewerResolvesThread() {
        ReviewComment c = ReviewComment.builder().submissionId(submission.getId()).revisionId(rev1.getId())
                .authorId(me).authorSide(AuthorSide.REVIEWER).body("x").build();
        c.setId(UUID.randomUUID());
        when(comments.findById(c.getId())).thenReturn(Optional.of(c));
        when(reviewerSide.assignedSubmission(submission.getId())).thenReturn(submission);

        service.resolve(c.getId());

        assertThat(c.getResolvedAt()).isNotNull();
        verify(comments).save(c);
    }

    @Test
    void strangerCannotComment() {
        UUID id = UUID.randomUUID();
        when(healerSide.ownSubmission(id)).thenThrow(new AccessDeniedException("Submission does not belong to you"));
        when(reviewerSide.assignedSubmission(id)).thenThrow(new AccessDeniedException("not assigned"));

        assertThatThrownBy(() -> service.add(id, pin(1, 0.1, 0.1, "hi"), AuthorSide.HEALER))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.add(id, pin(1, 0.1, 0.1, "hi"), AuthorSide.REVIEWER))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.list(id, AuthorSide.HEALER)).isInstanceOf(AccessDeniedException.class);
        verify(comments, never()).save(any());
        verify(events, never()).publish(any());
    }

    @Test
    void nullAuthor_commentListMapsWithoutNpe() {
        when(reviewerSide.assignedSubmission(submission.getId())).thenReturn(submission);
        ReviewComment orphaned = ReviewComment.builder().submissionId(submission.getId()).revisionId(rev1.getId())
                .authorId(null).authorSide(AuthorSide.REVIEWER).body("Please redo page 2").build();
        orphaned.setId(UUID.randomUUID());
        UUID healerAuthor = UUID.randomUUID();
        ReviewComment reply = ReviewComment.builder().submissionId(submission.getId()).revisionId(rev1.getId())
                .parentId(orphaned.getId()).authorId(healerAuthor).authorSide(AuthorSide.HEALER).body("Done").build();
        reply.setId(UUID.randomUUID());
        when(comments.findBySubmissionIdOrderByCreatedAtAsc(submission.getId())).thenReturn(List.of(orphaned, reply));
        when(revisions.findBySubmissionIdOrderByRevisionNoAsc(submission.getId())).thenReturn(List.of(rev1, rev2));
        when(healers.findAllById(any())).thenAnswer(inv -> {
            Iterable<UUID> ids = inv.getArgument(0);
            ids.forEach(id -> assertThat(id).as("no null id is looked up").isNotNull());
            com.pranicdoc.cph.auth.model.Healer asha = com.pranicdoc.cph.auth.model.Healer.builder().fullName("Asha Rao").build();
            asha.setId(healerAuthor);
            return List.of(asha);
        });

        List<CommentResponse> list = service.list(submission.getId(), AuthorSide.REVIEWER);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).authorId()).isNull();
        assertThat(list.get(0).authorName()).isNull();
        assertThat(list.get(0).body()).isEqualTo("Please redo page 2");
        assertThat(list.get(1).authorId()).isEqualTo(healerAuthor);
    }

    @Test
    void publishesCommentAdded() {
        asReviewerWithTwoRevisions();

        CommentResponse saved = service.add(submission.getId(), pin(2, 0.25, 0.75, "Fix this"), AuthorSide.REVIEWER);

        ArgumentCaptor<ReviewEvent> event = ArgumentCaptor.forClass(ReviewEvent.class);
        verify(events).publish(event.capture());
        assertThat(event.getValue().type()).isEqualTo(ReviewEventType.COMMENT_ADDED);
        assertThat(event.getValue().submissionId()).isEqualTo(submission.getId());
        assertThat(event.getValue().healerId()).isEqualTo(submission.getHealerId());
        assertThat(event.getValue().reviewerId()).isEqualTo(me);
        assertThat(event.getValue().revisionNo()).isEqualTo(2);
        assertThat(saved.x()).isEqualTo(0.25);
        assertThat(saved.y()).isEqualTo(0.75);
        assertThat(saved.authorSide()).isEqualTo(AuthorSide.REVIEWER);
    }
}
