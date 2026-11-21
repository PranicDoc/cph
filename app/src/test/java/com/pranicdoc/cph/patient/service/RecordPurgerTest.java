package com.pranicdoc.cph.patient.service;

import com.pranicdoc.storage.exception.StorageException;
import com.pranicdoc.storage.service.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecordPurgerTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final StorageService storage = mock(StorageService.class);
    /** Tasks the purger hands to its executor; tests run them by hand. */
    private final List<Runnable> queued = new java.util.ArrayList<>();
    private final RecordPurger purger = new RecordPurger(jdbc, storage, queued::add);

    private List<String> erasedTables() {
        UUID id = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(1);
        purger.purgePatient(id);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, org.mockito.Mockito.atLeastOnce()).update(sql.capture(), any(Object[].class));
        return sql.getAllValues().stream()
                .filter(s -> s.startsWith("DELETE FROM"))
                .map(s -> s.substring("DELETE FROM ".length()).split(" ")[0])
                .collect(Collectors.toList());
    }

    @Test
    void everyTableThatHoldsAPatientsDataIsErased() {
        assertThat(erasedTables()).contains(
                "patient", "patient_medical_history", "healing_case", "case_closure", "case_medical_snapshot",
                "case_medication", "case_symptom", "declaration", "feedback_link", "healer_protocol_understanding",
                "healing_details", "treatment_step", "treatment_step_targets", "healing_session", "pdf_job",
                "chakra_scan", "chakra_reading", "protocol_entry", "protocol_color", "qualitative_feedback",
                "symptom_feedback");
    }

    @Test
    void childRowsAreErasedBeforeTheRowTheyPointAt() {
        List<String> order = erasedTables();
        assertThat(order.indexOf("chakra_reading")).isLessThan(order.indexOf("chakra_scan"));
        assertThat(order.indexOf("chakra_scan")).isLessThan(order.indexOf("healing_session"));
        assertThat(order.indexOf("protocol_color")).isLessThan(order.indexOf("protocol_entry"));
        assertThat(order.indexOf("treatment_step_targets")).isLessThan(order.indexOf("treatment_step"));
        assertThat(order.indexOf("healing_session")).isLessThan(order.indexOf("healing_case"));
        assertThat(order.indexOf("healing_case")).isLessThan(order.lastIndexOf("patient"));
        assertThat(order.lastIndexOf("patient")).isEqualTo(order.size() - 1);
    }

    @Test
    void historyCopiesOfTheErasedRowsAreErasedToo() {
        assertThat(erasedTables()).contains("patient_aud", "healing_case_aud", "healing_session_aud");
    }

    @Test
    void consentRecordsAndTheAuditLogAreKeptOnPurpose() {
        assertThat(erasedTables()).doesNotContain("consent", "audit_log", "reading_session");
    }

    @Test
    void reviewRowsAreErasedBeforeTheCaseTheyPointAt() {
        List<String> order = erasedTables();
        assertThat(order).contains("review_comment", "review_revision", "review_submission");
        assertThat(order.indexOf("review_comment")).isLessThan(order.indexOf("review_revision"));
        assertThat(order.indexOf("review_revision")).isLessThan(order.indexOf("review_submission"));
        assertThat(order.indexOf("review_submission")).isLessThan(order.lastIndexOf("healing_case"));
    }

    // ---- stored files -------------------------------------------------------------------------

    private final UUID patientId = UUID.randomUUID();

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void storedFiles() {
        when(jdbc.queryForList(contains("SELECT file_url FROM pdf_job"), eq(String.class), eq(patientId)))
                .thenReturn(List.of("pdf/job-1.pdf", "pdf/job-2.pdf"));
        when(jdbc.queryForList(contains("SELECT file_url FROM review_revision"), eq(String.class), eq(patientId)))
                .thenReturn(List.of("review/sub-1/r1.pdf"));
        when(jdbc.queryForList(contains("SELECT review_file_url FROM review_revision"), eq(String.class), eq(patientId)))
                .thenReturn(List.of("review/sub-1/masked-r1.pdf"));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(1);
    }

    private void runQueued() {
        queued.forEach(Runnable::run);
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
        RecordPurger rejecting = new RecordPurger(jdbc, storage, task -> {
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
            RecordPurger async = new RecordPurger(jdbc, storage, pool);
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
