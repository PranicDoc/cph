package com.pranicdoc.cph.masterdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pranicdoc.cph.common.enums.ImportJobStatus;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.util.TransactionService;
import com.pranicdoc.cph.masterdata.dto.ProtocolImportJobView;
import com.pranicdoc.cph.masterdata.model.ProtocolImportJob;
import com.pranicdoc.cph.masterdata.repository.ProtocolImportJobRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link ProtocolImportJobService} — the submit/list/get orchestration
 * around an async import. The actual parse+upsert logic is {@link ProtocolImportService},
 * already covered by {@code ProtocolImportServiceTest}; this class only asserts job-lifecycle
 * bookkeeping and per-healer ownership scoping.
 */
@ExtendWith(MockitoExtension.class)
class ProtocolImportJobServiceTest {

    @Mock private ProtocolImportJobRepository jobRepo;
    @Mock private ProtocolImportAsyncRunner asyncRunner;
    @Mock private SecurityContextUtil securityContextUtil;

    private ProtocolImportJobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new ProtocolImportJobService(
                jobRepo, asyncRunner, securityContextUtil, new ObjectMapper(), new TransactionService());
    }

    // submitImport() defers the hand-off to the async runner to afterCommit via
    // TransactionService, so the runner never reads the job row before this transaction has
    // committed it — TransactionService.runAfterCommit() throws outside an active transaction,
    // so tests that exercise submitImport() need one open, and must fire afterCommit manually
    // since no real PlatformTransactionManager is driving a commit here.
    @BeforeEach
    void initTransactionSync() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearTransactionSync() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private void fireAfterCommit() {
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
    }

    @Test
    void submitImport_createsQueuedJobAndHandsOffToTheAsyncRunner() throws Exception {
        UUID healerId = UUID.randomUUID();
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        when(jobRepo.save(any())).thenAnswer(inv -> {
            ProtocolImportJob job = inv.getArgument(0);
            job.setId(UUID.randomUUID());
            return job;
        });

        MockMultipartFile file = new MockMultipartFile("file", "protocols.csv", "text/csv", "a,b\n1,2".getBytes());
        ProtocolImportJobView view = jobService.submitImport(file);

        assertEquals(ImportJobStatus.QUEUED, view.status());
        assertEquals("protocols.csv", view.originalFilename());

        ArgumentCaptor<ProtocolImportJob> savedCaptor = ArgumentCaptor.forClass(ProtocolImportJob.class);
        verify(jobRepo).save(savedCaptor.capture());
        assertEquals(healerId, savedCaptor.getValue().getHealerId());
        assertEquals(ImportJobStatus.QUEUED, savedCaptor.getValue().getStatus());

        // Not yet — the hand-off is deferred until the transaction actually commits.
        verify(asyncRunner, never()).run(any(), any(), any());

        fireAfterCommit();

        verify(asyncRunner).run(eq(view.id()), any(byte[].class), eq("protocols.csv"));
    }

    @Test
    void getImportJobs_returnsOnlyTheCurrentHealersOwnJobs() {
        UUID healerId = UUID.randomUUID();
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        ProtocolImportJob job = ProtocolImportJob.builder()
                .healerId(healerId).status(ImportJobStatus.COMPLETED).originalFilename("x.xlsx").build();
        when(jobRepo.findByHealerIdOrderByCreatedAtDesc(healerId)).thenReturn(List.of(job));

        List<ProtocolImportJobView> result = jobService.getImportJobs();

        assertEquals(1, result.size());
        assertEquals("x.xlsx", result.get(0).originalFilename());
    }

    @Test
    void getImportJob_deniesAccessToAnotherHealersJob() {
        UUID jobId = UUID.randomUUID();
        UUID ownerHealerId = UUID.randomUUID();
        UUID callerHealerId = UUID.randomUUID();
        ProtocolImportJob job = ProtocolImportJob.builder().healerId(ownerHealerId).status(ImportJobStatus.COMPLETED).build();
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(securityContextUtil.getCurrentHealerId()).thenReturn(callerHealerId);

        assertThrows(AccessDeniedException.class, () -> jobService.getImportJob(jobId));
    }

    @Test
    void getImportJob_unknownIdThrowsNotFound() {
        UUID jobId = UUID.randomUUID();
        when(jobRepo.findById(jobId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> jobService.getImportJob(jobId));
        verify(securityContextUtil, never()).getCurrentHealerId();
    }

    @Test
    void toView_parsesErrorsJsonBackIntoAList() {
        UUID healerId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        ProtocolImportJob job = ProtocolImportJob.builder()
                .healerId(healerId).status(ImportJobStatus.COMPLETED)
                .errorsJson("[\"Row 2: missing Book\",\"Row 5: bad Page\"]")
                .build();
        job.setId(jobId);
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);

        ProtocolImportJobView view = jobService.getImportJob(jobId);

        assertEquals(2, view.errors().size());
        assertTrue(view.errors().contains("Row 2: missing Book"));
    }
}
