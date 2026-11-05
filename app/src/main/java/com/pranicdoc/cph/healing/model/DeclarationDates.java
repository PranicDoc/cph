package com.pranicdoc.cph.healing.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * A declaration is signed on a calendar date, but {@code declaration.signed_at} is an instant. The
 * date is stored at noon India time and read back in the same zone, so it cannot shift by a day.
 */
public final class DeclarationDates {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final LocalTime NOON = LocalTime.NOON;

    private DeclarationDates() {
    }

    /** The instant to store for a chosen date. A date after today is refused. */
    public static Instant toSignedAt(LocalDate date) {
        if (date.isAfter(LocalDate.now(ZONE))) {
            throw new IllegalArgumentException("The declaration date cannot be in the future");
        }
        return date.atTime(NOON).atZone(ZONE).toInstant();
    }

    /** The calendar date of a stored instant; null when none was stored. */
    public static LocalDate toDate(Instant signedAt) {
        return signedAt == null ? null : signedAt.atZone(ZONE).toLocalDate();
    }

    /**
     * Reads the {@code signedAt} a client sent: a plain date ({@code 2026-11-02}), or a full timestamp
     * as older clients send. Nothing sent means it is being signed now.
     */
    public static Instant parseSignedAt(String signedAt) {
        if (signedAt == null || signedAt.isBlank()) {
            return Instant.now();
        }
        String value = signedAt.strip();
        try {
            return toSignedAt(LocalDate.parse(value));
        } catch (DateTimeParseException notAPlainDate) {
            return parseTimestamp(value);
        }
    }

    private static Instant parseTimestamp(String value) {
        Instant instant;
        try {
            instant = Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("The declaration date is not a valid date");
        }
        if (toDate(instant).isAfter(LocalDate.now(ZONE))) {
            throw new IllegalArgumentException("The declaration date cannot be in the future");
        }
        return instant;
    }
}
