package com.pranicdoc.cph.review.service;

import com.pranicdoc.cph.common.http.StorageObjectReader;
import com.pranicdoc.cph.common.http.StorageReadException;
import com.pranicdoc.storage.service.StorageService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The review-flavoured failures on top of the shared storage reader. */
class ReviewFileStoreTest {

    private final StorageService storage = mock(StorageService.class);
    private final StorageObjectReader reader = mock(StorageObjectReader.class);
    private final ReviewFileStore store = new ReviewFileStore(storage, reader);

    @Test
    void fetch_returnsTheBytesTheReaderGot() {
        when(reader.read("pdf/a.pdf")).thenReturn(new byte[]{9});

        assertThat(store.fetch("pdf/a.pdf")).containsExactly(9);
    }

    @Test
    void fetch_aNon200_keepsTheStatusInTheMessage() {
        when(reader.read("pdf/a.pdf")).thenThrow(StorageReadException.notOk(503));

        assertThatThrownBy(() -> store.fetch("pdf/a.pdf"))
                .isInstanceOf(ReviewStorageException.class)
                .hasMessage("Could not read the PDF to submit (storage returned 503)");
    }

    @Test
    void fetch_anIoError_keepsItsCause() {
        IOException cause = new IOException("reset");
        when(reader.read("pdf/a.pdf")).thenThrow(StorageReadException.io(cause));

        assertThatThrownBy(() -> store.fetch("pdf/a.pdf"))
                .isInstanceOf(ReviewStorageException.class)
                .hasMessage("Could not read the PDF from storage")
                .hasCause(cause);
    }

    @Test
    void fetch_whenInterrupted_saysSo() {
        when(reader.read("pdf/a.pdf")).thenThrow(StorageReadException.interrupted(new InterruptedException()));

        assertThatThrownBy(() -> store.fetch("pdf/a.pdf"))
                .isInstanceOf(ReviewStorageException.class)
                .hasMessage("Interrupted while reading the PDF from storage");
    }

    @Test
    void pin_copiesTheFetchedBytesToThePerRevisionKey() {
        UUID submissionId = UUID.randomUUID();
        when(reader.read("pdf/a.pdf")).thenReturn(new byte[]{1, 2});

        String key = store.pin(submissionId, 3, "pdf/a.pdf");

        assertThat(key).isEqualTo("review/" + submissionId + "/r3.pdf");
        verify(storage).upload(eq(key), any(byte[].class), eq("application/pdf"));
    }
}
