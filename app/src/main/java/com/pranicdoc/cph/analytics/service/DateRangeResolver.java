package com.pranicdoc.cph.analytics.service;

import com.pranicdoc.cph.analytics.dto.DateRangeFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Set;

@Component
@Slf4j
public class DateRangeResolver {

    private static final Set<String> VALID_PERIODS = Set.of("7d", "30d", "90d");

    public record DateRange(LocalDate from, LocalDate to) {}

    public DateRange resolve(DateRangeFilter filter) {
        // Custom date range bypasses period check entirely
        if (filter.startDate() != null && filter.endDate() != null) {
            return new DateRange(filter.startDate(), filter.endDate());
        }

        // Period-based range — period is guaranteed non-null by DateRangeFilter compact constructor
        String period = filter.period();
        return switch (period) {
            case "7d"  -> new DateRange(LocalDate.now().minusDays(7),  LocalDate.now());
            case "30d" -> new DateRange(LocalDate.now().minusDays(30), LocalDate.now());
            case "90d" -> new DateRange(LocalDate.now().minusDays(90), LocalDate.now());
            default    -> throw new IllegalArgumentException(
                    "Invalid period '" + period + "'. Use 7d, 30d, 90d, or provide startDate+endDate.");
        };
    }
}
