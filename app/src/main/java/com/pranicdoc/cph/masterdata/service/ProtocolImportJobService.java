package com.pranicdoc.cph.masterdata.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.enums.ImportJobStatus;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.util.TransactionService;
import com.pranicdoc.cph.masterdata.dto.ProtocolImportJobView;
import com.pranicdoc.cph.masterdata.model.ProtocolImportJob;
import com.pranicdoc.cph.masterdata.repository.ProtocolImportJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Submits and tracks async protocol imports (the "Imports" tab). Submission is synchronous
 * and fast — read the upload into memory, create a {@code QUEUED} job row, hand off to {@link
 * ProtocolImportAsyncRunner} — so the HTTP request never blocks on the actual parse+upsert.
 * A healer can submit several imports; each gets its own independent job row and is processed
 * on the bounded {@code protocolImportExecutor} pool (see {@code MasterDataAsyncConfig}) —
 * correctness under multiple concurrent submissions, not throughput, is the bar for now.
 *
 * <p>Jobs are scoped to the healer who submitted them — {@link #getImportJobs()} and {@link
 * #getImportJob(UUID)} never return another healer's jobs, matching this controller's other
 * self-serve-only surfaces (there is no "system" import job to union in, unlike the catalog
 * tables).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProtocolImportJobService {

    private final ProtocolImportJobRepository jobRepo;
    private final ProtocolImportAsyncRunner asyncRunner;
    private final SecurityContextUtil securityContextUtil;
    private final ObjectMapper objectMapper;
    private final TransactionService transactionService;

    @Auditable(action = "IMPORT_PROTOCOLS")
    @Transactional
    public ProtocolImportJobView submitImport(MultipartFile file) {
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read uploaded file: " + e.getMessage());
        }

        ProtocolImportJob job = ProtocolImportJob.builder()
                .healerId(securityContextUtil.getCurrentHealerId())
                .originalFilename(file.getOriginalFilename())
                .status(ImportJobStatus.QUEUED)
                .build();
        job = jobRepo.save(job);

        // The @Async runner reads this job back on its own thread/connection — if it fired
        // before this transaction commits, it wouldn't see the row yet (READ COMMITTED) and
        // would bail out as "vanished before processing started". Defer the handoff to
        // afterCommit instead — see TransactionService.
        UUID jobId = job.getId();
        String originalFilename = file.getOriginalFilename();
        transactionService.runAfterCommit(() -> asyncRunner.run(jobId, fileBytes, originalFilename));

        return toView(job);
    }

    @Transactional(readOnly = true)
    public List<ProtocolImportJobView> getImportJobs() {
        UUID healerId = securityContextUtil.getCurrentHealerId();
        return jobRepo.findByHealerIdOrderByCreatedAtDesc(healerId).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public ProtocolImportJobView getImportJob(UUID id) {
        ProtocolImportJob job = jobRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProtocolImportJob", id));
        if (!job.getHealerId().equals(securityContextUtil.getCurrentHealerId())) {
            throw new AccessDeniedException("You can only view your own import jobs");
        }
        return toView(job);
    }

    private ProtocolImportJobView toView(ProtocolImportJob job) {
        return new ProtocolImportJobView(
                job.getId(),
                job.getOriginalFilename(),
                job.getStatus(),
                job.getTotalRows(),
                job.getCreatedCount(),
                job.getUpdatedCount(),
                job.getSkippedCount(),
                readErrorsJson(job.getErrorsJson()),
                job.getFailureMessage(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getCreatedAt());
    }

    private List<String> readErrorsJson(String errorsJson) {
        if (errorsJson == null) return List.of();
        try {
            return objectMapper.readValue(errorsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize import job errors — showing none", e);
            return List.of();
        }
    }
}
