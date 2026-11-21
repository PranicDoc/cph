package com.pranicdoc.cph.review.service;

import com.pranicdoc.cph.common.http.StorageObjectReader;
import com.pranicdoc.cph.common.http.StorageReadException;
import com.pranicdoc.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Copies a PDF to a stable per-revision key. A regenerated PDF reuses its pdf_job row and
 * overwrites {@code pdf/{jobId}.pdf}, so a revision must not point at the job key.
 */
@Component
@RequiredArgsConstructor
public class ReviewFileStore {

    private static final String PDF = "application/pdf";

    private final StorageService storage;
    private final StorageObjectReader reader;

    /** Copies {@code sourceFileUrl} to {@code review/{submissionId}/r{revisionNo}.pdf} and returns that key. */
    public String pin(UUID submissionId, int revisionNo, String sourceFileUrl) {
        String key = "review/" + submissionId + "/r" + revisionNo + ".pdf";
        storage.upload(key, fetch(sourceFileUrl), PDF);
        return key;
    }

    public String presignedUrl(String key) {
        return storage.generatePresignedDownloadUrl(key).toString();
    }

    /** Reads a stored object through the shared storage reader, as a review-flavoured failure. */
    public byte[] fetch(String fileUrl) {
        try {
            return reader.read(fileUrl);
        } catch (StorageReadException e) {
            switch (e.reason()) {
                case NOT_OK:
                    throw new ReviewStorageException("Could not read the PDF to submit (storage returned "
                            + e.statusCode() + ")");
                case INTERRUPTED:
                    throw new ReviewStorageException("Interrupted while reading the PDF from storage", e.getCause());
                default:
                    throw new ReviewStorageException("Could not read the PDF from storage", e.getCause());
            }
        }
    }
}
