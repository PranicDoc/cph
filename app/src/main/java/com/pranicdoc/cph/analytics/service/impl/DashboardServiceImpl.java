package com.pranicdoc.cph.analytics.service.impl;

import com.pranicdoc.cph.analytics.dto.DashboardSummary;
import com.pranicdoc.cph.analytics.service.DashboardService;
import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.healing.service.PdfJobService;
import com.pranicdoc.cph.healing.service.impl.SessionService;
import com.pranicdoc.cph.patient.service.PatientService;
import com.pranicdoc.cph.payments.service.CreditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardServiceImpl implements DashboardService {

    private final PatientService patientService;
    private final SessionService sessionService;
    private final CreditService creditService;
    private final PdfJobService pdfJobService;

    @Override
    public ApiResponse<DashboardSummary> getSummary() {
        long activePatients = patientService.getAllActivePatientsCount();
        long sessionsThisMonth = sessionService.getAllSessionsCountThisMonth();
        long pdfsGeneratedThisMonth = pdfJobService.countPdfsGeneratedThisMonth();
        return null;
    }
}
