package com.pranicdoc.cph.patient.service;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrashTimestampsTest {

    private final Instant at = Instant.parse("2026-11-01T10:15:30Z");

    @Test
    void everyDriverTypeBecomesTheSameInstant() {
        assertThat(TrashTimestamps.toInstant(at)).isEqualTo(at);
        assertThat(TrashTimestamps.toInstant(Timestamp.from(at))).isEqualTo(at);
        assertThat(TrashTimestamps.toInstant(OffsetDateTime.ofInstant(at, ZoneOffset.ofHours(5)))).isEqualTo(at);
        assertThat(TrashTimestamps.toInstant(ZonedDateTime.ofInstant(at, ZoneOffset.UTC))).isEqualTo(at);
        assertThat(TrashTimestamps.toInstant(LocalDateTime.ofInstant(at, ZoneOffset.UTC))).isEqualTo(at);
        assertThat(TrashTimestamps.toInstant(java.util.Date.from(at))).isEqualTo(at);
    }

    @Test
    void anUnknownTypeFailsWithItsName() {
        assertThatThrownBy(() -> TrashTimestamps.toInstant("2026-11-01"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("java.lang.String");
    }
}
