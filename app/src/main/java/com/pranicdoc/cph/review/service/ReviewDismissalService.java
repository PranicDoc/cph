package com.pranicdoc.cph.review.service;

import com.pranicdoc.cph.review.event.ReviewEvent;
import com.pranicdoc.cph.review.event.ReviewEventPublisher;
import com.pranicdoc.cph.review.event.ReviewEventType;
import com.pranicdoc.cph.review.model.ReviewRevision;
import com.pranicdoc.cph.review.model.ReviewStage;
import com.pranicdoc.cph.review.model.ReviewStatus;
import com.pranicdoc.cph.review.model.ReviewSubmission;
import com.pranicdoc.cph.review.repository.ReviewRevisionRepository;
import com.pranicdoc.cph.review.repository.ReviewSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stale-approval dismissal, like a stale approval on a pull request: a write that changes an
 * approved case's data sets its submission to DISMISSED (from approval, with the reason), so the
 * approval no longer stands for content the instructor has not seen. Auto-submit then leaves the
 * case alone until the healer submits it again, which opens a new submission. Called from every
 * case write path and runs in that write's transaction, so a rolled-back write dismisses nothing.
 */
@Service
@RequiredArgsConstructor
public class ReviewDismissalService {

    private final ReviewSubmissionRepository submissions;
    private final ReviewRevisionRepository revisions;
    private final ReviewEventPublisher events;

    /** Dismisses the case's latest submission when it is APPROVED; otherwise does nothing. */
    @Transactional
    public void onCaseModified(UUID caseId, String reason) {
        submissions.findTopByCaseIdAndStageOrderByCreatedAtDesc(caseId, ReviewStage.REVIEWER)
                .ifPresent(latest -> dismissIfApproved(latest, reason));
    }

    /** Same as {@link #onCaseModified} for a write that touched several cases at once, in one query. */
    @Transactional
    public void onCasesModified(Collection<UUID> caseIds, String reason) {
        if (caseIds.isEmpty()) {
            return;
        }
        Comparator<ReviewSubmission> byCreated = Comparator.comparing(ReviewSubmission::getCreatedAt,
                Comparator.nullsFirst(Comparator.naturalOrder()));
        submissions.findByCaseIdInAndStage(caseIds, ReviewStage.REVIEWER).stream()
                .collect(Collectors.toMap(ReviewSubmission::getCaseId, Function.identity(),
                        (a, b) -> byCreated.compare(a, b) >= 0 ? a : b))
                .values()
                .forEach(latest -> dismissIfApproved(latest, reason));
    }

    private void dismissIfApproved(ReviewSubmission latest, String reason) {
        if (latest.getStatus() != ReviewStatus.APPROVED) {
            return;
        }
        latest.setStatus(ReviewStatus.DISMISSED);
        latest.setDismissedFromApproved(true);
        latest.setDismissReason(reason);
        submissions.save(latest);
        Integer revisionNo = revisions.findTopBySubmissionIdOrderByRevisionNoDesc(latest.getId())
                .map(ReviewRevision::getRevisionNo).orElse(null);
        // Sent after the write commits, and never if it rolls back.
        events.publish(new ReviewEvent(latest.getId(), latest.getHealerId(), latest.getReviewerId(),
                ReviewEventType.STATUS_CHANGED, revisionNo));
    }
}
