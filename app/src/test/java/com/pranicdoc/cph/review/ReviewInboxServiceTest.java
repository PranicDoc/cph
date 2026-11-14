package com.pranicdoc.cph.review;

import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.healing.dto.PreSignedUrlResponse;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.review.dto.InboxItemResponse;
import com.pranicdoc.cph.review.model.ReviewRevision;
import com.pranicdoc.cph.review.model.ReviewStage;
import com.pranicdoc.cph.review.model.ReviewStatus;
import com.pranicdoc.cph.review.model.ReviewSubmission;
import com.pranicdoc.cph.review.model.SubmittedVia;
import com.pranicdoc.cph.review.repository.ReviewRevisionRepository;
import com.pranicdoc.cph.review.repository.ReviewSubmissionRepository;
import com.pranicdoc.cph.review.service.ReviewFileStore;
import com.pranicdoc.cph.review.service.ReviewInboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class ReviewInboxServiceTest {

    @Mock private ReviewSubmissionRepository submissions;
    @Mock private ReviewRevisionRepository revisions;
    @Mock private CaseRepository cases;
    @Mock private HealerRepository healers;
    @Mock private ReviewFileStore files;
    @Mock private SecurityContextUtil security;

    private ReviewInboxService service;

    private final UUID me = UUID.randomUUID();
    private final UUID healerA = UUID.randomUUID();
    private final UUID healerB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ReviewInboxService(submissions, revisions, cases, healers, files, security);
        when(security.getCurrentHealerId()).thenReturn(me);
    }

    private ReviewSubmission submission(UUID healer, UUID reviewer, ReviewStatus status) {
        ReviewSubmission s = ReviewSubmission.builder().caseId(UUID.randomUUID()).healerId(healer)
                .reviewerId(reviewer).stage(ReviewStage.REVIEWER).status(status)
                .submittedVia(SubmittedVia.MANUAL).build();
        s.setId(UUID.randomUUID());
        return s;
    }

    private ReviewRevision revision(ReviewSubmission s, int no, Instant at, String reviewFile) {
        return ReviewRevision.builder().submissionId(s.getId()).revisionNo(no).fileUrl("review/x/r" + no + ".pdf")
                .reviewFileUrl(reviewFile).submittedVia(SubmittedVia.MANUAL).submittedAt(at).build();
    }

    private Healer healer(UUID id, String name) {
        Healer h = new Healer();
        h.setId(id);
        h.setFullName(name);
        return h;
    }

    private HealingCase healingCase(UUID id, String condition) {
        HealingCase c = HealingCase.builder().medicalCondition(condition).build();
        c.setId(id);
        return c;
    }

    @Test
    void inbox_onlyOwnAssignments_oldestFirst() {
        ReviewSubmission newer = submission(healerA, me, ReviewStatus.PENDING_REVIEW);
        ReviewSubmission older = submission(healerB, me, ReviewStatus.PENDING_REVIEW);
        ReviewSubmission ownCase = submission(me, me, ReviewStatus.PENDING_REVIEW);
        when(submissions.findByReviewerIdAndStage(me, ReviewStage.REVIEWER))
                .thenReturn(List.of(newer, ownCase, older));
        Instant t0 = Instant.parse("2026-11-01T10:00:00Z");
        when(revisions.findBySubmissionIdIn(any())).thenReturn(List.of(
                revision(newer, 1, t0.plusSeconds(500), null),
                revision(newer, 2, t0.plusSeconds(900), null),
                revision(older, 1, t0, null)));
        when(healers.findAllById(any())).thenReturn(List.of(healer(healerA, "Asha"), healer(healerB, "Bala")));
        when(cases.findAllById(any())).thenReturn(List.of(
                healingCase(newer.getCaseId(), "Migraine"), healingCase(older.getCaseId(), "Asthma")));

        List<InboxItemResponse> inbox = service.inbox(null);

        assertThat(inbox).extracting(InboxItemResponse::submissionId)
                .containsExactly(older.getId(), newer.getId());
        assertThat(inbox.get(0).healerName()).isEqualTo("Bala");
        assertThat(inbox.get(0).caseTitle()).isEqualTo("Asthma");
        assertThat(inbox.get(1).latestRevisionNo()).isEqualTo(2);
        assertThat(inbox.get(1).submittedAt()).isEqualTo(t0.plusSeconds(900));
    }

    @Test
    void inbox_statusFilterApplies() {
        ReviewSubmission pending = submission(healerA, me, ReviewStatus.PENDING_REVIEW);
        ReviewSubmission approved = submission(healerA, me, ReviewStatus.APPROVED);
        when(submissions.findByReviewerIdAndStage(me, ReviewStage.REVIEWER)).thenReturn(List.of(pending, approved));
        when(revisions.findBySubmissionIdIn(any())).thenReturn(List.of(revision(pending, 1, Instant.now(), null)));
        when(healers.findAllById(any())).thenReturn(List.of(healer(healerA, "Asha")));
        when(cases.findAllById(any())).thenReturn(List.of());

        List<InboxItemResponse> inbox = service.inbox(ReviewStatus.PENDING_REVIEW);

        assertThat(inbox).extracting(InboxItemResponse::submissionId).containsExactly(pending.getId());
        assertThat(inbox.get(0).caseTitle()).startsWith("Case");
    }

    @Test
    void reassignedReviewer_gets403() {
        ReviewSubmission moved = submission(healerA, UUID.randomUUID(), ReviewStatus.PENDING_REVIEW);
        when(submissions.findById(moved.getId())).thenReturn(Optional.of(moved));

        assertThatThrownBy(() -> service.getItem(moved.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getFileUrl(moved.getId(), 1)).isInstanceOf(AccessDeniedException.class);
        verify(files, never()).presignedUrl(any());
    }

    @Test
    void reviewerCannotOpenOwnCase() {
        ReviewSubmission own = submission(me, me, ReviewStatus.PENDING_REVIEW);
        when(submissions.findById(own.getId())).thenReturn(Optional.of(own));

        assertThatThrownBy(() -> service.getItem(own.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getFileUrl(own.getId(), 1)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void otherStageSubmissionAssignedToMe_gets403() {
        ReviewSubmission s = ReviewSubmission.builder().caseId(UUID.randomUUID()).healerId(healerA).reviewerId(me)
                .stage(ReviewStage.FEDERATION).status(ReviewStatus.PENDING_REVIEW)
                .submittedVia(SubmittedVia.MANUAL).build();
        s.setId(UUID.randomUUID());
        when(submissions.findById(s.getId())).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.getItem(s.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getFileUrl(s.getId(), 1)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void fileUrl_prefersReviewFileOverPinnedFile() {
        ReviewSubmission s = submission(healerA, me, ReviewStatus.PENDING_REVIEW);
        when(submissions.findById(s.getId())).thenReturn(Optional.of(s));
        when(revisions.findBySubmissionIdAndRevisionNo(s.getId(), 1))
                .thenReturn(Optional.of(revision(s, 1, Instant.now(), "review/x/masked.pdf")));
        when(files.presignedUrl("review/x/masked.pdf")).thenReturn("https://r2/masked");

        PreSignedUrlResponse url = service.getFileUrl(s.getId(), 1);

        assertThat(url.url()).isEqualTo("https://r2/masked");
    }
}
