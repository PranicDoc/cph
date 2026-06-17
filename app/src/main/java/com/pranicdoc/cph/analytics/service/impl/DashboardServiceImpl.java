package com.pranicdoc.cph.analytics.service.impl;

import com.pranicdoc.cph.analytics.dto.DateRangeFilter;
import com.pranicdoc.cph.analytics.dto.DashboardTopProtocol;
import com.pranicdoc.cph.analytics.dto.DashboardTopSymptom;
import com.pranicdoc.cph.healing.service.impl.CaseService;
import com.pranicdoc.cph.analytics.dto.DashboardPatientGrowth;
import com.pranicdoc.cph.analytics.dto.DashboardSessionTrend;
import com.pranicdoc.cph.analytics.dto.DashboardSummary;
import com.pranicdoc.cph.analytics.service.DashboardService;
import com.pranicdoc.cph.analytics.service.DateRangeResolver;
import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.healing.service.PdfJobService;
import com.pranicdoc.cph.healing.service.impl.SessionService;
import com.pranicdoc.cph.patient.service.PatientService;
import com.pranicdoc.cph.payments.dto.BalanceCreditResponse;
import com.pranicdoc.cph.payments.dto.OwnerType;
import com.pranicdoc.cph.payments.service.CreditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardServiceImpl implements DashboardService {

    private final PatientService patientService;
    private final SessionService sessionService;
    private final CreditService creditService;
    private final PdfJobService pdfJobService;
    private final DateRangeResolver dateRangeResolver;
    private final CaseService caseService;

    @Override
    @Cacheable(value = "dashboard.summary", key = "#healerId", sync = true)
    public ApiResponse<DashboardSummary> getSummary(UUID healerId) {
        long activePatients = patientService.getAllActivePatientsCount();
        long sessionsThisMonth = sessionService.getAllSessionsCountThisMonth();
        long pdfsGeneratedThisMonth = pdfJobService.countPdfsGeneratedThisMonth();

        Long creditsRemaining = null;
        boolean creditServiceUnavailable = false;

        try {
            BalanceCreditResponse balance = creditService.getBalance(OwnerType.HEALER, healerId);
            if (balance != null) {
                creditsRemaining = (long) balance.balance();
            } else {
                creditServiceUnavailable = true;
            }
        } catch (ResponseStatusException e) {
            log.warn("creditService.getBalance() failed for healerId={}: {}", healerId, e.getMessage());
            creditServiceUnavailable = true;
        }

        return ApiResponse.of(new DashboardSummary(
                activePatients,
                sessionsThisMonth,
                creditsRemaining,
                pdfsGeneratedThisMonth,
                creditServiceUnavailable
        ));
    }

    @Override
    @Cacheable(
            value = "dashboard.session-trends",
            key = "#healerId + ':' + #filter.period + ':' + #filter.startDate + ':' + #filter.endDate",
            sync = true
    )
    public ApiResponse<List<DashboardSessionTrend>> getSessionTrends(UUID healerId, DateRangeFilter filter) {
        DateRangeResolver.DateRange range = dateRangeResolver.resolve(filter);
        List<DashboardSessionTrend> raw = sessionService.getSessionTrends(
                healerId.toString(), range.from(), range.to());
        Map<LocalDate, Long> counts = raw.stream()
                .collect(Collectors.toMap(DashboardSessionTrend::date, DashboardSessionTrend::count));
        List<DashboardSessionTrend> filled = range.from().datesUntil(range.to().plusDays(1))
                .map(d -> new DashboardSessionTrend(d, counts.getOrDefault(d, 0L)))
                .toList();
        return ApiResponse.of(filled);
    }

    @Override
    @Cacheable(
            value = "dashboard.patient-growth",
            key = "#healerId + ':' + #filter.period + ':' + #filter.startDate + ':' + #filter.endDate",
            sync = true
    )
    public ApiResponse<List<DashboardPatientGrowth>> getPatientGrowth(UUID healerId, DateRangeFilter filter) {
        DateRangeResolver.DateRange range = dateRangeResolver.resolve(filter);
        List<DashboardPatientGrowth> raw = patientService.getPatientGrowth(
                healerId, range.from(), range.to());
        Map<LocalDate, Long> counts = raw.stream()
                .collect(Collectors.toMap(DashboardPatientGrowth::date, DashboardPatientGrowth::count));
        List<DashboardPatientGrowth> filled = range.from().datesUntil(range.to().plusDays(1))
                .map(d -> new DashboardPatientGrowth(d, counts.getOrDefault(d, 0L)))
                .toList();
        return ApiResponse.of(filled);
    }

    @Override
    @Cacheable(
            value = "dashboard.top-symptoms",
            key = "#healerId + ':' + #filter.period + ':' + #filter.startDate + ':' + #filter.endDate",
            sync = true
    )
    @Transactional(readOnly = true)
    public ApiResponse<List<DashboardTopSymptom>> getTopSymptoms(UUID healerId, DateRangeFilter filter) {
        DateRangeResolver.DateRange range = dateRangeResolver.resolve(filter);
        return ApiResponse.of(caseService.getTopSymptoms(healerId, range.from(), range.to()));
    }

    @Override
    @Cacheable(
            value = "dashboard.top-protocols",
            key = "#healerId + ':' + #filter.period + ':' + #filter.startDate + ':' + #filter.endDate",
            sync = true
    )
    @Transactional(readOnly = true)
    public ApiResponse<List<DashboardTopProtocol>> getTopProtocols(UUID healerId, DateRangeFilter filter) {
        DateRangeResolver.DateRange range = dateRangeResolver.resolve(filter);
        return ApiResponse.of(caseService.getTopProtocols(healerId, range.from(), range.to()));
    }
}