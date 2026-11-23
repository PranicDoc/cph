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
import com.pranicdoc.storage.exception.StorageException;
import com.pranicdoc.storage.service.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The file-deletion contract of the purger against mocked repositories. The erase SQL itself, the order
 * and the history rows are proven on real SQL by the review-data integration tests.
 */
class RecordPurgerTest {

    private final UUID patientId = UUID.randomUUID();
    private final UUID caseId = UUID.randomUUID();
    private final UUID submissionId = UUID.randomUUID();

    private final PatientRepository patients = mock(PatientRepository.class);
    private final CaseRepository cases = mock(CaseRepository.class);
    private final PdfJobRepository pdfJobs = mock(PdfJobRepository.class);
    private final ReviewSubmissionRepository submissions = mock(ReviewSubmissionRepository.class);
    private final ReviewRevisionRepository revisions = mock(ReviewRevisionRepository.class);
    private final ReviewCommentRepository comments = mock(ReviewCommentRepository.class);
    private final SessionRepository sessions = mock(SessionRepository.class);
    private final StorageService storage = mock(StorageService.class);
    /** Tasks the purger hands to its executor; tests run them by hand. */
    private final List<Runnable> queued = new java.util.ArrayList<>();

    private RecordPurger purgerWith(Executor executor) {
        return new RecordPurger(patients, mock(PatientMedicalHistoryRepository.class), cases,
                mock(CaseClosureRepository.class), mock(CaseMedicalSnapshotRepository.class),
                mock(CaseMedicationRepository.class), mock(com.pranicdoc.cph.healing.repository.CaseChiefComplaintRepository.class),
                mock(CaseSymptomRepository.class),
                mock(DeclarationRepository.class), mock(FeedbackLinkRepository.class),
                mock(HealerProtocolUnderstandingRepository.class), mock(HealingDetailsRepository.class),
                mock(TreatmentStepRepository.class), sessions, mock(ChakraScanRepository.class),
                mock(ChakraReadingRepository.class), mock(ProtocolEntryRepository.class),
                mock(ProtocolColorRepository.class), mock(QualitativeFeedbackRepository.class),
                mock(SymptomFeedbackRepository.class), pdfJobs, submissions, revisions, comments,
                storage, executor, new TransactionService());
    }

    private RecordPurger purger;

    @BeforeEach
    void setUp() {
        purger = purgerWith(queued::add);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void storedFiles() {
        when(cases.findIdsByPatientId(patientId)).thenReturn(List.of(caseId));
        when(submissions.findIdsByCaseIds(List.of(caseId))).thenReturn(List.of(submissionId));
        when(pdfJobs.findFileUrlsByCaseIds(List.of(caseId))).thenReturn(List.of("pdf/job-1.pdf", "pdf/job-2.pdf"));
        when(revisions.findFileUrlsBySubmissionIds(List.of(submissionId))).thenReturn(List.of("review/sub-1/r1.pdf"));
        when(revisions.findReviewFileUrlsBySubmissionIds(List.of(submissionId)))
                .thenReturn(List.of("review/sub-1/masked-r1.pdf"));
    }

    private void runQueued() {
        queued.forEach(Runnable::run);
    }

    @Test
    void thePatientRowGoesAfterTheirCasesAndTheirHistoryCopyWithIt() {
        storedFiles();
        when(sessions.findIdsByCaseIds(List.of(caseId))).thenReturn(List.of(UUID.randomUUID()));

        purger.purgePatient(patientId);

        var order = inOrder(sessions, cases, patients);
        order.verify(sessions).eraseByIds(org.mockito.ArgumentMatchers.anyCollection());
        order.verify(cases).eraseByIds(List.of(caseId));
        order.verify(patients).eraseAuditByIds(List.of(patientId));
        order.verify(patients).eraseByIds(List.of(patientId));
    }

    @Test
    void aPatientWithoutCasesIsStillErased() {
        purger.purgePatient(patientId);

        verify(patients).eraseByIds(List.of(patientId));
        verify(cases, never()).eraseByIds(org.mockito.ArgumentMatchers.anyCollection());
    }

    @Test
    void purgePatient_deletesStoredFilesAfterCommit_inOneBackgroundTask() {
        storedFiles();
        TransactionSynchronizationManager.initSynchronization();

        purger.purgePatient(patientId);

        assertThat(queued).isEmpty();
        verify(storage, never()).delete(anyString());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(queued).hasSize(1);
        verify(storage, never()).delete(anyString()); // queued, not run on the committing thread
        runQueued();
        verify(storage).delete("pdf/job-1.pdf");
        verify(storage).delete("pdf/job-2.pdf");
        verify(storage).delete("review/sub-1/r1.pdf");
        verify(storage).delete("review/sub-1/masked-r1.pdf");
    }

    @Test
    void purgePatient_doesNotDeleteStoredFilesWhenTheTransactionRollsBack() {
        storedFiles();
        TransactionSynchronizationManager.initSynchronization();

        purger.purgePatient(patientId);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(queued).isEmpty();
        verify(storage, never()).delete(anyString());
    }

    @Test
    void purgePatient_aFailedFileDeleteNeitherFailsThePurgeNorStopsTheOthers() {
        storedFiles();
        doThrow(new StorageException("boom", null)).when(storage).delete("pdf/job-1.pdf");
        TransactionSynchronizationManager.initSynchronization();

        purger.purgePatient(patientId);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        runQueued();

        verify(storage).delete("pdf/job-2.pdf");
        verify(storage).delete("review/sub-1/r1.pdf");
    }

    @Test
    void purgePatient_withoutATransactionQueuesTheSameTaskRightAway() {
        storedFiles();

        purger.purgePatient(patientId);

        assertThat(queued).hasSize(1);
        runQueued();
        verify(storage).delete("pdf/job-1.pdf");
        verify(storage).delete("review/sub-1/r1.pdf");
    }

    @Test
    void purgePatient_aRejectingExecutorDoesNotFailTheErase() {
        storedFiles();
        RecordPurger rejecting = purgerWith(task -> {
            throw new java.util.concurrent.RejectedExecutionException("full");
        });
        TransactionSynchronizationManager.initSynchronization();

        rejecting.purgePatient(patientId);

        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit)).doesNotThrowAnyException();
        verify(storage, never()).delete(anyString());
    }

    @Test
    void purgePatient_doesNotWaitForSlowStorage() throws Exception {
        storedFiles();
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.doAnswer(inv -> {
            release.await();
            return null;
        }).when(storage).delete(anyString());
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            RecordPurger async = purgerWith(pool);
            // The synchronisation is thread-bound, and assertTimeoutPreemptively runs on another thread.
            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> {
                TransactionSynchronizationManager.initSynchronization();
                try {
                    async.purgePatient(patientId);
                    TransactionSynchronizationManager.getSynchronizations()
                            .forEach(TransactionSynchronization::afterCommit);
                } finally {
                    TransactionSynchronizationManager.clearSynchronization();
                }
            });

            release.countDown();
            verify(storage, org.mockito.Mockito.timeout(3000)).delete("review/sub-1/masked-r1.pdf");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
