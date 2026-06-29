package com.pranicdoc.cph.analytics.service;

import com.pranicdoc.cph.analytics.dto.DashboardFollowup;
import com.pranicdoc.cph.analytics.dto.DashboardPendingReport;
import com.pranicdoc.cph.analytics.dto.DashboardUpcomingSession;
import com.pranicdoc.cph.analytics.service.impl.DashboardServiceImpl;
import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.healing.service.PdfJobService;
import com.pranicdoc.cph.healing.service.impl.CaseService;
import com.pranicdoc.cph.healing.service.impl.SessionService;
import com.pranicdoc.cph.notification.repository.FeedbackLinkRepository;
import com.pranicdoc.cph.patient.service.PatientService;
import com.pranicdoc.cph.payments.service.CreditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplWorkQueueTest {

    @Mock private PatientService patientService;
    @Mock private SessionService sessionService;
    @Mock private CreditService creditService;
    @Mock private PdfJobService pdfJobService;
    @Mock private DateRangeResolver dateRangeResolver;
    @Mock private CaseService caseService;
    @Mock private FeedbackLinkRepository feedbackLinkRepository;

    @InjectMocks
    private DashboardServiceImpl dashboardServiceImpl;

    private static final UUID HEALER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    // --- getUpcomingSessions ---

    @Test
    void getUpcomingSessions_combinesDraftsAndActiveCases() {
        DashboardUpcomingSession draft = new DashboardUpcomingSession(
                UUID.randomUUID(), "Jane", "Doe", LocalDate.now(), null, "DRAFT_SESSION");
        DashboardUpcomingSession activeCase = new DashboardUpcomingSession(
                UUID.randomUUID(), "Tom", "Hill", LocalDate.now().minusDays(3), null, "ACTIVE_CASE");

        when(sessionService.getDraftSessions(HEALER_ID)).thenReturn(List.of(draft));
        when(caseService.getActiveCasesForQueue(HEALER_ID)).thenReturn(List.of(activeCase));

        ApiResponse<List<DashboardUpcomingSession>> result =
                dashboardServiceImpl.getUpcomingSessions(HEALER_ID);

        assertNotNull(result);
        assertTrue(result.success());
        assertThat(result.data()).hasSize(2);
        assertThat(result.data().get(0).type()).isEqualTo("DRAFT_SESSION");
        assertThat(result.data().get(1).type()).isEqualTo("ACTIVE_CASE");
        verify(sessionService).getDraftSessions(HEALER_ID);
        verify(caseService).getActiveCasesForQueue(HEALER_ID);
    }

    @Test
    void getUpcomingSessions_emptyBoth_returnsEmptyList() {
        when(sessionService.getDraftSessions(HEALER_ID)).thenReturn(List.of());
        when(caseService.getActiveCasesForQueue(HEALER_ID)).thenReturn(List.of());

        ApiResponse<List<DashboardUpcomingSession>> result =
                dashboardServiceImpl.getUpcomingSessions(HEALER_ID);

        assertTrue(result.success());
        assertThat(result.data()).isEmpty();
    }

    // --- getPendingReports ---

    @Test
    void getPendingReports_returnsClosedCasesWithoutPdf() {
        List<DashboardPendingReport> reports = List.of(
                new DashboardPendingReport(UUID.randomUUID(), "Alice", "Smith",
                        LocalDate.now().minusDays(5))
        );
        when(caseService.getPendingReports(HEALER_ID)).thenReturn(reports);

        ApiResponse<List<DashboardPendingReport>> result =
                dashboardServiceImpl.getPendingReports(HEALER_ID);

        assertTrue(result.success());
        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).patientFirstName()).isEqualTo("Alice");
        verify(caseService).getPendingReports(HEALER_ID);
    }

    @Test
    void getPendingReports_emptyList_returnsSuccessWithEmptyData() {
        when(caseService.getPendingReports(HEALER_ID)).thenReturn(List.of());

        ApiResponse<List<DashboardPendingReport>> result =
                dashboardServiceImpl.getPendingReports(HEALER_ID);

        assertTrue(result.success());
        assertThat(result.data()).isEmpty();
    }

    // --- getFollowups ---

    @Test
    void getFollowups_returnsPendingFeedbackLinks() {
        FeedbackLinkRepository.FollowupPoint point = mock(FeedbackLinkRepository.FollowupPoint.class);
        when(point.getCaseId()).thenReturn(UUID.randomUUID());
        when(point.getPatientFirstName()).thenReturn("Bob");
        when(point.getPatientLastName()).thenReturn("Jones");
        when(point.getFeedbackToken()).thenReturn("token-abc");
        when(point.getSentAt()).thenReturn(Instant.now().minusSeconds(90_000));
        when(point.getIsFinal()).thenReturn(true);

        when(feedbackLinkRepository.getFollowupsForQueue(any(UUID.class), any(Instant.class)))
                .thenReturn(List.of(point));

        ApiResponse<List<DashboardFollowup>> result =
                dashboardServiceImpl.getFollowups(HEALER_ID);

        assertTrue(result.success());
        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).patientFirstName()).isEqualTo("Bob");
        assertThat(result.data().get(0).isFinal()).isTrue();
        verify(feedbackLinkRepository).getFollowupsForQueue(any(UUID.class), any(Instant.class));
    }

    @Test
    void getFollowups_emptyList_returnsSuccessWithEmptyData() {
        when(feedbackLinkRepository.getFollowupsForQueue(any(UUID.class), any(Instant.class)))
                .thenReturn(List.of());

        ApiResponse<List<DashboardFollowup>> result =
                dashboardServiceImpl.getFollowups(HEALER_ID);

        assertTrue(result.success());
        assertThat(result.data()).isEmpty();
    }
}
