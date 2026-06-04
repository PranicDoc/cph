package com.pranicdoc.cph.healing.dto.kafka;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Deprecated(since = "0.0.1-SNAPSHOT", forRemoval = false)
public record SessionSnapshot(
        Integer sessionNumber,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        String status,

        List<ChakraScanSnapshot> scans,
        List<SymptomFeedbackSnapshot> symptomFeedbacks,
        List<QualitativeFeedbackSnapshot> qualitativeFeedbacks,
        List<ProtocolEntrySnapshot> protocolEntries
) {}