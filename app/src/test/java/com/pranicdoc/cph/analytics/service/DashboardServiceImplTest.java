package com.pranicdoc.cph.analytics.service;

import com.pranicdoc.cph.analytics.dto.DashboardSummary;
import com.pranicdoc.cph.analytics.service.impl.DashboardServiceImpl;
import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.healing.service.PdfJobService;
import com.pranicdoc.cph.healing.service.impl.SessionService;
import com.pranicdoc.cph.patient.service.PatientService;
import com.pranicdoc.cph.payments.dto.BalanceCreditResponse;
import com.pranicdoc.cph.payments.dto.OwnerType;
import com.pranicdoc.cph.payments.service.CreditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DashboardServiceImpl.
 *
 * NOTE: Caching integration (Test 4 — @Cacheable coalescing) requires Spring proxy infrastructure
 * and cannot be verified with plain Mockito. Caching integration is covered by running
 * DashboardController integration tests with @SpringBootTest or @WebMvcTest + @MockBean.
 * @Cacheable requires Spring AOP proxy, not direct Mockito invocation.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

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

    // Test 1: Happy path — all services return data; creditsRemaining populated, creditServiceUnavailable=false
    @Test
    void getSummary_happyPath_returnsAllKpis() {
        when(patientService.getAllActivePatientsCount()).thenReturn(5L);
        when(sessionService.getAllSessionsCountThisMonth()).thenReturn(10L);
        when(pdfJobService.countPdfsGeneratedThisMonth()).thenReturn(3L);
        BalanceCreditResponse balanceResponse = new BalanceCreditResponse(
                HEALER_ID, "HEALER", 100, false, false);
        when(creditService.getBalance(eq(OwnerType.HEALER), eq(HEALER_ID))).thenReturn(balanceResponse);

        ApiResponse<DashboardSummary> response = dashboardServiceImpl.getSummary(HEALER_ID);

        assertNotNull(response);
        assertTrue(response.success());
        DashboardSummary summary = response.data();
        assertEquals(5L, summary.activePatients());
        assertEquals(10L, summary.sessionsThisMonth());
        assertEquals(3L, summary.pdfsGeneratedThisMonth());
        assertEquals(100L, summary.creditsRemaining());
        assertFalse(summary.creditServiceUnavailable());
    }

    // Test 2: NoOp null — creditService.getBalance() returns null (credit service disabled)
    @Test
    void getSummary_creditServiceReturnsNull_setsUnavailableFlag() {
        when(patientService.getAllActivePatientsCount()).thenReturn(2L);
        when(sessionService.getAllSessionsCountThisMonth()).thenReturn(4L);
        when(pdfJobService.countPdfsGeneratedThisMonth()).thenReturn(1L);
        when(creditService.getBalance(eq(OwnerType.HEALER), eq(HEALER_ID))).thenReturn(null);

        ApiResponse<DashboardSummary> response = dashboardServiceImpl.getSummary(HEALER_ID);

        assertNotNull(response);
        DashboardSummary summary = response.data();
        assertNull(summary.creditsRemaining(), "creditsRemaining should be null when credit service returns null");
        assertTrue(summary.creditServiceUnavailable(), "creditServiceUnavailable should be true when credit service returns null");
        // Other KPIs still populated
        assertEquals(2L, summary.activePatients());
        assertEquals(4L, summary.sessionsThisMonth());
        assertEquals(1L, summary.pdfsGeneratedThisMonth());
    }

    // Test 3: ResponseStatusException — creditService.getBalance() throws; other KPIs still computed; no exception propagates
    @Test
    void getSummary_creditServiceThrowsResponseStatusException_setsUnavailableFlagNoExceptionPropagates() {
        when(patientService.getAllActivePatientsCount()).thenReturn(7L);
        when(sessionService.getAllSessionsCountThisMonth()).thenReturn(15L);
        when(pdfJobService.countPdfsGeneratedThisMonth()).thenReturn(5L);
        when(creditService.getBalance(eq(OwnerType.HEALER), eq(HEALER_ID)))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "creditService down"));

        ApiResponse<DashboardSummary> response = assertDoesNotThrow(
                () -> dashboardServiceImpl.getSummary(HEALER_ID),
                "ResponseStatusException must not propagate from getSummary()");

        assertNotNull(response);
        DashboardSummary summary = response.data();
        assertNull(summary.creditsRemaining(), "creditsRemaining should be null when credit service throws");
        assertTrue(summary.creditServiceUnavailable(), "creditServiceUnavailable should be true when credit service throws");
        // Other 3 KPIs still computed
        assertEquals(7L, summary.activePatients());
        assertEquals(15L, summary.sessionsThisMonth());
        assertEquals(5L, summary.pdfsGeneratedThisMonth());
    }
}
