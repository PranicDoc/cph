package com.pranicdoc.cph.healing.mapper;

import com.pranicdoc.cph.healing.dto.CreateSessionRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealingSessionMapperTest {

    private final HealingSessionMapper mapper = new HealingSessionMapperImpl();

    @Test
    void toEntityMapsCanonicalSessionDateAndEndTime() {
        LocalDate sessionDate = LocalDate.of(2026, 5, 13);
        LocalTime startTime = LocalTime.of(9, 30);
        LocalTime endTime = LocalTime.of(10, 45);

        var session = mapper.toEntity(new CreateSessionRequest(
                UUID.randomUUID(),
                sessionDate,
                startTime,
                endTime,
                List.of(),
                List.of(),
                List.of()
        ));

        assertEquals(sessionDate, session.getSessionDate());
        assertEquals(endTime, session.getEndTime());
    }
}
