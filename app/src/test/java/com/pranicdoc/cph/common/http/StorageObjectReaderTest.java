package com.pranicdoc.cph.common.http;

import com.pranicdoc.storage.service.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageObjectReaderTest {

    private final StorageService storage = mock(StorageService.class);
    private final HttpClient http = mock(HttpClient.class);
    private final StorageObjectReader reader = new StorageObjectReader(storage, http);

    @BeforeEach
    void stubPresignedUrl() throws Exception {
        when(storage.generatePresignedDownloadUrl("pdf/a.pdf"))
                .thenReturn(new URL("https://storage.example.test/pdf/a.pdf?sig=1"));
    }

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<byte[]> response(int status, byte[] body) {
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    @Test
    @SuppressWarnings("unchecked")
    void read_returnsTheBytesFromThePresignedUrl() throws Exception {
        byte[] pdf = {1, 2, 3};
        HttpResponse<byte[]> ok = response(200, pdf);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(ok);

        assertThat(reader.read("pdf/a.pdf")).isEqualTo(pdf);

        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().uri()).isEqualTo(URI.create("https://storage.example.test/pdf/a.pdf?sig=1"));
        assertThat(request.getValue().method()).isEqualTo("GET");
    }

    @Test
    @SuppressWarnings("unchecked")
    void read_aNon200Response_isReportedWithItsStatus() throws Exception {
        HttpResponse<byte[]> missing = response(404, new byte[0]);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(missing);

        assertThatThrownBy(() -> reader.read("pdf/a.pdf"))
                .isInstanceOfSatisfying(StorageReadException.class, e -> {
                    assertThat(e.reason()).isEqualTo(StorageReadException.Reason.NOT_OK);
                    assertThat(e.statusCode()).isEqualTo(404);
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void read_anIoError_isReportedWithTheCause() throws Exception {
        IOException boom = new IOException("connection reset");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(boom);

        assertThatThrownBy(() -> reader.read("pdf/a.pdf"))
                .isInstanceOfSatisfying(StorageReadException.class, e -> {
                    assertThat(e.reason()).isEqualTo(StorageReadException.Reason.IO);
                    assertThat(e).hasCause(boom);
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void read_whenInterrupted_isReportedAndTheInterruptFlagIsKept() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("stop"));

        assertThatThrownBy(() -> reader.read("pdf/a.pdf"))
                .isInstanceOfSatisfying(StorageReadException.class,
                        e -> assertThat(e.reason()).isEqualTo(StorageReadException.Reason.INTERRUPTED));
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }
}
