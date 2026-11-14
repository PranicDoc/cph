package com.pranicdoc.cph.review.service;

import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.model.PdfJobEntity;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.healing.repository.PdfJobRepository;
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

    private final PdfJobRepository pdfJobs;
    private final CaseRepository cases;
    private final HealerRepository healers;
    private final ReviewerAssignmentService assignments;
    private final ReviewSubmissionService submissions;

    public ReviewAutoSubmitService(PdfJobRepository pdfJobs, CaseRepository cases, HealerRepository healers,
                                   ReviewerAssignmentService assignments, ReviewSubmissionService submissions) {
        this.pdfJobs = pdfJobs;
        this.cases = cases;
        this.healers = healers;
        this.assignments = assignments;
        this.submissions = submissions;
    }

    public void onPdfCompleted(String jobId) {
        Optional<PdfJobEntity> job = pdfJobs.findFirstByJobIdOrderByCreatedAtDesc(jobId);
        if (job.isEmpty() || job.get().getHealingCaseId() == null) {
            log.debug("Auto-submit: no case for PDF job {}", jobId);
            return;
        }
        UUID caseId = job.get().getHealingCaseId();
        Optional<HealingCase> healingCase = cases.findById(caseId);
        if (healingCase.isEmpty()) {
            return;
        }
        UUID healerId = healingCase.get().getHealerId();
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
