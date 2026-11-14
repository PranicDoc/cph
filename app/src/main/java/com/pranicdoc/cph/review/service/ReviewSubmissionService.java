package com.pranicdoc.cph.review.service;

import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.enums.JobStatus;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.healing.dto.PreSignedUrlResponse;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.model.PdfJobEntity;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.healing.repository.PdfJobRepository;
import com.pranicdoc.cph.review.dto.CaseReviewStatusResponse;
import com.pranicdoc.cph.review.dto.RevisionResponse;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Healer-side case review: submit a case PDF to the linked instructor, resubmit while the review is
 * open, and read back status. Each revision is copied to its own storage key so it stays pinned to
 * the exact file that was submitted even after the PDF is regenerated.
 */
@Service
public class ReviewSubmissionService {

    static final String NO_REVIEWER_MESSAGE =
            "You don't have an instructor linked yet. Ask your administrator to link one before submitting.";

    private static final Set<ReviewStatus> OPEN_FOR_MOVE =
            EnumSet.of(ReviewStatus.PENDING_REVIEW, ReviewStatus.CHANGES_REQUESTED);

    private final ReviewSubmissionRepository submissions;
    private final ReviewRevisionRepository revisions;
    private final CaseRepository cases;
    private final PdfJobRepository pdfJobs;
    private final ReviewerAssignmentService assignments;
    private final ReviewFileStore files;
    private final SecurityContextUtil security;
    private final TransactionTemplate tx;
    private final ReviewEventPublisher events;

    public ReviewSubmissionService(ReviewSubmissionRepository submissions, ReviewRevisionRepository revisions,
                                   CaseRepository cases, PdfJobRepository pdfJobs,
                                   ReviewerAssignmentService assignments, ReviewFileStore files,
                                   SecurityContextUtil security, PlatformTransactionManager txManager,
                                   ReviewEventPublisher events) {
        this.submissions = submissions;
        this.revisions = revisions;
        this.cases = cases;
        this.pdfJobs = pdfJobs;
        this.assignments = assignments;
        this.files = files;
        this.security = security;
        this.tx = new TransactionTemplate(txManager);
        this.events = events;
    }

    /**
     * Opens a new submission for the case, or adds a revision when one is open. Runs its writes in a
     * transaction of its own so a lost race on the unique index can be answered with the winner's
     * submission instead of a failed request. Both parties get a live REVISION_ADDED event once the
     * revision has committed; a lost race publishes nothing, since the winner already did.
     */
    @Auditable(action = "SUBMIT_FOR_REVIEW")
    public SubmissionResponse submit(UUID caseId, SubmittedVia via) {
        return doSubmit(security.getCurrentHealerId(), caseId, via);
    }

    /**
     * Same as {@link #submit} for a caller with no security context (the PDF-completed webhook):
     * the healer is named explicitly and the case must still belong to them.
     */
    @Auditable(action = "AUTO_SUBMIT_FOR_REVIEW")
    public SubmissionResponse submitAs(UUID healerId, UUID caseId, SubmittedVia via) {
        return doSubmit(healerId, caseId, via);
    }

    private SubmissionResponse doSubmit(UUID healerId, UUID caseId, SubmittedVia via) {
        HealingCase healingCase = cases.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));
        if (!healerId.equals(healingCase.getHealerId())) {
            throw new AccessDeniedException("Case does not belong to you");
        }
        UUID reviewerId = assignments.reviewerFor(healerId)
                .orElseThrow(() -> new ReviewRefusedException(NO_REVIEWER_MESSAGE));
        PdfJobEntity job = completedJob(caseId)
                .orElseThrow(() -> new ReviewRefusedException(
                        "Generate the case PDF before submitting it for review."));

        try {
            return tx.execute(status -> openOrAddRevision(caseId, healerId, reviewerId, job, via));
        } catch (DataIntegrityViolationException lostRace) {
            // The unique index (or the revision number) was taken by a concurrent submit.
            SubmissionResponse winner = tx.execute(status -> submissions
                    .findTopByCaseIdAndStageOrderByCreatedAtDesc(caseId, ReviewStage.REVIEWER)
                    .filter(s -> s.getStatus() != ReviewStatus.DISMISSED)
                    .map(s -> toResponse(s, revisions.findBySubmissionIdOrderByRevisionNoAsc(s.getId())))
                    .orElse(null));
            if (winner == null) {
                throw lostRace;
            }
            return winner;
        }
    }

    private SubmissionResponse openOrAddRevision(UUID caseId, UUID healerId, UUID reviewerId,
                                                 PdfJobEntity job, SubmittedVia via) {
        Optional<ReviewSubmission> latest =
                submissions.findTopByCaseIdAndStageOrderByCreatedAtDesc(caseId, ReviewStage.REVIEWER);
        if (latest.isPresent() && latest.get().getStatus() == ReviewStatus.APPROVED) {
            throw new ReviewRefusedException("Case is already approved");
        }
        if (latest.isPresent() && latest.get().getStatus() != ReviewStatus.DISMISSED) {
            return addRevision(latest.get(), reviewerId, job, via);
        }
        ReviewSubmission created = submissions.saveAndFlush(ReviewSubmission.builder()
                .caseId(caseId).healerId(healerId).reviewerId(reviewerId)
                .stage(ReviewStage.REVIEWER).status(ReviewStatus.PENDING_REVIEW)
                .submittedVia(via).build());
        ReviewRevision first = saveRevision(created.getId(), 1, job, via);
        publishRevisionAdded(created, first);
        return toResponse(created, List.of(first));
    }

    private SubmissionResponse addRevision(ReviewSubmission open, UUID reviewerId, PdfJobEntity job, SubmittedVia via) {
        int next = revisions.findTopBySubmissionIdOrderByRevisionNoDesc(open.getId())
                .map(r -> r.getRevisionNo() + 1).orElse(1);
        List<ReviewRevision> previous = revisions.findBySubmissionIdOrderByRevisionNoAsc(open.getId());
        ReviewRevision added = saveRevision(open.getId(), next, job, via);
        open.setStatus(ReviewStatus.PENDING_REVIEW);
        open.setReviewerId(reviewerId);
        submissions.saveAndFlush(open);
        publishRevisionAdded(open, added);
        List<ReviewRevision> all = new java.util.ArrayList<>(previous);
        all.add(added);
        return toResponse(open, all);
    }

    /** Registered inside the submit transaction; the publisher sends it only after that commits. */
    private void publishRevisionAdded(ReviewSubmission submission, ReviewRevision revision) {
        events.publish(new ReviewEvent(submission.getId(), submission.getHealerId(), submission.getReviewerId(),
                ReviewEventType.REVISION_ADDED, revision.getRevisionNo()));
    }

    /** Pins the PDF bytes first, so a failed copy saves no revision row. */
    private ReviewRevision saveRevision(UUID submissionId, int revisionNo, PdfJobEntity job, SubmittedVia via) {
        String pinnedKey = files.pin(submissionId, revisionNo, job.getFileUrl());
        return revisions.saveAndFlush(ReviewRevision.builder()
                .submissionId(submissionId).revisionNo(revisionNo).pdfJobId(job.getId())
                .fileUrl(pinnedKey).submittedVia(via).submittedAt(Instant.now()).build());
    }

    /** True when the case has a completed PDF: the single eligibility rule for submitting. */
    @Transactional(readOnly = true)
    public boolean isEligible(UUID caseId) {
        return completedJob(caseId).isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<ReviewSubmission> latestFor(UUID caseId) {
        return submissions.findTopByCaseIdAndStageOrderByCreatedAtDesc(caseId, ReviewStage.REVIEWER);
    }

    /** Moves the healer's open (pending or changes-requested) submissions to a newly linked reviewer. */
    @Transactional
    public void reassignOpen(UUID healerId, UUID reviewerId) {
        List<ReviewSubmission> open = submissions.findByHealerIdAndStageAndStatusIn(
                healerId, ReviewStage.REVIEWER, OPEN_FOR_MOVE);
        open.forEach(s -> s.setReviewerId(reviewerId));
        submissions.saveAll(open);
    }

    @Transactional(readOnly = true)
    public long approvedCount(UUID healerId) {
        return submissions.countByHealerIdAndStageAndStatus(healerId, ReviewStage.REVIEWER, ReviewStatus.APPROVED);
    }

    /** Review state per case for the caller's own cases; ids of other healers' cases are left out. */
    @Transactional(readOnly = true)
    public Map<UUID, CaseReviewStatusResponse> statusesFor(List<UUID> caseIds) {
        UUID healerId = security.getCurrentHealerId();
        List<UUID> owned = cases.findAllById(caseIds).stream()
                .filter(c -> healerId.equals(c.getHealerId()))
                .map(HealingCase::getId)
                .toList();
        Map<UUID, CaseReviewStatusResponse> result = new LinkedHashMap<>();
        if (owned.isEmpty()) {
            return result;
        }
        boolean hasReviewer = assignments.reviewerFor(healerId).isPresent();
        Set<UUID> eligible = pdfJobs.findByHealingCaseIdIn(owned).stream()
                .collect(Collectors.toMap(PdfJobEntity::getHealingCaseId, Function.identity(),
                        ReviewSubmissionService::newerJob))
                .values().stream().filter(ReviewSubmissionService::isSubmittable)
                .map(PdfJobEntity::getHealingCaseId).collect(Collectors.toSet());

        Map<UUID, ReviewSubmission> latestByCase = submissions.findByCaseIdInAndStage(owned, ReviewStage.REVIEWER)
                .stream().collect(Collectors.toMap(ReviewSubmission::getCaseId, Function.identity(),
                        (a, b) -> newer(a, b)));
        Map<UUID, Integer> latestRevisionBySubmission = new HashMap<>();
        if (!latestByCase.isEmpty()) {
            List<UUID> submissionIds = latestByCase.values().stream().map(ReviewSubmission::getId).toList();
            revisions.findBySubmissionIdIn(submissionIds)
                    .forEach(r -> latestRevisionBySubmission.merge(r.getSubmissionId(), r.getRevisionNo(), Math::max));
        }

        for (UUID caseId : owned) {
            ReviewSubmission sub = latestByCase.get(caseId);
            boolean isEligible = eligible.contains(caseId);
            if (sub == null) {
                result.put(caseId, new CaseReviewStatusResponse(caseId,
                        hasReviewer ? "NOT_SUBMITTED" : "NOT_SUBMITTED_NO_REVIEWER", isEligible, null));
            } else {
                result.put(caseId, new CaseReviewStatusResponse(caseId, sub.getStatus().name(), isEligible,
                        latestRevisionBySubmission.get(sub.getId())));
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponse> mySubmissions() {
        UUID healerId = security.getCurrentHealerId();
        List<ReviewSubmission> mine = submissions.findByHealerIdOrderByCreatedAtDesc(healerId);
        if (mine.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<ReviewRevision>> bySubmission = revisions
                .findBySubmissionIdIn(mine.stream().map(ReviewSubmission::getId).toList()).stream()
                .sorted(Comparator.comparingInt(ReviewRevision::getRevisionNo))
                .collect(Collectors.groupingBy(ReviewRevision::getSubmissionId));
        return mine.stream().map(s -> toResponse(s, bySubmission.getOrDefault(s.getId(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public SubmissionResponse getSubmission(UUID submissionId) {
        ReviewSubmission s = ownSubmission(submissionId);
        return toResponse(s, revisions.findBySubmissionIdOrderByRevisionNoAsc(s.getId()));
    }

    /** Presigned URL of one revision file; the masked copy wins when one exists. */
    @Transactional(readOnly = true)
    public PreSignedUrlResponse getFileUrl(UUID submissionId, int revisionNo) {
        ReviewSubmission s = ownSubmission(submissionId);
        ReviewRevision revision = revisions.findBySubmissionIdAndRevisionNo(s.getId(), revisionNo)
                .orElseThrow(() -> new ResourceNotFoundException("Revision", revisionNo));
        String key = revision.getReviewFileUrl() != null ? revision.getReviewFileUrl() : revision.getFileUrl();
        return new PreSignedUrlResponse(files.presignedUrl(key), Instant.now().plus(Duration.ofMinutes(10)));
    }

    /** Row-level check, shared with the comment service: the submission is the caller's own. */
    public ReviewSubmission ownSubmission(UUID submissionId) {
        ReviewSubmission s = submissions.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission", submissionId));
        if (!security.getCurrentHealerId().equals(s.getHealerId())) {
            throw new AccessDeniedException("Submission does not belong to you");
        }
        return s;
    }

    private Optional<PdfJobEntity> completedJob(UUID caseId) {
        return pdfJobs.findTopByHealingCaseIdOrderByCreatedAtDesc(caseId)
                .filter(ReviewSubmissionService::isSubmittable);
    }

    /** The one eligibility rule: a case's latest PDF job is COMPLETED and has a file. */
    private static boolean isSubmittable(PdfJobEntity job) {
        return job.getStatus() == JobStatus.COMPLETED && job.getFileUrl() != null;
    }

    private static PdfJobEntity newerJob(PdfJobEntity a, PdfJobEntity b) {
        Comparator<PdfJobEntity> byCreated = Comparator.comparing(PdfJobEntity::getCreatedAt,
                Comparator.nullsFirst(Comparator.naturalOrder()));
        return byCreated.compare(a, b) >= 0 ? a : b;
    }

    private static ReviewSubmission newer(ReviewSubmission a, ReviewSubmission b) {
        Comparator<ReviewSubmission> byCreated = Comparator.comparing(ReviewSubmission::getCreatedAt,
                Comparator.nullsFirst(Comparator.naturalOrder()));
        return byCreated.compare(a, b) >= 0 ? a : b;
    }

    private static SubmissionResponse toResponse(ReviewSubmission s, List<ReviewRevision> revs) {
        int latest = revs.stream().mapToInt(ReviewRevision::getRevisionNo).max().orElse(0);
        return new SubmissionResponse(s.getId(), s.getCaseId(), s.getHealerId(), s.getReviewerId(), s.getStage(),
                s.getStatus(), s.isDismissedFromApproved(), s.getDismissReason(), s.getSubmittedVia(), latest,
                s.getCreatedAt(), s.getUpdatedAt(),
                revs.stream().map(r -> new RevisionResponse(r.getRevisionNo(), r.getPdfJobId(),
                        r.getSubmittedVia(), r.getSubmittedAt())).toList());
    }
}
