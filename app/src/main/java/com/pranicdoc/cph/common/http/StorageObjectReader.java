package com.pranicdoc.cph.common.http;

import com.pranicdoc.storage.service.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * The one place that reads a stored object's bytes back: it asks storage for a presigned download
 * URL and fetches it over a shared {@link HttpClient}. Any failure is a {@link StorageReadException}
 * so each caller can map it to its own error contract.
 */
@Component
public class StorageObjectReader {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final StorageService storage;
    private final HttpClient http;

    // Hand-written: a second constructor lets a test supply the HttpClient.
    @Autowired
    public StorageObjectReader(StorageService storage) {
        this(storage, HttpClient.newHttpClient());
    }

    StorageObjectReader(StorageService storage, HttpClient http) {
        this.storage = storage;
        this.http = http;
    }

    public byte[] read(String storageKey) {
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(storage.generatePresignedDownloadUrl(storageKey).toString()))
                    .timeout(TIMEOUT).GET().build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw StorageReadException.notOk(response.statusCode());
            }
            return response.body();
        } catch (IOException e) {
            throw StorageReadException.io(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw StorageReadException.interrupted(e);
        }
    }
}
