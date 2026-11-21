package com.pranicdoc.cph.review.service;

import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.model.PdfJobEntity;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.healing.repository.PdfJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/** Finds the case and its healer behind a PDF job id, for the webhook that has no signed-in user. */
@Service
@RequiredArgsConstructor
public class PdfJobCaseResolver {

    /** The case a PDF job belongs to and the healer who owns it. */
    public record JobCase(UUID caseId, UUID healerId) {
    }

    private final PdfJobRepository pdfJobs;
    private final CaseRepository cases;

    /** Empty for an unknown job, a job with no case, or a missing case. */
    public Optional<JobCase> resolve(String jobId) {
        Optional<PdfJobEntity> job = pdfJobs.findFirstByJobIdOrderByCreatedAtDesc(jobId);
        if (job.isEmpty() || job.get().getHealingCaseId() == null) {
            return Optional.empty();
        }
        UUID caseId = job.get().getHealingCaseId();
        return cases.findById(caseId).map(HealingCase::getHealerId).map(healerId -> new JobCase(caseId, healerId));
    }
}
