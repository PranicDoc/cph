package com.pranicdoc.cph.analytics.service.impl;

import com.pranicdoc.cph.analytics.dto.DateRangeFilter;
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
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardServiceImpl implements DashboardService {

    private final PatientService patientService;
    private final SessionService sessionService;
    private final CreditService creditService;
    private final PdfJobService pdfJobService;
    private final DateRangeResolver dateRangeResolver;

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
        dateRangeResolver.resolve(filter);
        return ApiResponse.of(sessionService.getSessionTrends());
    }
}
