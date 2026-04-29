package com.pranicdoc.cph.healing.dto;

import com.pranicdoc.cph.common.enums.SessionStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        UUID healingCaseId,
        Integer sessionNumber,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        SessionStatus status
) {}
