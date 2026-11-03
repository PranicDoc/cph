package com.pranicdoc.cph.healing.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record CreateSessionRequest(
        @NotNull
        UUID caseId,
        @NotNull
        @JsonAlias("startDate")
        LocalDate sessionDate,
        @NotNull
        LocalTime startTime,
        @NotNull
        LocalTime endTime,
        @NotNull @Valid
        List<ScanRequest> scans,
        @Valid
        List<SymptomFeedbackRequest> symptomFeedbacks,
        @Valid
        List<QualitativeFeedbackRequest> qualitativeFeedbacks
) {
}
