package com.pranicdoc.cph.analytics.service;

import com.pranicdoc.cph.analytics.dto.DashboardCreditUsage;
import com.pranicdoc.cph.analytics.dto.DateRangeFilter;
import com.pranicdoc.cph.analytics.service.impl.DashboardServiceImpl;
import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.healing.service.PdfJobService;
import com.pranicdoc.cph.healing.service.impl.CaseService;
import com.pranicdoc.cph.healing.service.impl.SessionService;
import com.pranicdoc.cph.patient.service.PatientService;
import com.pranicdoc.cph.payments.dto.CphTransactionResponse;
import com.pranicdoc.cph.payments.dto.OwnerType;
import com.pranicdoc.cph.payments.dto.PagedTransactionResponse;
import com.pranicdoc.cph.payments.service.CreditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DashboardServiceImpl.getCreditUsage().
 * Tests: happy path grouping, DEDUCT-only filter, zero-fill, graceful degradation.
 * NOTE: @Cacheable AOP is NOT active here -- @InjectMocks bypasses Spring proxy infrastructure.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceImplCreditUsageTest {

    @Mock
    private PatientService patientService;

    @Mock
    private SessionService sessionService;

    @Mock
    private CreditService creditService;

    @Mock
    private PdfJobService pdfJobService;

    @Mock
    private DateRangeResolver dateRangeResolver;

    @Mock
    private CaseService caseService;

    @InjectMocks
    private DashboardServiceImpl dashboardServiceImpl;

    private static final UUID HEALER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    // Helper: LocalDate -> Instant at UTC start-of-day
    private static Instant toUtcInstant(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    @Test
    void getCreditUsage_deductTransactionsInRange_returnsGroupedByDate() {
        LocalDate today = LocalDate.now();
        DateRangeFilter filter = new DateRangeFilter("7d", null, null);
        LocalDate from = today.minusDays(6);
        LocalDate to = today;
        when(dateRangeResolver.resolve(filter))
                .thenReturn(new DateRangeResolver.DateRange(from, to));

        // Two DEDUCT transactions on the same day, one on another day
        CphTransactionResponse t1 = new CphTransactionResponse(
                UUID.randomUUID(), "DEDUCT", 10, 90, null, null, toUtcInstant(from));
        CphTransactionResponse t2 = new CphTransactionResponse(
                UUID.randomUUID(), "DEDUCT", 5, 85, null, null, toUtcInstant(from));
        CphTransactionResponse t3 = new CphTransactionResponse(
                UUID.randomUUID(), "DEDUCT", 3, 82, null, null, toUtcInstant(from.plusDays(1)));
        PagedTransactionResponse paged = new PagedTransactionResponse(
                List.of(t1, t2, t3), 1, 3, 0, 500, true);
        when(creditService.getTransactions(OwnerType.HEALER, HEALER_ID, 0, 500))
                .thenReturn(paged);

        ApiResponse<List<DashboardCreditUsage>> result =
                dashboardServiceImpl.getCreditUsage(HEALER_ID, filter);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).hasSize(7); // from minusDays(6) to today = 7 entries (zero-filled)
        // Day "from": t1 + t2 = 15
        DashboardCreditUsage firstDay = result.data().stream()
                .filter(d -> d.date().equals(from)).findFirst().orElseThrow();
        assertThat(firstDay.creditsConsumed()).isEqualTo(15L);
        // Day "from+1": t3 = 3
        DashboardCreditUsage secondDay = result.data().stream()
                .filter(d -> d.date().equals(from.plusDays(1))).findFirst().orElseThrow();
        assertThat(secondDay.creditsConsumed()).isEqualTo(3L);
        verify(creditService).getTransactions(OwnerType.HEALER, HEALER_ID, 0, 500);
    }

    @Test
    void getCreditUsage_mixedTransactionTypes_onlyDeductCounted() {
        LocalDate today = LocalDate.now();
        DateRangeFilter filter = new DateRangeFilter("7d", null, null);
        LocalDate from = today.minusDays(6);
        LocalDate to = today;
        when(dateRangeResolver.resolve(filter))
                .thenReturn(new DateRangeResolver.DateRange(from, to));

        CphTransactionResponse deduct = new CphTransactionResponse(
                UUID.randomUUID(), "DEDUCT", 20, 80, null, null, toUtcInstant(from));
        CphTransactionResponse credit = new CphTransactionResponse(
                UUID.randomUUID(), "CREDIT", 50, 130, null, null, toUtcInstant(from));
        PagedTransactionResponse paged = new PagedTransactionResponse(
                List.of(deduct, credit), 1, 2, 0, 500, true);
        when(creditService.getTransactions(OwnerType.HEALER, HEALER_ID, 0, 500))
                .thenReturn(paged);

        ApiResponse<List<DashboardCreditUsage>> result =
                dashboardServiceImpl.getCreditUsage(HEALER_ID, filter);

        assertThat(result.success()).isTrue();
        DashboardCreditUsage fromDay = result.data().stream()
                .filter(d -> d.date().equals(from)).findFirst().orElseThrow();
        assertThat(fromDay.creditsConsumed()).isEqualTo(20L); // CREDIT not counted
    }

    @Test
    void getCreditUsage_noTransactionsInRange_returnsZeroFilledList() {
        LocalDate today = LocalDate.now();
        DateRangeFilter filter = new DateRangeFilter("7d", null, null);
        LocalDate from = today.minusDays(6);
        LocalDate to = today;
        when(dateRangeResolver.resolve(filter))
                .thenReturn(new DateRangeResolver.DateRange(from, to));
        when(creditService.getTransactions(OwnerType.HEALER, HEALER_ID, 0, 500))
                .thenReturn(new PagedTransactionResponse(List.of(), 0, 0, 0, 500, true));

        ApiResponse<List<DashboardCreditUsage>> result =
                dashboardServiceImpl.getCreditUsage(HEALER_ID, filter);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).hasSize(7);
        assertThat(result.data()).allMatch(d -> d.creditsConsumed() == 0L);
    }

    @Test
    void getCreditUsage_creditServiceUnavailable_returnsZeroFilledGracefully() {
        LocalDate today = LocalDate.now();
        DateRangeFilter filter = new DateRangeFilter("7d", null, null);
        LocalDate from = today.minusDays(6);
        LocalDate to = today;
        when(dateRangeResolver.resolve(filter))
                .thenReturn(new DateRangeResolver.DateRange(from, to));
        when(creditService.getTransactions(OwnerType.HEALER, HEALER_ID, 0, 500))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "down"));

        ApiResponse<List<DashboardCreditUsage>> result =
                dashboardServiceImpl.getCreditUsage(HEALER_ID, filter);

        assertThat(result.success()).isTrue(); // no exception thrown
        assertThat(result.data()).hasSize(7);
        assertThat(result.data()).allMatch(d -> d.creditsConsumed() == 0L);
        verify(creditService).getTransactions(OwnerType.HEALER, HEALER_ID, 0, 500);
    }
}
