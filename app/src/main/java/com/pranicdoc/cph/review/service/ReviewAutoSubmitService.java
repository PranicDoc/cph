package com.pranicdoc.cph.review.service;

import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.exception.ReviewRefusedException;
import com.pranicdoc.cph.review.model.ReviewStatus;
import com.pranicdoc.cph.review.model.ReviewSubmission;
import com.pranicdoc.cph.review.model.SubmittedVia;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Sends a freshly generated case PDF to the healer's instructor when the healer opted in. Called
 * by the internal PDF-completed webhook, so there is no signed-in user: the healer is resolved
 * from the PDF job's case. Every skip is silent (no preference, no instructor, already approved).
 */
@Service
@Slf4j
public class ReviewAutoSubmitService {

    private final PdfJobCaseResolver jobCases;
    private final HealerRepository healers;
    private final ReviewerAssignmentService assignments;
    private final ReviewSubmissionService submissions;

    public ReviewAutoSubmitService(PdfJobCaseResolver jobCases, HealerRepository healers,
                                   ReviewerAssignmentService assignments, ReviewSubmissionService submissions) {
        this.jobCases = jobCases;
        this.healers = healers;
        this.assignments = assignments;
        this.submissions = submissions;
    }

    public void onPdfCompleted(String jobId) {
        Optional<PdfJobCaseResolver.JobCase> jobCase = jobCases.resolve(jobId);
        if (jobCase.isEmpty()) {
            log.debug("Auto-submit: no case for PDF job {}", jobId);
            return;
        }
        UUID caseId = jobCase.get().caseId();
        UUID healerId = jobCase.get().healerId();
        boolean optedIn = healers.findById(healerId).map(h -> h.isAutoSubmitForReview()).orElse(false);
        if (!optedIn) {
            return;
        }
        if (assignments.reviewerFor(healerId).isEmpty()) {
            log.info("Auto-submit skipped for case {}: healer {} has no instructor linked", caseId, healerId);
            return;
        }
        if (approvalStands(submissions.latestFor(caseId))) {
            return;
        }
        try {
            submissions.submitAs(healerId, caseId, SubmittedVia.AUTO);
        } catch (ReviewRefusedException refused) {
            log.info("Auto-submit skipped for case {}: {}", caseId, refused.getMessage());
        }
    }

    /** An approved case, or one whose approval was dismissed, is never re-sent automatically. */
    private static boolean approvalStands(Optional<ReviewSubmission> latest) {
        return latest.filter(s -> s.getStatus() == ReviewStatus.APPROVED
                || (s.getStatus() == ReviewStatus.DISMISSED && s.isDismissedFromApproved())).isPresent();
    }
}
