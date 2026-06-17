package com.pranicdoc.cph.analytics.service;

import com.pranicdoc.cph.analytics.dto.DateRangeFilter;
import com.pranicdoc.cph.analytics.dto.DashboardTopProtocol;
import com.pranicdoc.cph.analytics.dto.DashboardTopSymptom;
import com.pranicdoc.cph.analytics.service.impl.DashboardServiceImpl;
import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.healing.service.PdfJobService;
import com.pranicdoc.cph.healing.service.impl.CaseService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DashboardServiceImpl insight methods: getTopSymptoms() and getTopProtocols().
 *
 * NOTE: @Cacheable AOP is NOT active here -- @InjectMocks bypasses Spring proxy infrastructure.
 * Tests verify delegation to CaseService with correct date range resolution.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceImplInsightsTest {

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
    private static final LocalDate FROM = LocalDate.of(2026, 5, 17);
    private static final LocalDate TO = LocalDate.of(2026, 6, 16);

    // Test 1: getTopSymptoms happy path
    @Test
    void getTopSymptoms_delegatesToCaseService_returnsWrappedList() {
        DateRangeFilter filter = new DateRangeFilter("30d", null, null);
        DateRangeResolver.DateRange range = new DateRangeResolver.DateRange(FROM, TO);
        when(dateRangeResolver.resolve(filter)).thenReturn(range);
        when(caseService.getTopSymptoms(HEALER_ID, FROM, TO))
                .thenReturn(List.of(new DashboardTopSymptom("Anxiety", 5L)));

        ApiResponse<List<DashboardTopSymptom>> result = dashboardServiceImpl.getTopSymptoms(HEALER_ID, filter);

        assertNotNull(result);
        assertTrue(result.success());
        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).symptom()).isEqualTo("Anxiety");
        assertThat(result.data().get(0).count()).isEqualTo(5L);
        verify(dateRangeResolver).resolve(filter);
        verify(caseService).getTopSymptoms(HEALER_ID, FROM, TO);
    }

    // Test 2: getTopSymptoms empty list
    @Test
    void getTopSymptoms_emptyList_returnsSuccessWithEmptyData() {
        DateRangeFilter filter = new DateRangeFilter("7d", null, null);
        DateRangeResolver.DateRange range = new DateRangeResolver.DateRange(FROM, TO);
        when(dateRangeResolver.resolve(filter)).thenReturn(range);
        when(caseService.getTopSymptoms(HEALER_ID, FROM, TO)).thenReturn(List.of());

        ApiResponse<List<DashboardTopSymptom>> result = dashboardServiceImpl.getTopSymptoms(HEALER_ID, filter);

        assertNotNull(result);
        assertTrue(result.success());
        assertThat(result.data()).isEmpty();
    }

    // Test 3: getTopSymptoms calls DateRangeResolver with the correct filter
    @Test
    void getTopSymptoms_callsDateRangeResolverWithCorrectFilter() {
        DateRangeFilter filter = new DateRangeFilter("90d", null, null);
        DateRangeResolver.DateRange range = new DateRangeResolver.DateRange(FROM, TO);
        when(dateRangeResolver.resolve(filter)).thenReturn(range);
        when(caseService.getTopSymptoms(any(), any(), any())).thenReturn(List.of());

        dashboardServiceImpl.getTopSymptoms(HEALER_ID, filter);

        verify(dateRangeResolver).resolve(filter);
        verify(caseService).getTopSymptoms(HEALER_ID, FROM, TO);
    }

    // Test 4: getTopSymptoms invalid period propagates exception
    @Test
    void getTopSymptoms_invalidPeriod_throwsIllegalArgumentException() {
        when(dateRangeResolver.resolve(any())).thenThrow(new IllegalArgumentException("Invalid period"));

        assertThrows(IllegalArgumentException.class, () ->
                dashboardServiceImpl.getTopSymptoms(HEALER_ID, new DateRangeFilter("invalid", null, null)));
    }

    // Test 5: getTopProtocols happy path
    @Test
    void getTopProtocols_delegatesToCaseService_returnsWrappedList() {
        DateRangeFilter filter = new DateRangeFilter("30d", null, null);
        DateRangeResolver.DateRange range = new DateRangeResolver.DateRange(FROM, TO);
        when(dateRangeResolver.resolve(filter)).thenReturn(range);
        when(caseService.getTopProtocols(HEALER_ID, FROM, TO))
                .thenReturn(List.of(new DashboardTopProtocol("Basic Pranic Healing", 8L)));

        ApiResponse<List<DashboardTopProtocol>> result = dashboardServiceImpl.getTopProtocols(HEALER_ID, filter);

        assertNotNull(result);
        assertTrue(result.success());
        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).protocol()).isEqualTo("Basic Pranic Healing");
        assertThat(result.data().get(0).count()).isEqualTo(8L);
        verify(dateRangeResolver).resolve(filter);
        verify(caseService).getTopProtocols(HEALER_ID, FROM, TO);
    }

    // Test 6: getTopProtocols empty list
    @Test
    void getTopProtocols_emptyList_returnsSuccessWithEmptyData() {
        DateRangeFilter filter = new DateRangeFilter("7d", null, null);
        DateRangeResolver.DateRange range = new DateRangeResolver.DateRange(FROM, TO);
        when(dateRangeResolver.resolve(filter)).thenReturn(range);
        when(caseService.getTopProtocols(HEALER_ID, FROM, TO)).thenReturn(List.of());

        ApiResponse<List<DashboardTopProtocol>> result = dashboardServiceImpl.getTopProtocols(HEALER_ID, filter);

        assertNotNull(result);
        assertTrue(result.success());
        assertThat(result.data()).isEmpty();
    }

    // Test 7: getTopProtocols delegates LocalDate -- Instant conversion is in CaseService (not here)
    @Test
    void getTopProtocols_callsCaseServiceWithLocalDates() {
        DateRangeFilter filter = new DateRangeFilter(null, FROM, TO);
        DateRangeResolver.DateRange range = new DateRangeResolver.DateRange(FROM, TO);
        when(dateRangeResolver.resolve(filter)).thenReturn(range);
        when(caseService.getTopProtocols(any(UUID.class), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        dashboardServiceImpl.getTopProtocols(HEALER_ID, filter);

        verify(caseService).getTopProtocols(HEALER_ID, FROM, TO);
    }
}
