package com.pranicdoc.cph.review.service;

import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.exception.ReviewRefusedException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.healing.dto.PreSignedUrlResponse;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.rbac.RbacAuthz;
import com.pranicdoc.cph.review.dto.AdminReviewItemResponse;
import com.pranicdoc.cph.review.dto.InboxItemResponse;
import com.pranicdoc.cph.review.dto.SubmissionResponse;
import com.pranicdoc.cph.review.event.ReviewEvent;
import com.pranicdoc.cph.review.event.ReviewEventPublisher;
import com.pranicdoc.cph.review.event.ReviewEventType;
import com.pranicdoc.cph.review.model.ReviewRevision;
import com.pranicdoc.cph.review.model.ReviewStage;
import com.pranicdoc.cph.review.model.ReviewStatus;
import com.pranicdoc.cph.review.model.ReviewSubmission;
import com.pranicdoc.cph.review.model.ReviewerAssignment;
import com.pranicdoc.cph.review.repository.ReviewRevisionRepository;
import com.pranicdoc.cph.review.repository.ReviewSubmissionRepository;
import com.pranicdoc.cph.review.repository.ReviewerAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Instructor side of case review: the queue of submissions assigned to the caller, read access to
 * them, and the decisions (approve, request changes). Row-level rule on every call: the submission is
 * assigned to the caller and is not the caller's own case. A submission moved to another instructor
 * therefore answers 403. A caller without CASE:REVIEW (in only through {@link ReviewAuthz}'s assignment
 * rule) must also be the healer's reviewer <em>now</em>: unlinking leaves submissions, and moving a healer
 * leaves the closed ones, stored against the old reviewer.
 *
 * <p>A caller who is a super admin <em>now</em> bypasses the row rule: any reviewer-stage submission, whether
 * assigned to someone else, to no one, or their own. The bypass never rests on the stored ids, because a
 * self-assignment can outlive the role. The super admin's "All reviews" list is here too.
 */
@Service
@RequiredArgsConstructor
public class ReviewInboxService {

    static final String STALE_REVISION_MESSAGE = "A newer version was submitted. Refresh to review it.";

    /** The states an instructor can decide on: pending review, or changes requested and not yet resubmitted. */
    private static final Set<ReviewStatus> DECIDABLE =
            EnumSet.of(ReviewStatus.PENDING_REVIEW, ReviewStatus.CHANGES_REQUESTED);

    private final ReviewSubmissionRepository submissions;
    private final ReviewRevisionRepository revisions;
    private final CaseRepository cases;
    private final HealerRepository healers;
    private final ReviewFileStore files;
    private final SecurityContextUtil security;
    private final ReviewEventPublisher events;
    private final RbacAuthz rbacAuthz;
    private final ReviewerAssignmentRepository assignments;

    /** Approves the latest revision; counts toward the healer's approved cases. */
    @Auditable(action = "APPROVE_REVIEW")
    @Transactional
    public SubmissionResponse approve(UUID submissionId, int revisionNo) {
        return decide(submissionId, revisionNo, ReviewStatus.APPROVED);
    }

    /** Hands the latest revision back to the healer for changes. */
    @Auditable(action = "REQUEST_CHANGES")
    @Transactional
    public SubmissionResponse requestChanges(UUID submissionId, int revisionNo) {
        return decide(submissionId, revisionNo, ReviewStatus.CHANGES_REQUESTED);
    }

    /**
     * A decision applies to the latest revision of an open submission only. A healer resubmitting at
     * the same moment is caught either here (a newer revision is already there) or by the
     * submission's version column when both write it.
     */
    private SubmissionResponse decide(UUID submissionId, int revisionNo, ReviewStatus decision) {
        ReviewSubmission s = assignedSubmission(submissionId);
        if (!DECIDABLE.contains(s.getStatus())) {
            throw new ReviewRefusedException(s.getStatus() == ReviewStatus.APPROVED
                    ? "This submission is already approved."
                    : "This submission was dismissed. The healer needs to submit the case again.");
        }
        int latest = revisions.findTopBySubmissionIdOrderByRevisionNoDesc(s.getId())
                .map(ReviewRevision::getRevisionNo).orElse(0);
        if (revisionNo != latest) {
            throw new ReviewRefusedException(STALE_REVISION_MESSAGE);
        }
        s.setStatus(decision);
        submissions.saveAndFlush(s);
        events.publish(new ReviewEvent(s.getId(), s.getHealerId(), s.getReviewerId(),
                ReviewEventType.STATUS_CHANGED, latest, null, s.getStatus(),
                ReviewEvent.standIn(security.getCurrentHealerId(), s.getReviewerId())));
        return respond(s);
    }

    /** The caller's queue, oldest latest-revision first; healer and case names are batch-loaded. */
    @Transactional(readOnly = true)
    public List<InboxItemResponse> inbox(ReviewStatus status) {
        UUID me = security.getCurrentHealerId();
        Predicate<UUID> reviewsNow = currentlyLinkedHealers(me);
        boolean superAdmin = rbacAuthz.isSuperAdmin(); // the only caller who reviews their own cases
        List<ReviewSubmission> mine = submissions.findByReviewerIdAndStage(me, ReviewStage.REVIEWER).stream()
                .filter(s -> me.equals(s.getReviewerId())) // never a submission whose instructor was erased
                .filter(s -> superAdmin || !me.equals(s.getHealerId()))
                .filter(s -> reviewsNow.test(s.getHealerId()))
                .filter(s -> status == null || s.getStatus() == status)
                .toList();
        if (mine.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<ReviewRevision>> bySubmission = revisionsOf(mine);
        Map<UUID, String> healerNames = SubmissionLabels.namesOf(healers,
                mine.stream().map(ReviewSubmission::getHealerId).collect(Collectors.toSet()));
        Map<UUID, HealingCase> caseById = SubmissionLabels.casesOf(cases, mine);

        return mine.stream()
                .map(s -> toItem(s, bySubmission.getOrDefault(s.getId(), List.of()),
                        healerNames.getOrDefault(s.getHealerId(), ""), caseById.get(s.getCaseId())))
                .sorted(Comparator.comparing(InboxItemResponse::submittedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /**
     * Every reviewer-stage submission, oldest latest-revision first; each filter is optional. Names and cases
     * are batch-loaded. The caller (the SUPADM-only admin route) enforces who may ask.
     */
    @Transactional(readOnly = true)
    public List<AdminReviewItemResponse> allReviews(ReviewStatus status, UUID reviewerId, UUID healerId) {
        List<ReviewSubmission> matching = submissions.findByStage(ReviewStage.REVIEWER).stream()
                .filter(s -> status == null || s.getStatus() == status)
                .filter(s -> reviewerId == null || reviewerId.equals(s.getReviewerId()))
                .filter(s -> healerId == null || healerId.equals(s.getHealerId()))
                .toList();
        if (matching.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<ReviewRevision>> bySubmission = revisionsOf(matching);
        SubmissionLabels labels = SubmissionLabels.of(healers, cases, matching);

        return matching.stream()
                .map(s -> toAdminItem(s, bySubmission.getOrDefault(s.getId(), List.of()), labels.names(),
                        labels.cases().get(s.getCaseId())))
                .sorted(Comparator.comparing(AdminReviewItemResponse::submittedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Transactional(readOnly = true)
    public SubmissionResponse getItem(UUID submissionId) {
        return respond(assignedSubmission(submissionId));
    }

    /** Takes a submission that has already passed the row check. */
    private SubmissionResponse respond(ReviewSubmission s) {
        return SubmissionLabels.of(healers, cases, List.of(s))
                .respond(s, revisions.findBySubmissionIdOrderByRevisionNoAsc(s.getId()));
    }

    /** Presigned URL of one revision; the masked copy wins when one exists. */
    @Transactional(readOnly = true)
    public PreSignedUrlResponse getFileUrl(UUID submissionId, int revisionNo) {
        ReviewSubmission s = assignedSubmission(submissionId);
        ReviewRevision revision = revisions.findBySubmissionIdAndRevisionNo(s.getId(), revisionNo)
                .orElseThrow(() -> new ResourceNotFoundException("Revision", revisionNo));
        String key = revision.getReviewFileUrl() != null ? revision.getReviewFileUrl() : revision.getFileUrl();
        return new PreSignedUrlResponse(files.presignedUrl(key), Instant.now().plus(Duration.ofMinutes(10)));
    }

    /**
     * Row-level check, shared with the comment service. A reviewer-stage submission only, and either the
     * caller is a super admin, or it is assigned to the caller, is not their own case and, for a caller
     * without CASE:REVIEW, belongs to a healer they review now.
     */
    public ReviewSubmission assignedSubmission(UUID submissionId) {
        ReviewSubmission s = submissions.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission", submissionId));
        if (s.getStage() != ReviewStage.REVIEWER || !(rbacAuthz.isSuperAdmin() || isAssignedReviewer(s))) {
            throw new AccessDeniedException("Submission is not assigned to you");
        }
        return s;
    }

    private boolean isAssignedReviewer(ReviewSubmission s) {
        UUID me = security.getCurrentHealerId();
        return me.equals(s.getReviewerId()) && !me.equals(s.getHealerId())
                && (holdsReviewPermission() || assignments.existsByHealerIdAndReviewerId(s.getHealerId(), me));
    }

    private boolean holdsReviewPermission() {
        return rbacAuthz.hasPermission("CASE", "REVIEW");
    }

    /** Which healers' rows the caller may list: all for a CASE:REVIEW holder, else those linked to them now. */
    private Predicate<UUID> currentlyLinkedHealers(UUID me) {
        if (holdsReviewPermission()) {
            return healerId -> true;
        }
        Set<UUID> linked = assignments.findByReviewerId(me).stream()
                .map(ReviewerAssignment::getHealerId)
                .collect(Collectors.toSet());
        return linked::contains;
    }

    private Map<UUID, List<ReviewRevision>> revisionsOf(List<ReviewSubmission> rows) {
        return revisions.findBySubmissionIdIn(rows.stream().map(ReviewSubmission::getId).toList()).stream()
                .collect(Collectors.groupingBy(ReviewRevision::getSubmissionId));
    }

    private static ReviewRevision latestOf(List<ReviewRevision> revs) {
        return revs.stream().max(Comparator.comparingInt(ReviewRevision::getRevisionNo)).orElse(null);
    }

    private static AdminReviewItemResponse toAdminItem(ReviewSubmission s, List<ReviewRevision> revs,
                                                       Map<UUID, String> names, HealingCase c) {
        ReviewRevision latest = latestOf(revs);
        return new AdminReviewItemResponse(s.getId(), s.getHealerId(), names.getOrDefault(s.getHealerId(), ""),
                s.getReviewerId(), s.getReviewerId() == null ? null : names.get(s.getReviewerId()),
                SubmissionLabels.caseTitle(c),
                latest == null ? 0 : latest.getRevisionNo(), s.getStatus(),
                latest == null ? null : latest.getSubmittedAt());
    }

    private static InboxItemResponse toItem(ReviewSubmission s, List<ReviewRevision> revs, String healerName,
                                            HealingCase c) {
        ReviewRevision latest = latestOf(revs);
        return new InboxItemResponse(s.getId(), healerName, SubmissionLabels.caseTitle(c),
                latest == null ? 0 : latest.getRevisionNo(), s.getStatus(),
                latest == null ? null : latest.getSubmittedAt());
    }
}
