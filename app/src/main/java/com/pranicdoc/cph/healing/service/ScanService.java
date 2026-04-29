package com.pranicdoc.cph.healing.service;

import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.healing.dto.ScanRequest;
import com.pranicdoc.cph.healing.model.ChakraReading;
import com.pranicdoc.cph.healing.model.ChakraScan;
import com.pranicdoc.cph.healing.model.HealingSession;
import com.pranicdoc.cph.healing.repository.ChakraScanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ScanService {

    private final ChakraScanRepository chakraScanRepository;
    private final SessionService sessionService;

    /**
     * Save a chakra scan (PRE or POST) with all readings for a session.
     */
    @Auditable(action = "SAVE_SCAN")
    public void saveScan(UUID sessionId, ScanRequest request) {
        HealingSession session = sessionService.findOrThrow(sessionId);

        ChakraScan scan = ChakraScan.builder()
                .healingSession(session)
                .scanType(request.scanType())
                .notes(request.notes())
                .build();

        // Add all chakra readings
        for (ScanRequest.ChakraReadingRequest readingReq : request.readings()) {
            ChakraReading reading = ChakraReading.builder()
                    .chakraScan(scan)
                    .chakraId(readingReq.chakraId())
                    .sizeReading(readingReq.sizeReading())
                    .conditionReading(readingReq.conditionReading())
                    .notes(readingReq.notes())
                    .build();
            scan.getReadings().add(reading);
        }

        chakraScanRepository.save(scan);
    }
}
