package com.pranicdoc.cph.healing.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record CreateSessionRequest(
        UUID caseId,
        LocalDate startDate,
        LocalTime startTime,
        LocalTime endTime,
        List<ScanRequest> scans,
        List<SymptomFeedbackRequest> symptomFeedbacks,
        List<QualitativeFeedbackRequest> qualitativeFeedbacks,
        List<ProtocolRequest> protocolEntries
) {
}
