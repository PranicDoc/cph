package com.pranicdoc.cph.review.service;

import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.exception.ReviewRefusedException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.healing.dto.PreSignedUrlResponse;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.rbac.RbacAuthz;
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
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.pranicdoc.cph.review.mapper.SubmissionResponseMapper.toResponse;

/**
 * Instructor side of case review: the queue of submissions assigned to the caller, read access to
 * them, and the decisions (approve, request changes). Row-level rule on every call: the submission is
 * assigned to the caller and is not the caller's own case. A submission moved to another instructor
 * therefore answers 403. A caller without CASE:REVIEW (in only through {@link ReviewAuthz}'s assignment
 * rule) must also be the healer's reviewer <em>now</em>: unlinking leaves submissions, and moving a healer
 * leaves the closed ones, stored against the old reviewer.
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
                ReviewEventType.STATUS_CHANGED, latest, null, s.getStatus()));
        return toResponse(s, revisions.findBySubmissionIdOrderByRevisionNoAsc(s.getId()));
    }

    /** The caller's queue, oldest latest-revision first; healer and case names are batch-loaded. */
    @Transactional(readOnly = true)
    public List<InboxItemResponse> inbox(ReviewStatus status) {
        UUID me = security.getCurrentHealerId();
        Predicate<UUID> reviewsNow = currentlyLinkedHealers(me);
        List<ReviewSubmission> mine = submissions.findByReviewerIdAndStage(me, ReviewStage.REVIEWER).stream()
                .filter(s -> me.equals(s.getReviewerId())) // never a submission whose instructor was erased
                .filter(s -> !me.equals(s.getHealerId()))
                .filter(s -> reviewsNow.test(s.getHealerId()))
                .filter(s -> status == null || s.getStatus() == status)
                .toList();
        if (mine.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<ReviewRevision>> bySubmission = revisions
                .findBySubmissionIdIn(mine.stream().map(ReviewSubmission::getId).toList()).stream()
                .collect(Collectors.groupingBy(ReviewRevision::getSubmissionId));
        Map<UUID, String> healerNames = healers
                .findAllById(mine.stream().map(ReviewSubmission::getHealerId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Healer::getId, h -> h.getFullName() == null ? "" : h.getFullName()));
        Map<UUID, HealingCase> caseById = cases
                .findAllById(mine.stream().map(ReviewSubmission::getCaseId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(HealingCase::getId, Function.identity()));

        return mine.stream()
                .map(s -> toItem(s, bySubmission.getOrDefault(s.getId(), List.of()),
                        healerNames.getOrDefault(s.getHealerId(), ""), caseById.get(s.getCaseId())))
                .sorted(Comparator.comparing(InboxItemResponse::submittedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Transactional(readOnly = true)
    public SubmissionResponse getItem(UUID submissionId) {
        ReviewSubmission s = assignedSubmission(submissionId);
        return toResponse(s, revisions.findBySubmissionIdOrderByRevisionNoAsc(s.getId()));
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
     * Row-level check, shared with the comment service: assigned to the caller, not their own case, and for
     * a caller without CASE:REVIEW, of a healer they review now.
     */
    public ReviewSubmission assignedSubmission(UUID submissionId) {
        ReviewSubmission s = submissions.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission", submissionId));
        UUID me = security.getCurrentHealerId();
        if (s.getStage() != ReviewStage.REVIEWER || !me.equals(s.getReviewerId()) || me.equals(s.getHealerId())
                || !(holdsReviewPermission() || assignments.existsByHealerIdAndReviewerId(s.getHealerId(), me))) {
            throw new AccessDeniedException("Submission is not assigned to you");
        }
        return s;
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

    /** The case's medical condition is the label healers see for a case; fall back to its creation date. */
    private static String caseTitle(HealingCase c) {
        if (c == null) {
            return "Case";
        }
        String condition = c.getMedicalCondition();
        if (condition != null && !condition.isBlank()) {
            return condition;
        }
        return c.getCreatedAt() == null ? "Case"
                : "Case of " + c.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static InboxItemResponse toItem(ReviewSubmission s, List<ReviewRevision> revs, String healerName,
                                            HealingCase c) {
        ReviewRevision latest = revs.stream().max(Comparator.comparingInt(ReviewRevision::getRevisionNo)).orElse(null);
        return new InboxItemResponse(s.getId(), healerName, caseTitle(c),
                latest == null ? 0 : latest.getRevisionNo(), s.getStatus(),
                latest == null ? null : latest.getSubmittedAt());
    }
}
