package com.pranicdoc.cph.analytics.service;

import com.pranicdoc.cph.analytics.dto.DashboardFollowup;
import com.pranicdoc.cph.analytics.dto.DashboardPendingReport;
import com.pranicdoc.cph.analytics.dto.DashboardUpcomingSession;
import com.pranicdoc.cph.analytics.service.impl.DashboardServiceImpl;
import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.common.enums.SessionStatus;
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
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DashboardServiceImpl work-queue methods: getUpcomingSessions(),
 * getPendingReports(), and getFollowups().
 *
 * NOTE: @Cacheable AOP is NOT active here -- @InjectMocks bypasses Spring proxy infrastructure.
 * Tests verify delegation to SessionService/PatientService with correct wrapping in ApiResponse.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceImplWorkQueueTest {

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

    // --- getUpcomingSessions tests ---

    @Test
    void getUpcomingSessions_nonEmptyList_returnsWrappedList() {
        List<DashboardUpcomingSession> sessions = List.of(
                new DashboardUpcomingSession(UUID.randomUUID(), "Jane", "Doe",
                        LocalDate.now().plusDays(1), LocalTime.of(10, 0), SessionStatus.DRAFT)
        );
        when(sessionService.getUpcomingSessions(HEALER_ID)).thenReturn(sessions);

        ApiResponse<List<DashboardUpcomingSession>> result =
                dashboardServiceImpl.getUpcomingSessions(HEALER_ID);

        assertNotNull(result);
        assertTrue(result.success());
        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).patientFirstName()).isEqualTo("Jane");
        verify(sessionService).getUpcomingSessions(HEALER_ID);
    }

    @Test
    void getUpcomingSessions_emptyList_returnsSuccessWithEmptyData() {
        when(sessionService.getUpcomingSessions(HEALER_ID)).thenReturn(List.of());

        ApiResponse<List<DashboardUpcomingSession>> result =
                dashboardServiceImpl.getUpcomingSessions(HEALER_ID);

        assertNotNull(result);
        assertTrue(result.success());
        assertThat(result.data()).isEmpty();
        verify(sessionService).getUpcomingSessions(HEALER_ID);
    }

    // --- getPendingReports tests ---

    @Test
    void getPendingReports_nonEmptyList_returnsWrappedList() {
        List<DashboardPendingReport> reports = List.of(
                new DashboardPendingReport(UUID.randomUUID(), "Alice", "Smith",
                        LocalDate.now().minusDays(5))
        );
        when(sessionService.getPendingReports(HEALER_ID)).thenReturn(reports);

        ApiResponse<List<DashboardPendingReport>> result =
                dashboardServiceImpl.getPendingReports(HEALER_ID);

        assertNotNull(result);
        assertTrue(result.success());
        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).patientFirstName()).isEqualTo("Alice");
        verify(sessionService).getPendingReports(HEALER_ID);
    }

    @Test
    void getPendingReports_emptyList_returnsSuccessWithEmptyData() {
        when(sessionService.getPendingReports(HEALER_ID)).thenReturn(List.of());

        ApiResponse<List<DashboardPendingReport>> result =
                dashboardServiceImpl.getPendingReports(HEALER_ID);

        assertNotNull(result);
        assertTrue(result.success());
        assertThat(result.data()).isEmpty();
        verify(sessionService).getPendingReports(HEALER_ID);
    }

    // --- getFollowups tests ---

    @Test
    void getFollowups_nonEmptyList_returnsWrappedList() {
        List<DashboardFollowup> followups = List.of(
                new DashboardFollowup(UUID.randomUUID(), "Bob", "Jones",
                        LocalDate.now().minusDays(45), 45L)
        );
        when(patientService.getFollowups(HEALER_ID)).thenReturn(followups);

        ApiResponse<List<DashboardFollowup>> result =
                dashboardServiceImpl.getFollowups(HEALER_ID);

        assertNotNull(result);
        assertTrue(result.success());
        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).patientFirstName()).isEqualTo("Bob");
        assertThat(result.data().get(0).daysSinceLastSession()).isEqualTo(45L);
        verify(patientService).getFollowups(HEALER_ID);
    }

    @Test
    void getFollowups_emptyList_returnsSuccessWithEmptyData() {
        when(patientService.getFollowups(HEALER_ID)).thenReturn(List.of());

        ApiResponse<List<DashboardFollowup>> result =
                dashboardServiceImpl.getFollowups(HEALER_ID);

        assertNotNull(result);
        assertTrue(result.success());
        assertThat(result.data()).isEmpty();
        verify(patientService).getFollowups(HEALER_ID);
    }
}
