package com.pranicdoc.cph.healing.service.impl;

import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.healing.repository.PdfJobRepository;
import com.pranicdoc.cph.healing.service.PdfJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfJobServiceImpl implements PdfJobService {

    private final PdfJobRepository pdfJobRepository;
    private final SecurityContextUtil securityContextUtil;

    @Override
    public long countPdfsGeneratedThisMonth() {
        String healerId = securityContextUtil.getCurrentHealerId().toString();
        LocalDate today = LocalDate.now();
        LocalDateTime startOfMonth =
                today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime startOfNextMonth =
                today.plusMonths(1)
                        .withDayOfMonth(1)
                        .atStartOfDay();
        return pdfJobRepository.countPdfsGeneratedThisMonth(healerId, startOfMonth, startOfNextMonth);
    }
}
