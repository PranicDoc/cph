package com.pranicdoc.cph.healing.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeclarationDatesTest {

    private static final LocalDate TODAY_IN_INDIA = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    @Test
    void aChosenDateIsStoredAtNoonIndiaTime() {
        assertThat(DeclarationDates.toSignedAt(LocalDate.of(2026, 11, 2)))
                .isEqualTo(Instant.parse("2026-11-02T06:30:00Z"));
    }

    @Test
    void aStoredDateReadsBackAsTheSameDate() {
        LocalDate date = LocalDate.of(2026, 3, 1);
        assertThat(DeclarationDates.toDate(DeclarationDates.toSignedAt(date))).isEqualTo(date);
    }

    @Test
    void anInstantIsReadInIndiaTimeSoLateUtcEveningsAreTheNextDay() {
        assertThat(DeclarationDates.toDate(Instant.parse("2026-11-02T18:29:59Z"))).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(DeclarationDates.toDate(Instant.parse("2026-11-02T18:30:00Z"))).isEqualTo(LocalDate.of(2026, 11, 3));
    }

    @Test
    void noStoredInstantHasNoDate() {
        assertThat(DeclarationDates.toDate(null)).isNull();
    }

    @Test
    void todayIsAllowedAndTomorrowIsRefused() {
        assertThat(DeclarationDates.toDate(DeclarationDates.toSignedAt(TODAY_IN_INDIA))).isEqualTo(TODAY_IN_INDIA);
        assertThatThrownBy(() -> DeclarationDates.toSignedAt(TODAY_IN_INDIA.plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("future");
    }

    @Test
    void parsesAPlainDateAndAnOlderClientsTimestamp() {
        assertThat(DeclarationDates.parseSignedAt("2026-11-02")).isEqualTo(Instant.parse("2026-11-02T06:30:00Z"));
        assertThat(DeclarationDates.parseSignedAt("2026-11-02T10:15:30.000Z"))
                .isEqualTo(Instant.parse("2026-11-02T10:15:30Z"));
    }

    @Test
    void nothingSentMeansSignedNow() {
        Instant before = Instant.now();
        assertThat(DeclarationDates.parseSignedAt(null)).isBetween(before, Instant.now());
        assertThat(DeclarationDates.parseSignedAt("  ")).isAfterOrEqualTo(before);
    }

    @Test
    void refusesAFutureDateInEitherFormAndGarbage() {
        String tomorrow = TODAY_IN_INDIA.plusDays(1).toString();
        assertThatThrownBy(() -> DeclarationDates.parseSignedAt(tomorrow))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeclarationDates.parseSignedAt(tomorrow + "T12:00:00Z"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeclarationDates.parseSignedAt("not-a-date"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a valid date");
    }
}
