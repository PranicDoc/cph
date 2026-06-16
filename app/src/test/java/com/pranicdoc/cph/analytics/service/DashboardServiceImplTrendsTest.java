package com.pranicdoc.cph.analytics.service;

import com.pranicdoc.cph.analytics.dto.DateRangeFilter;
import com.pranicdoc.cph.analytics.dto.DashboardPatientGrowth;
import com.pranicdoc.cph.analytics.dto.DashboardSessionTrend;
import com.pranicdoc.cph.analytics.service.impl.DashboardServiceImpl;
import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.healing.service.PdfJobService;
import com.pranicdoc.cph.healing.service.impl.SessionService;
import com.pranicdoc.cph.patient.service.PatientService;
import com.pranicdoc.cph.payments.service.CreditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DashboardServiceImpl trend methods: getSessionTrends() and getPatientGrowth().
 *
 * NOTE: @Cacheable cache isolation is NOT tested here. @Cacheable requires Spring AOP proxy
 * infrastructure and cannot be verified with plain Mockito @InjectMocks.
 * @Cacheable cache isolation tested separately via integration test with @SpringBootTest or
 * manually verified in DashboardControllerTrendsTest.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTrendsTest {

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

    @InjectMocks
    private DashboardServiceImpl dashboardServiceImpl;

    private static final UUID HEALER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    // Test 1: 30d happy path -- DB returns 5 sparse entries; result has 31 entries; zero-filled
    @Test
    void getSessionTrends_30dPeriod_returnsZeroFilledList() {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(30);
        LocalDate to = today;
        when(dateRangeResolver.resolve(any())).thenReturn(new DateRangeResolver.DateRange(from, to));

        List<DashboardSessionTrend> sparse = List.of(
                new DashboardSessionTrend(from.plusDays(1), 3L),
                new DashboardSessionTrend(from.plusDays(5), 7L),
                new DashboardSessionTrend(from.plusDays(10), 2L),
                new DashboardSessionTrend(from.plusDays(20), 1L),
                new DashboardSessionTrend(from.plusDays(28), 4L)
        );
        when(sessionService.getSessionTrends(eq(HEALER_ID.toString()), eq(from), eq(to))).thenReturn(sparse);

        ApiResponse<List<DashboardSessionTrend>> response =
                dashboardServiceImpl.getSessionTrends(HEALER_ID, new DateRangeFilter(null, null, null));

        assertNotNull(response);
        assertTrue(response.success());
        List<DashboardSessionTrend> result = response.data();
        assertEquals(31, result.size(), "Expected 31 entries for 30d period");
        assertEquals(from, result.get(0).date());
        assertEquals(3L, result.get(1).count());
        assertEquals(0L, result.get(2).count());
    }

    // Test 2: 7d all zero -- DB returns empty list; result has 8 entries each with count=0
    @Test
    void getSessionTrends_7dAllZero_returnsEightZeroEntries() {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(7);
        LocalDate to = today;
        when(dateRangeResolver.resolve(any())).thenReturn(new DateRangeResolver.DateRange(from, to));
        when(sessionService.getSessionTrends(eq(HEALER_ID.toString()), eq(from), eq(to))).thenReturn(List.of());

        ApiResponse<List<DashboardSessionTrend>> response =
                dashboardServiceImpl.getSessionTrends(HEALER_ID, new DateRangeFilter("7d", null, null));

        assertNotNull(response);
        List<DashboardSessionTrend> result = response.data();
        assertEquals(8, result.size(), "Expected 8 entries for 7d period");
        result.forEach(t -> assertEquals(0L, t.count(), "All counts should be 0"));
    }

    // Test 3: invalid period -- DateRangeResolver throws IllegalArgumentException; exception propagates
    @Test
    void getSessionTrends_invalidPeriod_throwsIllegalArgumentException() {
        when(dateRangeResolver.resolve(any()))
                .thenThrow(new IllegalArgumentException("Invalid period"));

        assertThrows(IllegalArgumentException.class, () ->
                dashboardServiceImpl.getSessionTrends(HEALER_ID, new DateRangeFilter("invalid", null, null)));
    }

    // Test 4: custom date range -- startDate + endDate provided; result covers exactly that window
    @Test
    void getSessionTrends_customDateRange_returnsEntriesForWindow() {
        LocalDate from = LocalDate.of(2026, 5, 1);
        LocalDate to = LocalDate.of(2026, 5, 3);
        when(dateRangeResolver.resolve(any())).thenReturn(new DateRangeResolver.DateRange(from, to));
        when(sessionService.getSessionTrends(eq(HEALER_ID.toString()), eq(from), eq(to)))
                .thenReturn(List.of(new DashboardSessionTrend(LocalDate.of(2026, 5, 2), 5L)));

        ApiResponse<List<DashboardSessionTrend>> response = dashboardServiceImpl.getSessionTrends(
                HEALER_ID, new DateRangeFilter(null, from, to));

        assertNotNull(response);
        List<DashboardSessionTrend> result = response.data();
        assertEquals(3, result.size(), "Custom range 2026-05-01 to 2026-05-03 => 3 entries");
        assertEquals(0L, result.get(0).count());
        assertEquals(5L, result.get(1).count());
        assertEquals(0L, result.get(2).count());
    }

    // Test 5: 7d happy path -- DB returns 2 entries; result has 8 entries; 2 with counts, rest 0
    @Test
    void getPatientGrowth_7dPeriod_returnsZeroFilledList() {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(7);
        LocalDate to = today;
        when(dateRangeResolver.resolve(any())).thenReturn(new DateRangeResolver.DateRange(from, to));

        List<DashboardPatientGrowth> sparse = List.of(
                new DashboardPatientGrowth(from.plusDays(2), 4L),
                new DashboardPatientGrowth(from.plusDays(5), 2L)
        );
        when(patientService.getPatientGrowth(eq(HEALER_ID), eq(from), eq(to))).thenReturn(sparse);

        ApiResponse<List<DashboardPatientGrowth>> response =
                dashboardServiceImpl.getPatientGrowth(HEALER_ID, new DateRangeFilter("7d", null, null));

        assertNotNull(response);
        List<DashboardPatientGrowth> result = response.data();
        assertEquals(8, result.size(), "Expected 8 entries for 7d period");
        assertEquals(4L, result.get(2).count());
        assertEquals(2L, result.get(5).count());
        assertEquals(0L, result.get(0).count());
        assertEquals(0L, result.get(1).count());
        assertEquals(0L, result.get(3).count());
    }

    // Test 6: 30d all zero -- DB returns empty list; result has 31 entries each with count=0
    @Test
    void getPatientGrowth_30dAllZero_returnsZeroFilledList() {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(30);
        LocalDate to = today;
        when(dateRangeResolver.resolve(any())).thenReturn(new DateRangeResolver.DateRange(from, to));
        when(patientService.getPatientGrowth(eq(HEALER_ID), eq(from), eq(to))).thenReturn(List.of());

        ApiResponse<List<DashboardPatientGrowth>> response =
                dashboardServiceImpl.getPatientGrowth(HEALER_ID, new DateRangeFilter(null, null, null));

        assertNotNull(response);
        List<DashboardPatientGrowth> result = response.data();
        assertEquals(31, result.size(), "Expected 31 entries for 30d period");
        result.forEach(g -> assertEquals(0L, g.count(), "All counts should be 0"));
    }
}
