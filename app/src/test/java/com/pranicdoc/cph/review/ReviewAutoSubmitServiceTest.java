package com.pranicdoc.cph.review;

import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.model.PdfJobEntity;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.healing.repository.PdfJobRepository;
import com.pranicdoc.cph.review.model.ReviewStage;
import com.pranicdoc.cph.review.model.ReviewStatus;
import com.pranicdoc.cph.review.model.ReviewSubmission;
import com.pranicdoc.cph.review.model.SubmittedVia;
import com.pranicdoc.cph.review.service.ReviewAutoSubmitService;
import com.pranicdoc.cph.review.service.ReviewRefusedException;
import com.pranicdoc.cph.review.service.ReviewSubmissionService;
import com.pranicdoc.cph.review.service.ReviewerAssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewAutoSubmitServiceTest {

    @Mock private PdfJobRepository pdfJobs;
    @Mock private CaseRepository cases;
    @Mock private HealerRepository healers;
    @Mock private ReviewerAssignmentService assignments;
    @Mock private ReviewSubmissionService submissions;

    private ReviewAutoSubmitService service;

    private final UUID healerId = UUID.randomUUID();
    private final UUID caseId = UUID.randomUUID();
    private final UUID reviewerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ReviewAutoSubmitService(pdfJobs, cases, healers, assignments, submissions);
        PdfJobEntity job = PdfJobEntity.builder().jobId("job-1").healingCaseId(caseId).build();
        when(pdfJobs.findFirstByJobIdOrderByCreatedAtDesc("job-1")).thenReturn(Optional.of(job));
        HealingCase c = HealingCase.builder().build();
        c.setId(caseId);
        c.setHealerId(healerId);
        when(cases.findById(caseId)).thenReturn(Optional.of(c));
        when(healers.findById(healerId)).thenReturn(Optional.of(healer(true)));
        when(assignments.reviewerFor(healerId)).thenReturn(Optional.of(reviewerId));
        when(submissions.latestFor(caseId)).thenReturn(Optional.empty());
    }

    private Healer healer(boolean autoSubmit) {
        Healer h = Healer.builder().autoSubmitForReview(autoSubmit).build();
        h.setId(healerId);
        return h;
    }

    private ReviewSubmission latest(ReviewStatus status, boolean dismissedFromApproved) {
        ReviewSubmission s = ReviewSubmission.builder().caseId(caseId).healerId(healerId).reviewerId(reviewerId)
                .stage(ReviewStage.REVIEWER).status(status).dismissedFromApproved(dismissedFromApproved)
                .submittedVia(SubmittedVia.MANUAL).build();
        s.setId(UUID.randomUUID());
        return s;
    }

    private void verifyNotSubmitted() {
        verify(submissions, never()).submitAs(any(), any(), any());
    }

    @Test
    void prefOff_noSubmit() {
        when(healers.findById(healerId)).thenReturn(Optional.of(healer(false)));

        service.onPdfCompleted("job-1");

        verifyNotSubmitted();
    }

    @Test
    void noReviewer_noSubmit() {
        when(assignments.reviewerFor(healerId)).thenReturn(Optional.empty());

        service.onPdfCompleted("job-1");

        verifyNotSubmitted();
    }

    @Test
    void noSubmission_opensAuto() {
        service.onPdfCompleted("job-1");

        verify(submissions).submitAs(healerId, caseId, SubmittedVia.AUTO);
    }

    @Test
    void openSubmission_addsRevisionAuto() {
        when(submissions.latestFor(caseId)).thenReturn(Optional.of(latest(ReviewStatus.CHANGES_REQUESTED, false)));

        service.onPdfCompleted("job-1");

        verify(submissions).submitAs(healerId, caseId, SubmittedVia.AUTO);
    }

    @Test
    void approved_skipped() {
        when(submissions.latestFor(caseId)).thenReturn(Optional.of(latest(ReviewStatus.APPROVED, false)));

        service.onPdfCompleted("job-1");

        verifyNotSubmitted();
    }

    @Test
    void dismissedFromApproved_skipped() {
        when(submissions.latestFor(caseId)).thenReturn(Optional.of(latest(ReviewStatus.DISMISSED, true)));

        service.onPdfCompleted("job-1");

        verifyNotSubmitted();
    }

    @Test
    void dismissedNotFromApproved_opensNew() {
        when(submissions.latestFor(caseId)).thenReturn(Optional.of(latest(ReviewStatus.DISMISSED, false)));

        service.onPdfCompleted("job-1");

        verify(submissions).submitAs(healerId, caseId, SubmittedVia.AUTO);
    }

    @Test
    void unknownJob_noSubmit() {
        service.onPdfCompleted("nope");

        verifyNotSubmitted();
    }

    @Test
    void refusalFromSubmit_isSwallowed() {
        when(submissions.submitAs(healerId, caseId, SubmittedVia.AUTO))
                .thenThrow(new ReviewRefusedException("Generate the case PDF before submitting it for review."));

        service.onPdfCompleted("job-1");

        verify(submissions).submitAs(healerId, caseId, SubmittedVia.AUTO);
    }
}
