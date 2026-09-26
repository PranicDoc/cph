package com.pranicdoc.cph.masterdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pranicdoc.cph.common.enums.ImportJobStatus;
import com.pranicdoc.cph.masterdata.dto.ProtocolImportResult;
import com.pranicdoc.cph.masterdata.model.ProtocolImportJob;
import com.pranicdoc.cph.masterdata.repository.ProtocolImportJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link ProtocolImportAsyncRunner#run}, called directly (not through the
 * real {@code @Async} proxy — that's Spring framework behavior, not this class's logic). Only
 * the status-transition/result-recording bookkeeping is under test; row-processing correctness
 * is {@code ProtocolImportServiceTest}'s job.
 */
@ExtendWith(MockitoExtension.class)
class ProtocolImportAsyncRunnerTest {

    @Mock private ProtocolImportJobRepository jobRepo;
    @Mock private ProtocolImportService importService;

    private ProtocolImportAsyncRunner runner;

    @BeforeEach
    void setUp() {
        runner = new ProtocolImportAsyncRunner(jobRepo, importService, new ObjectMapper());
    }

    @Test
    void successfulImport_movesQueuedToProcessingToCompletedWithResults() throws Exception {
        UUID jobId = UUID.randomUUID();
        ProtocolImportJob job = ProtocolImportJob.builder().healerId(UUID.randomUUID()).status(ImportJobStatus.QUEUED).build();
        job.setId(jobId);
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(importService.importFile(anyString(), any()))
                .thenReturn(new ProtocolImportResult(10, 6, 2, 2, List.of("Row 3: bad Page")));

        runner.run(jobId, "a,b\n1,2".getBytes(), "protocols.csv");

        ArgumentCaptor<ProtocolImportJob> savedCaptor = ArgumentCaptor.forClass(ProtocolImportJob.class);
        verify(jobRepo, times(2)).save(savedCaptor.capture());

        ProtocolImportJob finalState = savedCaptor.getAllValues().get(1);
        assertEquals(ImportJobStatus.COMPLETED, finalState.getStatus());
        assertEquals(10, finalState.getTotalRows());
        assertEquals(6, finalState.getCreatedCount());
        assertEquals(2, finalState.getUpdatedCount());
        assertEquals(2, finalState.getSkippedCount());
        assertNotNull(finalState.getStartedAt());
        assertNotNull(finalState.getCompletedAt());
        assertEquals("[\"Row 3: bad Page\"]", finalState.getErrorsJson());
    }

    @Test
    void exceptionDuringProcessing_marksJobFailedWithTheExceptionMessage() throws Exception {
        UUID jobId = UUID.randomUUID();
        ProtocolImportJob job = ProtocolImportJob.builder().healerId(UUID.randomUUID()).status(ImportJobStatus.QUEUED).build();
        job.setId(jobId);
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(importService.importFile(anyString(), any()))
                .thenThrow(new IllegalArgumentException("Sheet 'Protocols' not found"));

        runner.run(jobId, "junk".getBytes(), "bad.xlsx");

        ArgumentCaptor<ProtocolImportJob> savedCaptor = ArgumentCaptor.forClass(ProtocolImportJob.class);
        verify(jobRepo, times(2)).save(savedCaptor.capture());

        ProtocolImportJob finalState = savedCaptor.getAllValues().get(1);
        assertEquals(ImportJobStatus.FAILED, finalState.getStatus());
        assertEquals("Sheet 'Protocols' not found", finalState.getFailureMessage());
        assertNotNull(finalState.getCompletedAt());
    }

    @Test
    void jobRowGoneBeforeProcessingStarts_doesNothing() {
        UUID jobId = UUID.randomUUID();
        when(jobRepo.findById(jobId)).thenReturn(Optional.empty());

        runner.run(jobId, "x".getBytes(), "x.csv");

        verify(jobRepo, times(0)).save(any());
    }
}
