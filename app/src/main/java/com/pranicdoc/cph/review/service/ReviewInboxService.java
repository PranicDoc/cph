package com.pranicdoc.cph.review.service;

import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.healing.dto.PreSignedUrlResponse;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.review.dto.InboxItemResponse;
import com.pranicdoc.cph.review.dto.RevisionResponse;
import com.pranicdoc.cph.review.dto.SubmissionResponse;
import com.pranicdoc.cph.review.model.ReviewRevision;
import com.pranicdoc.cph.review.model.ReviewStage;
import com.pranicdoc.cph.review.model.ReviewStatus;
import com.pranicdoc.cph.review.model.ReviewSubmission;
import com.pranicdoc.cph.review.repository.ReviewRevisionRepository;
import com.pranicdoc.cph.review.repository.ReviewSubmissionRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Instructor side of case review: the queue of submissions assigned to the caller and read access to
 * them. Row-level rule on every read: the submission is assigned to the caller and is not the
 * caller's own case. A submission moved to another instructor therefore answers 403.
 */
@Service
public class ReviewInboxService {

    private final ReviewSubmissionRepository submissions;
    private final ReviewRevisionRepository revisions;
    private final CaseRepository cases;
    private final HealerRepository healers;
    private final ReviewFileStore files;
    private final SecurityContextUtil security;

    public ReviewInboxService(ReviewSubmissionRepository submissions, ReviewRevisionRepository revisions,
                              CaseRepository cases, HealerRepository healers, ReviewFileStore files,
                              SecurityContextUtil security) {
        this.submissions = submissions;
        this.revisions = revisions;
        this.cases = cases;
        this.healers = healers;
        this.files = files;
        this.security = security;
    }

    /** The caller's queue, oldest latest-revision first; healer and case names are batch-loaded. */
    @Transactional(readOnly = true)
    public List<InboxItemResponse> inbox(ReviewStatus status) {
        UUID me = security.getCurrentHealerId();
        List<ReviewSubmission> mine = submissions.findByReviewerIdAndStage(me, ReviewStage.REVIEWER).stream()
                .filter(s -> !me.equals(s.getHealerId()))
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
        List<ReviewRevision> revs = revisions.findBySubmissionIdOrderByRevisionNoAsc(s.getId());
        int latest = revs.stream().mapToInt(ReviewRevision::getRevisionNo).max().orElse(0);
        return new SubmissionResponse(s.getId(), s.getCaseId(), s.getHealerId(), s.getReviewerId(), s.getStage(),
                s.getStatus(), s.isDismissedFromApproved(), s.getDismissReason(), s.getSubmittedVia(), latest,
                s.getCreatedAt(), s.getUpdatedAt(),
                revs.stream().map(r -> new RevisionResponse(r.getRevisionNo(), r.getPdfJobId(),
                        r.getSubmittedVia(), r.getSubmittedAt())).toList());
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

    private ReviewSubmission assignedSubmission(UUID submissionId) {
        ReviewSubmission s = submissions.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission", submissionId));
        UUID me = security.getCurrentHealerId();
        if (s.getStage() != ReviewStage.REVIEWER || !me.equals(s.getReviewerId()) || me.equals(s.getHealerId())) {
            throw new AccessDeniedException("Submission is not assigned to you");
        }
        return s;
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
