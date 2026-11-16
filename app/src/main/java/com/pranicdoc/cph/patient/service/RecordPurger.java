package com.pranicdoc.cph.patient.service;

import com.pranicdoc.cph.common.util.TransactionService;
import com.pranicdoc.cph.healing.repository.CaseClosureRepository;
import com.pranicdoc.cph.healing.repository.CaseMedicalSnapshotRepository;
import com.pranicdoc.cph.healing.repository.CaseMedicationRepository;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.healing.repository.CaseSymptomRepository;
import com.pranicdoc.cph.healing.repository.ChakraReadingRepository;
import com.pranicdoc.cph.healing.repository.ChakraScanRepository;
import com.pranicdoc.cph.healing.repository.DeclarationRepository;
import com.pranicdoc.cph.healing.repository.HealerProtocolUnderstandingRepository;
import com.pranicdoc.cph.healing.repository.HealingDetailsRepository;
import com.pranicdoc.cph.healing.repository.PdfJobRepository;
import com.pranicdoc.cph.healing.repository.ProtocolColorRepository;
import com.pranicdoc.cph.healing.repository.ProtocolEntryRepository;
import com.pranicdoc.cph.healing.repository.QualitativeFeedbackRepository;
import com.pranicdoc.cph.healing.repository.SessionRepository;
import com.pranicdoc.cph.healing.repository.SymptomFeedbackRepository;
import com.pranicdoc.cph.healing.repository.TreatmentStepRepository;
import com.pranicdoc.cph.notification.repository.FeedbackLinkRepository;
import com.pranicdoc.cph.patient.repository.PatientMedicalHistoryRepository;
import com.pranicdoc.cph.patient.repository.PatientRepository;
import com.pranicdoc.cph.review.repository.ReviewCommentRepository;
import com.pranicdoc.cph.review.repository.ReviewRevisionRepository;
import com.pranicdoc.cph.review.repository.ReviewSubmissionRepository;
import com.pranicdoc.storage.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Erases a patient's records for good: the patient, medical history, every case with its sessions,
 * scans, protocol, feedback, closure, PDF job and case-review rows (submissions, revisions, comments),
 * and the audit-history copies of those rows.
 *
 * <p>The tables are listed here (child to parent) rather than discovered, so a new table that holds
 * patient data is never erased by accident; add it here and to {@link EraseCoverage} when one is created
 * ({@code EraseCoverageGuardTest} fails the build until you do). Consent records and the audit log are
 * kept on purpose (legal evidence).
 *
 * <p>The PDFs those rows point at in object storage (the job PDFs and the review copies) are deleted
 * once the surrounding transaction has committed, on a background executor so the erase never waits for
 * storage, best effort: a storage failure (or a rejected task) is logged and never affects the erase, and
 * nothing is deleted if the erase rolls back.
 */
@Component
@Slf4j
public class RecordPurger {

    private final PatientRepository patients;
    private final PatientMedicalHistoryRepository medicalHistories;
    private final CaseRepository cases;
    private final CaseClosureRepository closures;
    private final CaseMedicalSnapshotRepository snapshots;
    private final CaseMedicationRepository medications;
    private final CaseSymptomRepository caseSymptoms;
    private final DeclarationRepository declarations;
    private final FeedbackLinkRepository feedbackLinks;
    private final HealerProtocolUnderstandingRepository understandings;
    private final HealingDetailsRepository healingDetails;
    private final TreatmentStepRepository treatmentSteps;
    private final SessionRepository sessions;
    private final ChakraScanRepository scans;
    private final ChakraReadingRepository readings;
    private final ProtocolEntryRepository protocolEntries;
    private final ProtocolColorRepository protocolColors;
    private final QualitativeFeedbackRepository qualitativeFeedback;
    private final SymptomFeedbackRepository symptomFeedback;
    private final PdfJobRepository pdfJobs;
    private final ReviewSubmissionRepository submissions;
    private final ReviewRevisionRepository revisions;
    private final ReviewCommentRepository comments;
    private final StorageService storage;
    private final Executor executor;
    private final TransactionService transactions;

    // One argument per repository so the erase plan below reads as a plain list of what it touches.
    @SuppressWarnings("java:S107")
    public RecordPurger(PatientRepository patients, PatientMedicalHistoryRepository medicalHistories,
                        CaseRepository cases, CaseClosureRepository closures,
                        CaseMedicalSnapshotRepository snapshots, CaseMedicationRepository medications,
                        CaseSymptomRepository caseSymptoms, DeclarationRepository declarations,
                        FeedbackLinkRepository feedbackLinks, HealerProtocolUnderstandingRepository understandings,
                        HealingDetailsRepository healingDetails, TreatmentStepRepository treatmentSteps,
                        SessionRepository sessions, ChakraScanRepository scans, ChakraReadingRepository readings,
                        ProtocolEntryRepository protocolEntries, ProtocolColorRepository protocolColors,
                        QualitativeFeedbackRepository qualitativeFeedback, SymptomFeedbackRepository symptomFeedback,
                        PdfJobRepository pdfJobs, ReviewSubmissionRepository submissions,
                        ReviewRevisionRepository revisions, ReviewCommentRepository comments,
                        StorageService storage, @Qualifier("notificationExecutor") Executor executor,
                        TransactionService transactions) {
        this.patients = patients;
        this.medicalHistories = medicalHistories;
        this.cases = cases;
        this.closures = closures;
        this.snapshots = snapshots;
        this.medications = medications;
        this.caseSymptoms = caseSymptoms;
        this.declarations = declarations;
        this.feedbackLinks = feedbackLinks;
        this.understandings = understandings;
        this.healingDetails = healingDetails;
        this.treatmentSteps = treatmentSteps;
        this.sessions = sessions;
        this.scans = scans;
        this.readings = readings;
        this.protocolEntries = protocolEntries;
        this.protocolColors = protocolColors;
        this.qualitativeFeedback = qualitativeFeedback;
        this.symptomFeedback = symptomFeedback;
        this.pdfJobs = pdfJobs;
        this.submissions = submissions;
        this.revisions = revisions;
        this.comments = comments;
        this.storage = storage;
        this.executor = executor;
        this.transactions = transactions;
    }

    /** Erases one patient and everything of theirs. Runs in the caller's transaction, or its own. */
    @Transactional
    public void purgePatient(UUID patientId) {
        List<UUID> caseIds = cases.findIdsByPatientId(patientId);
        List<UUID> submissionIds = caseIds.isEmpty() ? List.of() : submissions.findIdsByCaseIds(caseIds);
        List<String> files = new ArrayList<>();
        if (!caseIds.isEmpty()) {
            files.addAll(pdfJobs.findFileUrlsByCaseIds(caseIds));
        }
        files.addAll(reviewFilesOf(submissionIds));

        List<UUID> patient = List.of(patientId);
        medicalHistories.eraseAuditByPatientIds(patient);
        medicalHistories.eraseByPatientIds(patient);
        purgeCases(caseIds, submissionIds);
        patients.eraseAuditByIds(patient);
        patients.eraseByIds(patient);
        deleteStoredFilesAfterCommit(files);
    }

    /**
     * Erases every review submission the healer opened, with its revisions, comments and review copies.
     * Their patients' purge already removes these; this catches any that point at the healer through
     * another case. Runs in the caller's transaction, or its own.
     */
    @Transactional
    public void purgeReviewSubmissionsOfHealer(UUID healerId) {
        List<UUID> submissionIds = submissions.findIdsByHealerId(healerId);
        List<String> files = reviewFilesOf(submissionIds);
        purgeSubmissions(submissionIds);
        deleteStoredFilesAfterCommit(files);
    }

    /** Case ids of a patient (also the soft-deleted ones), for callers that need to clean up files. */
    public List<UUID> caseIdsOf(UUID patientId) {
        return cases.findIdsByPatientId(patientId);
    }

    /** The cases, child tables first. The order matters for the foreign keys. */
    private void purgeCases(List<UUID> caseIds, List<UUID> submissionIds) {
        if (caseIds.isEmpty()) {
            return;
        }
        closures.eraseAuditByCaseIds(caseIds);
        closures.eraseByCaseIds(caseIds);
        snapshots.eraseAuditByCaseIds(caseIds);
        snapshots.eraseByCaseIds(caseIds);
        medications.eraseAuditByCaseIds(caseIds);
        medications.eraseByCaseIds(caseIds);
        List<UUID> symptomIds = caseSymptoms.findIdsByCaseIds(caseIds);
        if (!symptomIds.isEmpty()) {
            symptomFeedback.eraseAuditByCaseSymptomIds(symptomIds);
            symptomFeedback.eraseByCaseSymptomIds(symptomIds);
            caseSymptoms.eraseAuditByIds(symptomIds);
            caseSymptoms.eraseByIds(symptomIds);
        }
        declarations.eraseAuditByCaseIds(caseIds);
        declarations.eraseByCaseIds(caseIds);
        feedbackLinks.eraseByCaseIds(caseIds);
        understandings.eraseAuditByCaseIds(caseIds);
        understandings.eraseByCaseIds(caseIds);
        purgeHealingDetails(healingDetails.findIdsByCaseIds(caseIds));
        purgeSessions(sessions.findIdsByCaseIds(caseIds));
        pdfJobs.eraseAuditByCaseIds(caseIds);
        pdfJobs.eraseByCaseIds(caseIds);
        purgeSubmissions(submissionIds);
        cases.eraseAuditByIds(caseIds);
        cases.eraseByIds(caseIds);
    }

    private void purgeHealingDetails(List<UUID> detailsIds) {
        if (detailsIds.isEmpty()) {
            return;
        }
        List<UUID> stepIds = treatmentSteps.findIdsByHealingDetailsIds(detailsIds);
        if (!stepIds.isEmpty()) {
            // The step's target list is a plain value table: no id of its own, no history copy.
            treatmentSteps.eraseTargetsByStepIds(stepIds);
            treatmentSteps.eraseAuditByIds(stepIds);
            treatmentSteps.eraseByIds(stepIds);
        }
        healingDetails.eraseAuditByIds(detailsIds);
        healingDetails.eraseByIds(detailsIds);
    }

    private void purgeSessions(List<UUID> sessionIds) {
        if (sessionIds.isEmpty()) {
            return;
        }
        List<UUID> scanIds = scans.findIdsBySessionIds(sessionIds);
        if (!scanIds.isEmpty()) {
            readings.eraseAuditByScanIds(scanIds);
            readings.eraseByScanIds(scanIds);
            scans.eraseAuditByIds(scanIds);
            scans.eraseByIds(scanIds);
        }
        List<UUID> entryIds = protocolEntries.findIdsBySessionIds(sessionIds);
        if (!entryIds.isEmpty()) {
            protocolColors.eraseAuditByEntryIds(entryIds);
            protocolColors.eraseByEntryIds(entryIds);
            protocolEntries.eraseAuditByIds(entryIds);
            protocolEntries.eraseByIds(entryIds);
        }
        qualitativeFeedback.eraseAuditBySessionIds(sessionIds);
        qualitativeFeedback.eraseBySessionIds(sessionIds);
        symptomFeedback.eraseAuditBySessionIds(sessionIds);
        symptomFeedback.eraseBySessionIds(sessionIds);
        sessions.eraseAuditByIds(sessionIds);
        sessions.eraseByIds(sessionIds);
    }

    /** Review threads: comments (a reply is unlinked from its parent first), then revisions, then the submissions. */
    private void purgeSubmissions(List<UUID> submissionIds) {
        if (submissionIds.isEmpty()) {
            return;
        }
        comments.unlinkParentsBySubmissionIds(submissionIds);
        comments.eraseAuditBySubmissionIds(submissionIds);
        comments.eraseBySubmissionIds(submissionIds);
        List<UUID> revisionIds = revisions.findIdsBySubmissionIds(submissionIds);
        if (!revisionIds.isEmpty()) {
            comments.unlinkParentsByRevisionIds(revisionIds);
            comments.eraseAuditByRevisionIds(revisionIds);
            comments.eraseByRevisionIds(revisionIds);
            revisions.eraseAuditByIds(revisionIds);
            revisions.eraseByIds(revisionIds);
        }
        submissions.eraseAuditByIds(submissionIds);
        submissions.eraseByIds(submissionIds);
    }

    private List<String> reviewFilesOf(List<UUID> submissionIds) {
        List<String> files = new ArrayList<>();
        if (!submissionIds.isEmpty()) {
            files.addAll(revisions.findFileUrlsBySubmissionIds(submissionIds));
            files.addAll(revisions.findReviewFileUrlsBySubmissionIds(submissionIds));
        }
        return files;
    }

    /**
     * After the surrounding transaction commits (at once when there is none), on the background executor,
     * best effort: a failed key or a rejected task is logged and never thrown, the erase is already done.
     */
    private void deleteStoredFilesAfterCommit(List<String> keys) {
        List<String> unique = List.copyOf(new LinkedHashSet<>(keys));
        if (unique.isEmpty()) {
            return;
        }
        transactions.runAfterCommitOrNow(() -> submitDeletion(unique));
    }

    private void submitDeletion(List<String> keys) {
        try {
            executor.execute(() -> deleteAll(keys));
        } catch (Exception e) {
            log.warn("Could not queue the deletion of {} stored file(s) after an erase; remove them by hand: {}",
                    keys.size(), e.toString());
        }
    }

    private void deleteAll(List<String> keys) {
        for (String key : keys) {
            try {
                storage.delete(key);
            } catch (Exception e) {
                log.warn("Could not delete stored file {} after an erase; remove it by hand: {}", key, e.toString());
            }
        }
    }
}
