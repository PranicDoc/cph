package com.pranicdoc.cph.healing.mapper;

import com.pranicdoc.cph.healing.dto.CreateSessionRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealingSessionMapperTest {

    private final HealingSessionMapper mapper = new HealingSessionMapperImpl();

    @Test
    void toEntityMapsCanonicalSessionDateAndEndTime() {
        LocalDate sessionDate = LocalDate.of(2026, 5, 13);
        LocalTime startTime = LocalTime.of(9, 30);
        LocalTime endTime = LocalTime.of(10, 45);

        var session = mapper.toEntity(new CreateSessionRequest(
                UUID.randomUUID(),
                3,
                sessionDate,
                startTime,
                endTime,
                List.of(),
                List.of(),
                List.of()
        ));

        assertEquals(3, session.getSessionNumber());
        assertEquals(sessionDate, session.getSessionDate());
        assertEquals(endTime, session.getEndTime());
    }

    /** The UI omits the feedback lists on a new session; the entity must still get empty, mutable lists. */
    @Test
    void toEntityGivesEmptyFeedbackListsWhenRequestOmitsThem() {
        var session = mapper.toEntity(new CreateSessionRequest(
                UUID.randomUUID(),
                4,
                LocalDate.of(2026, 11, 11),
                LocalTime.of(19, 29),
                LocalTime.of(20, 29),
                List.of(),
                null,
                null
        ));

        assertNotNull(session.getQualitativeFeedbacks());
        assertNotNull(session.getSymptomFeedbacks());
        assertTrue(session.getQualitativeFeedbacks().isEmpty());
        session.getQualitativeFeedbacks().add(new com.pranicdoc.cph.healing.model.QualitativeFeedback());
        session.getSymptomFeedbacks().add(new com.pranicdoc.cph.healing.model.SymptomFeedback());
    }
}
