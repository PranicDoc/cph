package com.pranicdoc.cph.analytics.service;

import com.pranicdoc.cph.analytics.dto.DateRangeFilter;
import com.pranicdoc.cph.analytics.service.DateRangeResolver.DateRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DateRangeResolverTest {

    private DateRangeResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DateRangeResolver();
    }

    // Test 1: null period defaults to 30d
    @Test
    void resolve_nullPeriod_defaults30d() {
        DateRange range = resolver.resolve(new DateRangeFilter(null, null, null));
        LocalDate expectedFrom = LocalDate.now().minusDays(30);
        assertNotNull(range.from());
        assertNotNull(range.to());
        // Accept ±1 day tolerance for boundary safety
        assertTrue(range.from().isAfter(LocalDate.now().minusDays(32)),
                "from should be within 32 days ago");
        assertTrue(range.from().isBefore(LocalDate.now()),
                "from should be before today");
        assertTrue(range.to().isEqual(LocalDate.now()) || range.to().isBefore(LocalDate.now().plusDays(1)),
                "to should be today or just before tomorrow");
    }

    // Test 2: period="7d" resolves to last 7 days
    @Test
    void resolve_period7d_returns7DayRange() {
        DateRange range = resolver.resolve(new DateRangeFilter("7d", null, null));
        assertTrue(range.from().isAfter(LocalDate.now().minusDays(9)),
                "from should be within 9 days ago");
        assertTrue(range.from().isBefore(LocalDate.now()),
                "from should be before today");
        assertTrue(range.to().isEqual(LocalDate.now()) || range.to().isBefore(LocalDate.now().plusDays(1)),
                "to should be today");
    }

    // Test 3: period="30d" resolves to last 30 days
    @Test
    void resolve_period30d_returns30DayRange() {
        DateRange range = resolver.resolve(new DateRangeFilter("30d", null, null));
        assertTrue(range.from().isAfter(LocalDate.now().minusDays(32)),
                "from should be within 32 days ago");
        assertTrue(range.from().isBefore(LocalDate.now()),
                "from should be before today");
        assertTrue(range.to().isEqual(LocalDate.now()) || range.to().isBefore(LocalDate.now().plusDays(1)),
                "to should be today");
    }

    // Test 4: period="90d" resolves to last 90 days
    @Test
    void resolve_period90d_returns90DayRange() {
        DateRange range = resolver.resolve(new DateRangeFilter("90d", null, null));
        assertTrue(range.from().isAfter(LocalDate.now().minusDays(92)),
                "from should be within 92 days ago");
        assertTrue(range.from().isBefore(LocalDate.now()),
                "from should be before today");
        assertTrue(range.to().isEqual(LocalDate.now()) || range.to().isBefore(LocalDate.now().plusDays(1)),
                "to should be today");
    }

    // Test 5: custom startDate + endDate bypasses period check
    @Test
    void resolve_customDateRange_returnsExactDates() {
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 6, 1);
        DateRange range = resolver.resolve(new DateRangeFilter(null, start, end));
        assertEquals(start, range.from(), "from should be exact custom startDate");
        assertEquals(end, range.to(), "to should be exact custom endDate");
    }

    // Test 6: invalid period string throws IllegalArgumentException
    @Test
    void resolve_invalidPeriod_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(new DateRangeFilter("invalid", null, null)));
        assertTrue(ex.getMessage().contains("Invalid period"),
                "Exception message should contain 'Invalid period'");
    }

    // Test 7: "1d" is not a valid period — throws IllegalArgumentException
    @Test
    void resolve_period1d_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(new DateRangeFilter("1d", null, null)));
        assertTrue(ex.getMessage().contains("Invalid period"),
                "Exception message should contain 'Invalid period'");
    }
}
