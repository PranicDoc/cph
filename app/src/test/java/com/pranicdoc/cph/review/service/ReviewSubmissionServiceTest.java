package com.pranicdoc.cph.review.service;

import com.pranicdoc.cph.common.enums.JobStatus;
import com.pranicdoc.cph.common.http.StorageObjectReader;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.model.PdfJobEntity;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.healing.repository.PdfJobRepository;
import com.pranicdoc.cph.review.dto.CaseReviewStatusResponse;
import com.pranicdoc.cph.review.dto.SubmissionOutcome;
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
import com.pranicdoc.cph.common.exception.ReviewRefusedException;
import com.pranicdoc.storage.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewSubmissionServiceTest {

    private static final String BLOCKED =
            "You don't have an instructor linked yet. Ask your administrator to link one before submitting.";

    @Mock private ReviewSubmissionRepository submissions;
    @Mock private ReviewRevisionRepository revisions;
    @Mock private CaseRepository cases;
    @Mock private PdfJobRepository pdfJobs;
    @Mock private ReviewerAssignmentService assignments;
    @Mock private StorageService storage;
    @Mock private StorageObjectReader reader;
    @Mock private SecurityContextUtil security;
    @Mock private PlatformTransactionManager txManager;
    @Mock private ReviewEventPublisher events;

    private ReviewFileStore files;
    private ReviewSubmissionService service;

    private final UUID healerId = UUID.randomUUID();
    private final UUID reviewerId = UUID.randomUUID();
    private final UUID caseId = UUID.randomUUID();
    private final byte[] pdfBytes = "%PDF-source".getBytes();

    @BeforeEach
    void setUp() {
        files = spy(new ReviewFileStore(storage, reader));
        doReturn(pdfBytes).when(files).fetch(anyString());
        service = new ReviewSubmissionService(submissions, revisions, cases, pdfJobs, assignments,
                files, security, new TransactionTemplate(txManager), events);

        when(security.getCurrentHealerId()).thenReturn(healerId);
        when(cases.findById(caseId)).thenReturn(Optional.of(ownedCase(caseId, healerId)));
        when(assignments.reviewerFor(healerId)).thenReturn(Optional.of(reviewerId));
        when(pdfJobs.findTopByHealingCaseIdOrderByCreatedAtDesc(caseId))
                .thenReturn(Optional.of(job(JobStatus.COMPLETED)));
        when(submissions.saveAndFlush(any(ReviewSubmission.class))).thenAnswer(i -> {
            ReviewSubmission s = i.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
            }
            return s;
        });
        when(revisions.saveAndFlush(any(ReviewRevision.class))).thenAnswer(i -> i.getArgument(0));
        when(submissions.findTopByCaseIdAndStageOrderByCreatedAtDesc(any(), any())).thenReturn(Optional.empty());
        when(revisions.findBySubmissionIdOrderByRevisionNoAsc(any())).thenReturn(List.of());
    }

    private static HealingCase ownedCase(UUID id, UUID owner) {
        HealingCase c = HealingCase.builder().build();
        c.setId(id);
        c.setHealerId(owner);
        return c;
    }

    private PdfJobEntity job(JobStatus status) {
        PdfJobEntity j = PdfJobEntity.builder().healingCaseId(caseId).status(status)
                .fileUrl("pdf/job-1.pdf").build();
        j.setId(UUID.randomUUID());
        return j;
    }

    private ReviewSubmission existing(ReviewStatus status) {
        ReviewSubmission s = ReviewSubmission.builder().caseId(caseId).healerId(healerId).reviewerId(reviewerId)
                .stage(ReviewStage.REVIEWER).status(status).submittedVia(SubmittedVia.MANUAL).build();
        s.setId(UUID.randomUUID());
        return s;
    }

    @Test
    void submit_blockedWithoutLinkedReviewer() {
        when(assignments.reviewerFor(healerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(caseId, SubmittedVia.MANUAL))
                .isInstanceOf(ReviewRefusedException.class)
                .hasMessage(BLOCKED);
        verify(submissions, never()).saveAndFlush(any());
    }

    @Test
    void submit_rejectsCaseWithoutCompletedPdf() {
        when(pdfJobs.findTopByHealingCaseIdOrderByCreatedAtDesc(caseId))
                .thenReturn(Optional.of(job(JobStatus.PROCESSING)));

        assertThatThrownBy(() -> service.submit(caseId, SubmittedVia.MANUAL))
                .isInstanceOf(ReviewRefusedException.class)
                .hasMessageContaining("PDF");
        verify(submissions, never()).saveAndFlush(any());
    }

    @Test
    void submit_rejectsOtherHealersCase() {
        when(cases.findById(caseId)).thenReturn(Optional.of(ownedCase(caseId, UUID.randomUUID())));

        assertThatThrownBy(() -> service.submit(caseId, SubmittedVia.MANUAL))
                .isInstanceOf(AccessDeniedException.class);
        verify(submissions, never()).saveAndFlush(any());
    }

    @Test
    void submit_createsRevision1Pinned() {
        SubmissionResponse response = service.submit(caseId, SubmittedVia.MANUAL);

        assertThat(response.status()).isEqualTo(ReviewStatus.PENDING_REVIEW);
        assertThat(response.latestRevisionNo()).isEqualTo(1);
        assertThat(response.reviewerId()).isEqualTo(reviewerId);
        ArgumentCaptor<ReviewRevision> rev = ArgumentCaptor.forClass(ReviewRevision.class);
        verify(revisions).saveAndFlush(rev.capture());
        UUID submissionId = response.id();
        assertThat(rev.getValue().getFileUrl()).isEqualTo("review/" + submissionId + "/r1.pdf");
        assertThat(rev.getValue().getRevisionNo()).isEqualTo(1);
        assertThat(rev.getValue().getSubmittedVia()).isEqualTo(SubmittedVia.MANUAL);
        verify(storage).upload("review/" + submissionId + "/r1.pdf", pdfBytes, "application/pdf");
    }

    @Test
    void resubmitWhileChangesRequested_addsRevision2AndResetsStatus() {
        ReviewSubmission open = existing(ReviewStatus.CHANGES_REQUESTED);
        when(submissions.findTopByCaseIdAndStageOrderByCreatedAtDesc(caseId, ReviewStage.REVIEWER))
                .thenReturn(Optional.of(open));
        ReviewRevision r1 = ReviewRevision.builder().submissionId(open.getId()).revisionNo(1).fileUrl("k1").build();
        when(revisions.findTopBySubmissionIdOrderByRevisionNoDesc(open.getId())).thenReturn(Optional.of(r1));

        SubmissionResponse response = service.submit(caseId, SubmittedVia.MANUAL);

        assertThat(response.id()).isEqualTo(open.getId());
        assertThat(open.getStatus()).isEqualTo(ReviewStatus.PENDING_REVIEW);
        assertThat(response.latestRevisionNo()).isEqualTo(2);
        verify(storage).upload(eq("review/" + open.getId() + "/r2.pdf"), eq(pdfBytes), eq("application/pdf"));
    }

    @Test
    void submitWhileApproved_throws() {
        when(submissions.findTopByCaseIdAndStageOrderByCreatedAtDesc(caseId, ReviewStage.REVIEWER))
                .thenReturn(Optional.of(existing(ReviewStatus.APPROVED)));

        assertThatThrownBy(() -> service.submit(caseId, SubmittedVia.MANUAL))
                .isInstanceOf(ReviewRefusedException.class)
                .hasMessage("Case is already approved");
        verify(storage, never()).upload(anyString(), any(), anyString());
    }

    @Test
    void submitAfterDismissed_opensNewSubmission() {
        ReviewSubmission dismissed = existing(ReviewStatus.DISMISSED);
        when(submissions.findTopByCaseIdAndStageOrderByCreatedAtDesc(caseId, ReviewStage.REVIEWER))
                .thenReturn(Optional.of(dismissed));

        SubmissionResponse response = service.submit(caseId, SubmittedVia.MANUAL);

        assertThat(response.id()).isNotEqualTo(dismissed.getId());
        assertThat(response.status()).isEqualTo(ReviewStatus.PENDING_REVIEW);
        assertThat(response.latestRevisionNo()).isEqualTo(1);
        assertThat(dismissed.getStatus()).isEqualTo(ReviewStatus.DISMISSED);
    }

    @Test
    void revisionFileSurvivesRegeneration() {
        service.submit(caseId, SubmittedVia.MANUAL);
        ArgumentCaptor<ReviewRevision> rev = ArgumentCaptor.forClass(ReviewRevision.class);
        verify(revisions).saveAndFlush(rev.capture());
        String pinned = rev.getValue().getFileUrl();

        // The pdf job row is reused and its source key overwritten: the revision must not follow it.
        assertThat(pinned).startsWith("review/").endsWith("/r1.pdf").doesNotContain("pdf/job-1.pdf");
        // The bytes were copied from the source at submit time, under the pinned key.
        verify(storage).upload(eq(pinned), eq(pdfBytes), eq("application/pdf"));
        verify(files).fetch("pdf/job-1.pdf");
    }

    @Test
    void submitTwiceConcurrently_yieldsOneOpenSubmission() {
        ReviewSubmission winner = existing(ReviewStatus.PENDING_REVIEW);
        when(submissions.saveAndFlush(any(ReviewSubmission.class)))
                .thenThrow(new DataIntegrityViolationException("uq_review_submission_open"));
        when(submissions.findTopByCaseIdAndStageOrderByCreatedAtDesc(caseId, ReviewStage.REVIEWER))
                .thenReturn(Optional.empty())          // first read inside the failed attempt
                .thenReturn(Optional.of(winner));      // re-read after the unique index rejected us
        ReviewRevision r1 = ReviewRevision.builder().submissionId(winner.getId()).revisionNo(1).fileUrl("k").build();
        when(revisions.findBySubmissionIdOrderByRevisionNoAsc(winner.getId())).thenReturn(List.of(r1));

        SubmissionResponse response = service.submit(caseId, SubmittedVia.MANUAL);

        assertThat(response.id()).isEqualTo(winner.getId());
        verify(storage, never()).upload(anyString(), any(), anyString());
    }

    @Test
    void submit_publishesRevisionAddedToBothParties() {
        SubmissionResponse response = service.submit(caseId, SubmittedVia.MANUAL);

        verify(events).publish(new ReviewEvent(response.id(), healerId, reviewerId,
                ReviewEventType.REVISION_ADDED, 1, null, ReviewStatus.PENDING_REVIEW));
    }

    @Test
    void resubmit_publishesRevisionAddedWithTheNewRevisionNumber() {
        ReviewSubmission open = existing(ReviewStatus.CHANGES_REQUESTED);
        when(submissions.findTopByCaseIdAndStageOrderByCreatedAtDesc(caseId, ReviewStage.REVIEWER))
                .thenReturn(Optional.of(open));
        ReviewRevision r1 = ReviewRevision.builder().submissionId(open.getId()).revisionNo(1).fileUrl("k1").build();
        when(revisions.findTopBySubmissionIdOrderByRevisionNoDesc(open.getId())).thenReturn(Optional.of(r1));

        service.submit(caseId, SubmittedVia.MANUAL);

        verify(events).publish(new ReviewEvent(open.getId(), healerId, reviewerId,
                ReviewEventType.REVISION_ADDED, 2, null, ReviewStatus.PENDING_REVIEW));
    }

    @Test
    void autoSubmit_publishesThroughTheSamePath() {
        SubmissionResponse response = service.submitAs(healerId, caseId, SubmittedVia.AUTO);

        verify(events).publish(new ReviewEvent(response.id(), healerId, reviewerId,
                ReviewEventType.REVISION_ADDED, 1, null, ReviewStatus.PENDING_REVIEW));
    }

    @Test
    void refusedSubmit_publishesNothing() {
        when(submissions.findTopByCaseIdAndStageOrderByCreatedAtDesc(caseId, ReviewStage.REVIEWER))
                .thenReturn(Optional.of(existing(ReviewStatus.APPROVED)));

        assertThatThrownBy(() -> service.submit(caseId, SubmittedVia.MANUAL))
                .isInstanceOf(ReviewRefusedException.class);
        verify(events, never()).publish(any());
    }

    // ---- identical snapshot (F1) ----

    private static final String CASE_JSON = "{\"title\":\"c\",\"sessions\":[1,2]}";
    private static final String CASE_JSON_REORDERED = "{ \"sessions\":[1,2], \"title\":\"c\" }";

    private void latestJobHolds(String caseData) {
        PdfJobEntity j = job(JobStatus.COMPLETED);
        j.setHealingCaseData(caseData);
        when(pdfJobs.findTopByHealingCaseIdOrderByCreatedAtDesc(caseId)).thenReturn(Optional.of(j));
    }

    private ReviewSubmission openWithRevision1Hashed(ReviewStatus status, String caseData) {
        ReviewSubmission open = existing(status);
        when(submissions.findTopByCaseIdAndStageOrderByCreatedAtDesc(caseId, ReviewStage.REVIEWER))
                .thenReturn(Optional.of(open));
        ReviewRevision r1 = ReviewRevision.builder().submissionId(open.getId()).revisionNo(1).fileUrl("k1")
                .snapshotHash(SnapshotHasher.hash(caseData)).build();
        when(revisions.findTopBySubmissionIdOrderByRevisionNoDesc(open.getId())).thenReturn(Optional.of(r1));
        when(revisions.findBySubmissionIdOrderByRevisionNoAsc(open.getId())).thenReturn(List.of(r1));
        return open;
    }

    @Test
    void identicalSnapshot_autoSubmit_addsNoRevision() {
        ReviewSubmission open = openWithRevision1Hashed(ReviewStatus.CHANGES_REQUESTED, CASE_JSON);
        latestJobHolds(CASE_JSON_REORDERED);

        SubmissionResponse response = service.submitAs(healerId, caseId, SubmittedVia.AUTO);

        assertThat(response.outcome()).isEqualTo(SubmissionOutcome.UNCHANGED);
        assertThat(response.latestRevisionNo()).isEqualTo(1);
        assertThat(open.getStatus()).isEqualTo(ReviewStatus.CHANGES_REQUESTED);
        verify(revisions, never()).saveAndFlush(any());
        verify(events, never()).publish(any());
    }

    @Test
    void identicalSnapshot_manualWhilePending_isNoOp() {
        ReviewSubmission open = openWithRevision1Hashed(ReviewStatus.PENDING_REVIEW, CASE_JSON);
        latestJobHolds(CASE_JSON);

        SubmissionResponse response = service.submit(caseId, SubmittedVia.MANUAL);

        assertThat(response.outcome()).isEqualTo(SubmissionOutcome.UNCHANGED);
        assertThat(response.latestRevisionNo()).isEqualTo(1);
        assertThat(open.getStatus()).isEqualTo(ReviewStatus.PENDING_REVIEW);
        verify(revisions, never()).saveAndFlush(any());
        verify(submissions, never()).saveAndFlush(any());
        verify(events, never()).publish(any());
    }

    @Test
    void identicalSnapshot_manualWhileChangesRequested_rerequestsWithoutNewRevision() {
        ReviewSubmission open = openWithRevision1Hashed(ReviewStatus.CHANGES_REQUESTED, CASE_JSON);
        latestJobHolds(CASE_JSON);

        SubmissionResponse response = service.submit(caseId, SubmittedVia.MANUAL);

        assertThat(response.outcome()).isEqualTo(SubmissionOutcome.REREQUESTED);
        assertThat(response.status()).isEqualTo(ReviewStatus.PENDING_REVIEW);
        assertThat(open.getStatus()).isEqualTo(ReviewStatus.PENDING_REVIEW);
        assertThat(response.latestRevisionNo()).isEqualTo(1);
        assertThat(response.revisions()).hasSize(1);
        verify(revisions, never()).saveAndFlush(any());
        verify(submissions).saveAndFlush(open);
        // One STATUS_CHANGED and no REVISION_ADDED: the notifier mails the instructor exactly once.
        verify(events).publish(new ReviewEvent(open.getId(), healerId, reviewerId,
                ReviewEventType.STATUS_CHANGED, 1, null, ReviewStatus.PENDING_REVIEW));
        verify(events, times(1)).publish(any());
    }

    @Test
    void changedSnapshot_addsRevision_asBefore() {
        ReviewSubmission open = openWithRevision1Hashed(ReviewStatus.CHANGES_REQUESTED, CASE_JSON);
        latestJobHolds("{\"title\":\"edited\"}");

        SubmissionResponse response = service.submit(caseId, SubmittedVia.MANUAL);

        assertThat(response.outcome()).isEqualTo(SubmissionOutcome.REVISION_ADDED);
        assertThat(response.latestRevisionNo()).isEqualTo(2);
        assertThat(open.getStatus()).isEqualTo(ReviewStatus.PENDING_REVIEW);
        ArgumentCaptor<ReviewRevision> rev = ArgumentCaptor.forClass(ReviewRevision.class);
        verify(revisions).saveAndFlush(rev.capture());
        assertThat(rev.getValue().getSnapshotHash()).isEqualTo(SnapshotHasher.hash("{\"title\":\"edited\"}"));
        verify(events).publish(new ReviewEvent(open.getId(), healerId, reviewerId,
                ReviewEventType.REVISION_ADDED, 2, null, ReviewStatus.PENDING_REVIEW));
    }

    @Test
    void revisionWithoutHash_neverMatches() {
        openWithRevision1Hashed(ReviewStatus.PENDING_REVIEW, null);
        latestJobHolds(CASE_JSON);

        assertThat(service.submit(caseId, SubmittedVia.MANUAL).outcome())
                .isEqualTo(SubmissionOutcome.REVISION_ADDED);
    }

    @Test
    void identicalSnapshot_afterDismissal_opensNewSubmission() {
        ReviewSubmission dismissed = existing(ReviewStatus.DISMISSED);
        when(submissions.findTopByCaseIdAndStageOrderByCreatedAtDesc(caseId, ReviewStage.REVIEWER))
                .thenReturn(Optional.of(dismissed));
        ReviewRevision old = ReviewRevision.builder().submissionId(dismissed.getId()).revisionNo(1).fileUrl("k1")
                .snapshotHash(SnapshotHasher.hash(CASE_JSON)).build();
        when(revisions.findTopBySubmissionIdOrderByRevisionNoDesc(dismissed.getId())).thenReturn(Optional.of(old));
        latestJobHolds(CASE_JSON);

        SubmissionResponse response = service.submit(caseId, SubmittedVia.MANUAL);

        assertThat(response.id()).isNotEqualTo(dismissed.getId());
        assertThat(response.outcome()).isEqualTo(SubmissionOutcome.CREATED);
        ArgumentCaptor<ReviewRevision> rev = ArgumentCaptor.forClass(ReviewRevision.class);
        verify(revisions).saveAndFlush(rev.capture());
        assertThat(rev.getValue().getSnapshotHash()).isEqualTo(SnapshotHasher.hash(CASE_JSON));
    }

    @Test
    void identicalSnapshot_doesNotCopyThePdf() {
        openWithRevision1Hashed(ReviewStatus.CHANGES_REQUESTED, CASE_JSON);
        latestJobHolds(CASE_JSON);

        service.submit(caseId, SubmittedVia.MANUAL);
        service.submitAs(healerId, caseId, SubmittedVia.AUTO);

        verify(files, never()).pin(any(), anyInt(), anyString());
        verify(storage, never()).upload(anyString(), any(), anyString());
    }

    @Test
    void statusesFor_reportsPerCaseStatusAndEligibility() {
        UUID other = UUID.randomUUID();
        UUID foreign = UUID.randomUUID();
        when(cases.findAllById(List.of(caseId, other, foreign))).thenReturn(List.of(
                ownedCase(caseId, healerId), ownedCase(other, healerId), ownedCase(foreign, UUID.randomUUID())));
        when(pdfJobs.findByHealingCaseIdIn(any()))
                .thenReturn(List.of(job(JobStatus.COMPLETED)));
        ReviewSubmission sub = existing(ReviewStatus.CHANGES_REQUESTED);
        when(submissions.findByCaseIdInAndStage(any(), eq(ReviewStage.REVIEWER))).thenReturn(List.of(sub));
        when(revisions.findBySubmissionIdIn(any())).thenReturn(List.of(
                ReviewRevision.builder().submissionId(sub.getId()).revisionNo(1).build(),
                ReviewRevision.builder().submissionId(sub.getId()).revisionNo(2).build()));

        Map<UUID, CaseReviewStatusResponse> result = service.statusesFor(List.of(caseId, other, foreign));

        assertThat(result).containsOnlyKeys(caseId, other);
        assertThat(result.get(caseId).status()).isEqualTo("CHANGES_REQUESTED");
        assertThat(result.get(caseId).eligible()).isTrue();
        assertThat(result.get(caseId).latestRevisionNo()).isEqualTo(2);
        assertThat(result.get(other).status()).isEqualTo("NOT_SUBMITTED");
        assertThat(result.get(other).eligible()).isFalse();
    }

    @Test
    void statusesFor_withoutReviewerReportsNoReviewer() {
        when(assignments.reviewerFor(healerId)).thenReturn(Optional.empty());
        when(cases.findAllById(List.of(caseId))).thenReturn(List.of(ownedCase(caseId, healerId)));
        when(pdfJobs.findByHealingCaseIdIn(any())).thenReturn(List.of());
        when(submissions.findByCaseIdInAndStage(any(), any())).thenReturn(List.of());

        assertThat(service.statusesFor(List.of(caseId)).get(caseId).status())
                .isEqualTo("NOT_SUBMITTED_NO_REVIEWER");
    }

    @Test
    void statusesFor_latestJobNotCompleted_isIneligibleAndAgreesWithIsEligible() {
        PdfJobEntity older = job(JobStatus.COMPLETED);
        older.setCreatedAt(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        PdfJobEntity latest = job(JobStatus.PROCESSING);
        latest.setCreatedAt(java.time.Instant.parse("2026-02-01T00:00:00Z"));
        when(cases.findAllById(List.of(caseId))).thenReturn(List.of(ownedCase(caseId, healerId)));
        when(pdfJobs.findByHealingCaseIdIn(any())).thenReturn(List.of(older, latest));
        when(pdfJobs.findTopByHealingCaseIdOrderByCreatedAtDesc(caseId)).thenReturn(Optional.of(latest));
        when(submissions.findByCaseIdInAndStage(any(), any())).thenReturn(List.of());

        assertThat(service.statusesFor(List.of(caseId)).get(caseId).eligible()).isFalse();
        assertThat(service.isEligible(caseId)).isFalse();
    }

    @Test
    void statusesFor_completedJobWithoutFile_isIneligible() {
        PdfJobEntity noFile = job(JobStatus.COMPLETED);
        noFile.setFileUrl(null);
        when(cases.findAllById(List.of(caseId))).thenReturn(List.of(ownedCase(caseId, healerId)));
        when(pdfJobs.findByHealingCaseIdIn(any())).thenReturn(List.of(noFile));
        when(submissions.findByCaseIdInAndStage(any(), any())).thenReturn(List.of());

        assertThat(service.statusesFor(List.of(caseId)).get(caseId).eligible()).isFalse();
    }

    @Test
    void reassignOpen_movesPendingAndChangesRequestedSubmissions() {
        UUID newReviewer = UUID.randomUUID();
        ReviewSubmission a = existing(ReviewStatus.PENDING_REVIEW);
        ReviewSubmission b = existing(ReviewStatus.CHANGES_REQUESTED);
        when(submissions.findByHealerIdAndStageAndStatusIn(eq(healerId), eq(ReviewStage.REVIEWER), any()))
                .thenReturn(List.of(a, b));

        service.reassignOpen(healerId, newReviewer);

        assertThat(a.getReviewerId()).isEqualTo(newReviewer);
        assertThat(b.getReviewerId()).isEqualTo(newReviewer);
        verify(submissions).saveAll(List.of(a, b));
    }

    @Test
    void approvedCount_countsApprovedReviewerSubmissions() {
        when(submissions.countByHealerIdAndStageAndStatus(healerId, ReviewStage.REVIEWER, ReviewStatus.APPROVED))
                .thenReturn(3L);
        assertThat(service.approvedCount(healerId)).isEqualTo(3L);
    }
}
