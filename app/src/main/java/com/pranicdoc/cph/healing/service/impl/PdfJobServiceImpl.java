package com.pranicdoc.cph.healing.service.impl;

import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.healing.repository.PdfJobRepository;
import com.pranicdoc.cph.healing.service.PdfJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfJobServiceImpl implements PdfJobService {

    private final PdfJobRepository pdfJobRepository;
    private final SecurityContextUtil securityContextUtil;

    @Override
    public long countPdfsGeneratedThisMonth() {
        UUID healerId = securityContextUtil.getCurrentHealerId();
        ZoneId zone = ZoneId.systemDefault();
        Instant startOfMonth = LocalDate.now()
                .withDayOfMonth(1)
                .atStartOfDay(zone)
                .toInstant();
        Instant startOfNextMonth = LocalDate.now()
                .plusMonths(1)
                .withDayOfMonth(1)
                .atStartOfDay(zone)
                .toInstant();

        return pdfJobRepository.countPdfsGeneratedThisMonth(healerId, startOfMonth, startOfNextMonth);
    }
}
