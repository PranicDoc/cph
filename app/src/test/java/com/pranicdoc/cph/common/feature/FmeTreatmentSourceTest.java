package com.pranicdoc.cph.common.feature;

import io.split.client.SplitClientConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class FmeTreatmentSourceTest {

    private FmeTreatmentSource source;
    private Path file;

    @AfterEach
    void tearDown() {
        if (source != null) {
            source.destroy();
        }
        if (file != null) {
            // Best effort: the SDK's file watcher can keep the file locked on Windows.
            file.toFile().delete();
        }
    }

    private FmeTreatmentSource localhost(String flagValue, long timeoutMs) throws Exception {
        file = Files.createTempFile("fme-split", ".yaml");
        Files.writeString(file, "- case_review_enabled:\n    treatment: \"" + flagValue + "\"\n");
        String path = file.toString();
        return new FmeTreatmentSource("localhost", timeoutMs, ms -> SplitClientConfig.builder()
                .splitFile(path)
                .setBlockUntilReadyTimeout(ms)
                .build());
    }

    @Test
    void returnsTheLocalTreatment() throws Exception {
        source = localhost("on", 10_000);
        assertThat(source.treatment("cph", "case_review_enabled")).isEqualTo("on");
    }

    @Test
    void returnsOffWhenFlagIsOff() throws Exception {
        source = localhost("off", 10_000);
        assertThat(source.treatment("cph", "case_review_enabled")).isEqualTo("off");
    }

    @Test
    void unknownFlagIsControl() throws Exception {
        source = localhost("on", 10_000);
        assertThat(source.treatment("cph", "no_such_flag")).isEqualTo("control");
    }

    @Test
    void neverBlocksPastTheTimeout() {
        // Unreachable endpoints and a tiny timeout: the SDK can never become ready.
        assertThatCode(() -> source = unreachable(50)).doesNotThrowAnyException();
        assertThat(source.treatment("cph", "case_review_enabled")).isEqualTo("control");
    }

    private static FmeTreatmentSource unreachable(long timeoutMs) {
        return new FmeTreatmentSource("not-a-real-key", timeoutMs, ms -> SplitClientConfig.builder()
                .endpoint("http://127.0.0.1:1", "http://127.0.0.1:1")
                .setBlockUntilReadyTimeout(ms)
                .build());
    }

    @Test
    void unbuildableConfigDoesNotFailStartup() {
        assertThatCode(() -> source = new FmeTreatmentSource("k", 50, ms -> {
            throw new IllegalArgumentException("bad config");
        })).doesNotThrowAnyException();
        assertThat(source.treatment("cph", "case_review_enabled")).isEqualTo("control");
        assertThatCode(() -> source.destroy()).doesNotThrowAnyException();
    }

    @Test
    void zeroAndNegativeTimeoutsDoNotThrow() throws Exception {
        // Localhost mode: no network threads, so nothing lingers in the shared test JVM.
        assertThatCode(() -> source = localhost("on", 0)).doesNotThrowAnyException();
        source.destroy();
        assertThatCode(() -> source = localhost("on", -5)).doesNotThrowAnyException();
    }

    @Test
    void nonNumericTimeoutFallsBack() {
        assertThat(FmeTreatmentSource.parseTimeout("abc")).isEqualTo(3000);
        assertThat(FmeTreatmentSource.parseTimeout("")).isEqualTo(3000);
        assertThat(FmeTreatmentSource.parseTimeout(null)).isEqualTo(3000);
        assertThat(FmeTreatmentSource.parseTimeout(" 250 ")).isEqualTo(250);
    }
}
