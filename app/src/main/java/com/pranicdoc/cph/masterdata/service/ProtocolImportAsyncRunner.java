package com.pranicdoc.cph.masterdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pranicdoc.cph.common.enums.ImportJobStatus;
import com.pranicdoc.cph.masterdata.dto.ProtocolImportResult;
import com.pranicdoc.cph.masterdata.model.ProtocolImportJob;
import com.pranicdoc.cph.masterdata.repository.ProtocolImportJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The actual background execution for a submitted protocol import — a separate bean from
 * {@link ProtocolImportJobService} purely so {@code @Async} takes effect: Spring's @Async
 * proxy only intercepts calls that arrive from a DIFFERENT bean, not self-invocation within
 * the same class (see {@code WhatsappDispatchService}/{@code NotificationScheduler} for the
 * identical existing precedent this mirrors). Runs on the {@code protocolImportExecutor}
 * pool (see {@link MasterDataAsyncConfig}), bounded so a burst of submitted imports queues
 * rather than spawning unbounded threads.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProtocolImportAsyncRunner {

    private final ProtocolImportJobRepository jobRepo;
    private final ProtocolImportService importService;
    private final ObjectMapper objectMapper;

    @Async("protocolImportExecutor")
    @Transactional
    public void run(UUID jobId, byte[] fileBytes, String filename) {
        ProtocolImportJob job = jobRepo.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("Protocol import job {} vanished before processing started", jobId);
            return;
        }

        job.setStatus(ImportJobStatus.PROCESSING);
        job.setStartedAt(Instant.now());
        jobRepo.save(job);

        try {
            ProtocolImportResult result = importService.importFile(filename, new ByteArrayInputStream(fileBytes));
            job.setStatus(ImportJobStatus.COMPLETED);
            job.setTotalRows(result.totalRows());
            job.setCreatedCount(result.created());
            job.setUpdatedCount(result.updated());
            job.setSkippedCount(result.skipped());
            job.setErrorsJson(writeErrorsJson(result.errors()));
        } catch (Exception e) {
            log.warn("Protocol import job {} failed", jobId, e);
            job.setStatus(ImportJobStatus.FAILED);
            job.setFailureMessage(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }

        job.setCompletedAt(Instant.now());
        jobRepo.save(job);
    }

    private String writeErrorsJson(List<String> errors) {
        try {
            return objectMapper.writeValueAsString(errors);
        } catch (Exception e) {
            log.warn("Failed to serialize import job errors — dropping them", e);
            return "[]";
        }
    }
}
