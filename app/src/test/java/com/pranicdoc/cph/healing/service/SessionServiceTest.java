package com.pranicdoc.cph.healing.service;

import com.pranicdoc.cph.common.enums.ColorUsageType;
import com.pranicdoc.cph.common.enums.FeedbackType;
import com.pranicdoc.cph.common.enums.ScanType;
import com.pranicdoc.cph.common.util.PhoneNumberFormatter;
import com.pranicdoc.cph.healing.dto.CreateSessionRequest;
import com.pranicdoc.cph.healing.dto.ProtocolRequest;
import com.pranicdoc.cph.healing.dto.QualitativeFeedbackRequest;
import com.pranicdoc.cph.healing.dto.ScanRequest;
import com.pranicdoc.cph.healing.dto.SessionResponse;
import com.pranicdoc.cph.healing.mapper.HealingSessionMapper;
import com.pranicdoc.cph.healing.model.ChakraScan;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.model.HealingSession;
import com.pranicdoc.cph.healing.model.ProtocolEntry;
import com.pranicdoc.cph.healing.repository.SessionRepository;
import com.pranicdoc.cph.masterdata.repository.ChakraCatalogRepository;
import com.pranicdoc.cph.notification.repository.OutboxRepository;
import com.pranicdoc.cph.patient.dto.PatientResponse;
import com.pranicdoc.cph.patient.service.PatientService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private CaseService caseService;

    @Mock
    private HealingSessionMapper mapper;

    @Mock
    private ChakraCatalogRepository chakraCatalogRepository;

    @Mock
    private PatientService patientService;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private PhoneNumberFormatter phoneNumberFormatter;

    @InjectMocks
    private SessionService sessionService;

    @Test
    void createPreservesSubmittedSessionDateAndEndTime() {
        UUID caseId = UUID.randomUUID();
        UUID chakraId = UUID.randomUUID();
        UUID colorId = UUID.randomUUID();
        LocalDate sessionDate = LocalDate.of(2026, 5, 13);
        LocalTime startTime = LocalTime.of(9, 30);
        LocalTime endTime = LocalTime.of(10, 45);
        CreateSessionRequest request = request(caseId, sessionDate, startTime, endTime, chakraId, colorId);
        HealingCase healingCase = healingCase(caseId);
        HealingSession session = completedSession(sessionDate, startTime, endTime);

        when(sessionRepository.findByHealingCaseIdOrderBySessionNumberAsc(caseId)).thenReturn(List.of());
        when(caseService.findOrThrow(caseId)).thenReturn(healingCase);
        when(chakraCatalogRepository.existsById(chakraId)).thenReturn(true);
        when(mapper.toEntity(request)).thenReturn(session);
        when(sessionRepository.save(any(HealingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(patientService.getById(any())).thenReturn(new PatientResponse(
                null, null, null, null, null, null, null, "+91234567890",
                null, null, null, null, null, null, null, null));
        when(phoneNumberFormatter.formatToE164(any())).thenThrow(new IllegalArgumentException("invalid"));

        SessionResponse response = sessionService.create(request);

        assertEquals(sessionDate, response.sessionDate());
        assertEquals(endTime, response.endTime());
    }

    @Test
    void createRejectsScanReadingsWithUnknownChakraIds() {
        UUID caseId = UUID.randomUUID();
        UUID chakraId = UUID.randomUUID();
        UUID colorId = UUID.randomUUID();
        CreateSessionRequest request = request(
                caseId,
                LocalDate.of(2026, 5, 13),
                LocalTime.of(9, 30),
                LocalTime.of(10, 45),
                chakraId,
                colorId
        );

        when(sessionRepository.findByHealingCaseIdOrderBySessionNumberAsc(caseId)).thenReturn(List.of());
        when(caseService.findOrThrow(caseId)).thenReturn(healingCase(caseId));
        when(chakraCatalogRepository.existsById(chakraId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> sessionService.create(request));
        verify(mapper, never()).toEntity(any());
    }

    private CreateSessionRequest request(
            UUID caseId,
            LocalDate sessionDate,
            LocalTime startTime,
            LocalTime endTime,
            UUID chakraId,
            UUID colorId
    ) {
        ScanRequest.ChakraReadingRequest reading = new ScanRequest.ChakraReadingRequest(
                chakraId,
                "Crown Chakra",
                "N",
                "N",
                ""
        );

        return new CreateSessionRequest(
                caseId,
                sessionDate,
                startTime,
                endTime,
                List.of(
                        new ScanRequest(ScanType.PRE, "", List.of(reading)),
                        new ScanRequest(ScanType.POST, "", List.of(reading))
                ),
                List.of(),
                List.of(new QualitativeFeedbackRequest(FeedbackType.INTERIM, "", "")),
                List.of(new ProtocolRequest(
                        chakraId,
                        "Crown Chakra",
                        "General Sweeping",
                        "",
                        List.of(new ProtocolRequest.ProtocolColorRequest(colorId, ColorUsageType.CLEANSING)),
                        "Pranic Breathing Protocol",
                        "",
                        "Deep breathing with visualization"
                ))
        );
    }

    private HealingCase healingCase(UUID caseId) {
        HealingCase healingCase = HealingCase.builder().build();
        healingCase.setId(caseId);
        healingCase.setSessions(new ArrayList<>());
        return healingCase;
    }

    private HealingSession completedSession(
            LocalDate sessionDate,
            LocalTime startTime,
            LocalTime endTime
    ) {
        HealingSession session = new HealingSession();
        session.setId(UUID.randomUUID());
        session.setSessionDate(sessionDate);
        session.setStartTime(startTime);
        session.setEndTime(endTime);

        ChakraScan preScan = new ChakraScan();
        preScan.setScanType(ScanType.PRE);
        ChakraScan postScan = new ChakraScan();
        postScan.setScanType(ScanType.POST);
        session.setScans(List.of(preScan, postScan));

        session.setSymptomFeedbacks(List.of());
        session.setQualitativeFeedbacks(List.of());
        session.setProtocolEntries(List.of(new ProtocolEntry()));
        return session;
    }
}
