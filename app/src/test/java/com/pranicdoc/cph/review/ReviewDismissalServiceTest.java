package com.pranicdoc.cph.review;

import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.model.PdfJobEntity;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.healing.repository.PdfJobRepository;
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
import com.pranicdoc.cph.review.service.ReviewAutoSubmitService;
import com.pranicdoc.cph.review.service.ReviewDismissalService;
import com.pranicdoc.cph.review.service.ReviewSubmissionService;
import com.pranicdoc.cph.review.service.ReviewerAssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Editing a case whose latest submission is APPROVED dismisses that approval (with the reason and
 * {@code dismissedFromApproved}), and auto-submit then leaves the case alone. Any other state is
 * left as it is.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewDismissalServiceTest {

    @Mock private ReviewSubmissionRepository submissions;
    @Mock private ReviewRevisionRepository revisions;
    @Mock private ReviewEventPublisher events;

    private ReviewDismissalService service;

    private final UUID caseId = UUID.randomUUID();
    private final UUID healer = UUID.randomUUID();
    private final UUID reviewer = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ReviewDismissalService(submissions, revisions, events);
    }

    private ReviewSubmission latest(UUID forCase, ReviewStatus status) {
        ReviewSubmission s = ReviewSubmission.builder().caseId(forCase).healerId(healer).reviewerId(reviewer)
                .stage(ReviewStage.REVIEWER).status(status).submittedVia(SubmittedVia.MANUAL).build();
        s.setId(UUID.randomUUID());
        s.setCreatedAt(Instant.now());
        when(submissions.findTopByCaseIdAndStageOrderByCreatedAtDesc(forCase, ReviewStage.REVIEWER))
                .thenReturn(Optional.of(s));
        when(revisions.findTopBySubmissionIdOrderByRevisionNoDesc(s.getId())).thenReturn(Optional.of(
                ReviewRevision.builder().submissionId(s.getId()).revisionNo(3).fileUrl("k")
                        .submittedVia(SubmittedVia.MANUAL).submittedAt(Instant.now()).build()));
        return s;
    }

    @Test
    void approved_dismissedWithReason() {
        ReviewSubmission s = latest(caseId, ReviewStatus.APPROVED);

        service.onCaseModified(caseId, "Session edited");

        assertThat(s.getStatus()).isEqualTo(ReviewStatus.DISMISSED);
        assertThat(s.isDismissedFromApproved()).isTrue();
        assertThat(s.getDismissReason()).isEqualTo("Session edited");
        verify(submissions).save(s);
        verify(events).publish(new ReviewEvent(s.getId(), healer, reviewer, ReviewEventType.STATUS_CHANGED, 3));
    }

    @Test
    void pending_untouched() {
        for (ReviewStatus status : List.of(ReviewStatus.PENDING_REVIEW, ReviewStatus.CHANGES_REQUESTED,
                ReviewStatus.DISMISSED)) {
            UUID otherCase = UUID.randomUUID();
            ReviewSubmission s = latest(otherCase, status);

            service.onCaseModified(otherCase, "Case details edited");

            assertThat(s.getStatus()).isEqualTo(status);
            assertThat(s.isDismissedFromApproved()).isFalse();
            assertThat(s.getDismissReason()).isNull();
        }
        verify(submissions, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void noSubmission_doesNothing() {
        when(submissions.findTopByCaseIdAndStageOrderByCreatedAtDesc(caseId, ReviewStage.REVIEWER))
                .thenReturn(Optional.empty());

        service.onCaseModified(caseId, "Case details edited");

        verify(submissions, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void manyCases_onlyEachCasesLatestApprovedIsDismissed() {
        UUID caseA = UUID.randomUUID();
        UUID caseB = UUID.randomUUID();
        ReviewSubmission oldApprovedA = submissionOf(caseA, ReviewStatus.DISMISSED, Instant.parse("2026-11-01T00:00:00Z"));
        ReviewSubmission latestApprovedA = submissionOf(caseA, ReviewStatus.APPROVED, Instant.parse("2026-11-05T00:00:00Z"));
        ReviewSubmission pendingB = submissionOf(caseB, ReviewStatus.PENDING_REVIEW, Instant.parse("2026-11-05T00:00:00Z"));
        when(submissions.findByCaseIdInAndStage(anyCollection(), eq(ReviewStage.REVIEWER)))
                .thenReturn(List.of(latestApprovedA, oldApprovedA, pendingB));

        service.onCasesModified(List.of(caseA, caseB), "Medical history edited");

        assertThat(latestApprovedA.getStatus()).isEqualTo(ReviewStatus.DISMISSED);
        assertThat(latestApprovedA.getDismissReason()).isEqualTo("Medical history edited");
        assertThat(oldApprovedA.getDismissReason()).isNull();
        assertThat(pendingB.getStatus()).isEqualTo(ReviewStatus.PENDING_REVIEW);
        verify(submissions).save(latestApprovedA);
        verify(submissions, never()).save(pendingB);
    }

    private ReviewSubmission submissionOf(UUID forCase, ReviewStatus status, Instant createdAt) {
        ReviewSubmission s = ReviewSubmission.builder().caseId(forCase).healerId(healer).reviewerId(reviewer)
                .stage(ReviewStage.REVIEWER).status(status).submittedVia(SubmittedVia.MANUAL).build();
        s.setId(UUID.randomUUID());
        s.setCreatedAt(createdAt);
        return s;
    }

    @Mock private PdfJobRepository pdfJobs;
    @Mock private CaseRepository cases;
    @Mock private HealerRepository healers;
    @Mock private ReviewerAssignmentService assignments;
    @Mock private ReviewSubmissionService submissionService;

    /** After a dismissal from approval, a newly generated PDF is not sent automatically. */
    @Test
    void dismissedApproval_autoSubmitThenSkips() {
        ReviewSubmission s = latest(caseId, ReviewStatus.APPROVED);
        service.onCaseModified(caseId, "Session edited");

        PdfJobEntity job = new PdfJobEntity();
        job.setJobId("job-1");
        job.setHealingCaseId(caseId);
        when(pdfJobs.findFirstByJobIdOrderByCreatedAtDesc("job-1")).thenReturn(Optional.of(job));
        HealingCase healingCase = HealingCase.builder().build();
        healingCase.setId(caseId);
        healingCase.setHealerId(healer);
        when(cases.findById(caseId)).thenReturn(Optional.of(healingCase));
        Healer h = new Healer();
        h.setId(healer);
        h.setAutoSubmitForReview(true);
        when(healers.findById(healer)).thenReturn(Optional.of(h));
        when(assignments.reviewerFor(healer)).thenReturn(Optional.of(reviewer));
        when(submissionService.latestFor(caseId)).thenReturn(Optional.of(s));

        new ReviewAutoSubmitService(new com.pranicdoc.cph.review.service.PdfJobCaseResolver(pdfJobs, cases), healers, assignments, submissionService).onPdfCompleted("job-1");

        verify(submissionService, never()).submitAs(any(), any(), any());
    }
}
