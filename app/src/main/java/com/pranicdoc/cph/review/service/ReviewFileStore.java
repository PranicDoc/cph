package com.pranicdoc.cph.review.service;

import com.pranicdoc.storage.service.StorageService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * Copies a PDF to a stable per-revision key. A regenerated PDF reuses its pdf_job row and
 * overwrites {@code pdf/{jobId}.pdf}, so a revision must not point at the job key.
 */
@Component
public class ReviewFileStore {

    private static final String PDF = "application/pdf";

    private final StorageService storage;

    public ReviewFileStore(StorageService storage) {
        this.storage = storage;
    }

    /** Copies {@code sourceFileUrl} to {@code review/{submissionId}/r{revisionNo}.pdf} and returns that key. */
    public String pin(UUID submissionId, int revisionNo, String sourceFileUrl) {
        String key = "review/" + submissionId + "/r" + revisionNo + ".pdf";
        storage.upload(key, fetch(sourceFileUrl), PDF);
        return key;
    }

    public String presignedUrl(String key) {
        return storage.generatePresignedDownloadUrl(key).toString();
    }

    /** Reads a stored object through a presigned URL, the same way CaseService.downloadFile does. */
    public byte[] fetch(String fileUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(storage.generatePresignedDownloadUrl(fileUrl).toString()))
                    .timeout(Duration.ofSeconds(30)).GET().build();
            HttpResponse<byte[]> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new ReviewStorageException("Could not read the PDF to submit (storage returned "
                        + response.statusCode() + ")");
            }
            return response.body();
        } catch (IOException e) {
            throw new ReviewStorageException("Could not read the PDF from storage", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReviewStorageException("Interrupted while reading the PDF from storage", e);
        }
    }
}
