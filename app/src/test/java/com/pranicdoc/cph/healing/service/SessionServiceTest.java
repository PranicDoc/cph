package com.pranicdoc.cph.healing.service;

import com.pranicdoc.cph.common.enums.CaseStatus;
import com.pranicdoc.cph.common.exception.CaseLockedException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.enums.FeedbackType;
import com.pranicdoc.cph.common.enums.ScanType;
import com.pranicdoc.cph.healing.dto.CreateSessionRequest;
import com.pranicdoc.cph.healing.dto.QualitativeFeedbackRequest;
import com.pranicdoc.cph.healing.dto.ScanRequest;
import com.pranicdoc.cph.healing.dto.SessionResponse;
import com.pranicdoc.cph.healing.dto.UpdateSessionRequest;
import com.pranicdoc.cph.healing.mapper.HealingSessionMapper;
import com.pranicdoc.cph.healing.model.ChakraReading;
import com.pranicdoc.cph.healing.model.ChakraScan;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.model.HealingSession;
import com.pranicdoc.cph.healing.repository.SessionRepository;
import com.pranicdoc.cph.healing.service.NotificationService;
import com.pranicdoc.cph.healing.service.impl.CaseService;
import com.pranicdoc.cph.healing.service.impl.SessionService;
import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import com.pranicdoc.cph.masterdata.repository.ChakraCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.PranaColorCatalogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private PranaColorCatalogRepository pranaColorCatalogRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private SecurityContextUtil securityContextUtil;

    @InjectMocks
    private SessionService sessionService;

    // --- Editing a saved session ---

    private final UUID healerId = UUID.randomUUID();

    private HealingSession savedSession(HealingCase healingCase) {
        HealingSession session = completedSession(LocalDate.of(2026, 5, 13), LocalTime.of(9, 30), LocalTime.of(10, 45));
        session.setSessionNumber(2);
        session.setScans(new ArrayList<>(session.getScans()));
        session.getScans().forEach(scan -> scan.setReadings(new ArrayList<>(List.of(
                ChakraReading.builder().chakraName("Crown Chakra").energyLevel("N").build()))));
        session.setHealingCase(healingCase);
        return session;
    }

    private UpdateSessionRequest edit(UUID chakraId, String energy) {
        List<ScanRequest.ChakraReadingRequest> readings =
                List.of(new ScanRequest.ChakraReadingRequest(chakraId, "Solar Plexus", energy, "N", ""));
        return new UpdateSessionRequest(List.of(
                new ScanRequest(ScanType.PRE, "", readings),
                new ScanRequest(ScanType.POST, "", readings)));
    }

    private void stubScanMapping() {
        when(mapper.toChakraScan(any(ScanRequest.class))).thenAnswer(invocation -> {
            ScanRequest req = invocation.getArgument(0);
            ChakraScan scan = new ChakraScan();
            scan.setScanType(req.scanType());
            scan.setNotes(req.notes());
            scan.setReadings(new ArrayList<>(req.readings().stream()
                    .map(r -> ChakraReading.builder().chakraId(r.chakraId()).chakraName(r.chakraName())
                            .energyLevel(r.energyLevel()).activationLevel(r.activationLevel()).build())
                    .toList()));
            return scan;
        });
    }

    @Test
    void updateReplacesTheReadingsAndLeavesStatusNumberAndNotificationsAlone() {
        UUID caseId = UUID.randomUUID();
        UUID chakraId = UUID.randomUUID();
        HealingCase healingCase = healingCase(caseId);
        healingCase.setHealerId(healerId);
        healingCase.setStatus(CaseStatus.CONTINUED);
        HealingSession session = savedSession(healingCase);

        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(caseService.findAndLockOrThrow(caseId)).thenReturn(healingCase);
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        when(chakraCatalogRepository.findAllById(any())).thenReturn(List.of(chakra(chakraId)));
        stubScanMapping();
        when(sessionRepository.save(any(HealingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SessionResponse response = sessionService.update(session.getId(), edit(chakraId, "H"));

        assertEquals(2, response.sessionNumber());
        assertEquals(CaseStatus.CONTINUED, healingCase.getStatus());
        session.getScans().forEach(scan -> {
            assertEquals(1, scan.getReadings().size());
            assertEquals("Solar Plexus", scan.getReadings().get(0).getChakraName());
            assertEquals("H", scan.getReadings().get(0).getEnergyLevel());
            assertEquals(scan, scan.getReadings().get(0).getChakraScan());
        });
        verify(notificationService, never()).sendFeedbackNotification(any(), anyBoolean());
    }

    @Test
    void updateRefusesAnotherHealersSession() {
        UUID caseId = UUID.randomUUID();
        HealingCase healingCase = healingCase(caseId);
        healingCase.setHealerId(UUID.randomUUID());
        HealingSession session = savedSession(healingCase);

        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(caseService.findAndLockOrThrow(caseId)).thenReturn(healingCase);
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);

        assertThrows(AccessDeniedException.class,
                () -> sessionService.update(session.getId(), edit(UUID.randomUUID(), "H")));
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void updateOnALockedCaseIsRefused() {
        UUID caseId = UUID.randomUUID();
        HealingSession session = savedSession(healingCase(caseId));

        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(caseService.findAndLockOrThrow(caseId)).thenThrow(new CaseLockedException());

        assertThrows(CaseLockedException.class,
                () -> sessionService.update(session.getId(), edit(UUID.randomUUID(), "H")));
    }

    @Test
    void updateRejectsAnEmptyScan() {
        UUID caseId = UUID.randomUUID();
        HealingCase healingCase = healingCase(caseId);
        healingCase.setHealerId(healerId);
        HealingSession session = savedSession(healingCase);

        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(caseService.findAndLockOrThrow(caseId)).thenReturn(healingCase);
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        stubScanMapping();

        UpdateSessionRequest empty = new UpdateSessionRequest(List.of(new ScanRequest(ScanType.POST, "", List.of())));
        assertThrows(com.pranicdoc.cph.common.exception.SessionIncompleteException.class,
                () -> sessionService.update(session.getId(), empty));
        verify(sessionRepository, never()).save(any());
    }

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
        HealingSession session = completedSessionWithReadings(sessionDate, startTime, endTime);

        when(sessionRepository.findByHealingCaseIdOrderBySessionNumberAsc(caseId)).thenReturn(List.of());
        when(caseService.findAndLockOrThrow(caseId)).thenReturn(healingCase);
        when(chakraCatalogRepository.findAllById(any())).thenReturn(List.of(chakra(chakraId)));
        when(mapper.toEntity(request)).thenReturn(session);
        when(sessionRepository.save(any(HealingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
        when(caseService.findAndLockOrThrow(caseId)).thenReturn(healingCase(caseId));
        when(chakraCatalogRepository.findAllById(any())).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> sessionService.create(request));
        verify(mapper, never()).toEntity(any());
    }

    @Test
    void createActivatesDraftCase() {
        UUID caseId = UUID.randomUUID();
        UUID chakraId = UUID.randomUUID();
        UUID colorId = UUID.randomUUID();
        CreateSessionRequest request = request(
                caseId, LocalDate.of(2026, 5, 13), LocalTime.of(9, 30), LocalTime.of(10, 45),
                chakraId, colorId);
        HealingCase healingCase = healingCase(caseId);
        assertEquals(CaseStatus.DRAFT, healingCase.getStatus());
        HealingSession session = completedSessionWithReadings(
                LocalDate.of(2026, 5, 13), LocalTime.of(9, 30), LocalTime.of(10, 45));

        when(sessionRepository.findByHealingCaseIdOrderBySessionNumberAsc(caseId)).thenReturn(List.of());
        when(caseService.findAndLockOrThrow(caseId)).thenReturn(healingCase);
        when(chakraCatalogRepository.findAllById(any())).thenReturn(List.of(chakra(chakraId)));
        when(mapper.toEntity(request)).thenReturn(session);
        when(sessionRepository.save(any(HealingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        sessionService.create(request);

        assertEquals(CaseStatus.ACTIVE, healingCase.getStatus());
    }

    @Test
    void createDoesNotOverwriteAlreadyActiveCaseStatus() {
        UUID caseId = UUID.randomUUID();
        UUID chakraId = UUID.randomUUID();
        UUID colorId = UUID.randomUUID();
        CreateSessionRequest request = request(
                caseId, LocalDate.of(2026, 5, 13), LocalTime.of(9, 30), LocalTime.of(10, 45),
                chakraId, colorId);
        HealingCase healingCase = healingCase(caseId);
        healingCase.setStatus(CaseStatus.CLOSED);
        HealingSession session = completedSessionWithReadings(
                LocalDate.of(2026, 5, 13), LocalTime.of(9, 30), LocalTime.of(10, 45));

        when(sessionRepository.findByHealingCaseIdOrderBySessionNumberAsc(caseId)).thenReturn(List.of());
        when(caseService.findAndLockOrThrow(caseId)).thenReturn(healingCase);
        when(chakraCatalogRepository.findAllById(any())).thenReturn(List.of(chakra(chakraId)));
        when(mapper.toEntity(request)).thenReturn(session);
        when(sessionRepository.save(any(HealingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        sessionService.create(request);

        assertEquals(CaseStatus.CLOSED, healingCase.getStatus());
    }

    @Test
    void completeActivatesDraftCase() {
        UUID sessionId = UUID.randomUUID();
        HealingCase healingCase = healingCase(UUID.randomUUID());
        HealingSession session = completedSessionWithReadings(
                LocalDate.of(2026, 5, 13), LocalTime.of(9, 30), null);
        session.setId(sessionId);
        session.setHealingCase(healingCase);

        when(sessionRepository.findById(sessionId)).thenReturn(java.util.Optional.of(session));
        when(sessionRepository.save(any(HealingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        sessionService.complete(sessionId);

        assertEquals(CaseStatus.ACTIVE, healingCase.getStatus());
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
                List.of(new QualitativeFeedbackRequest(FeedbackType.INTERIM, "", ""))
        );
    }

    private ChakraCatalog chakra(UUID id) {
        ChakraCatalog chakra = new ChakraCatalog();
        chakra.setId(id);
        return chakra;
    }

    @Test
    void createValidatesAllChakrasWithASingleCatalogQuery() {
        UUID caseId = UUID.randomUUID();
        UUID colorId = UUID.randomUUID();
        // 2 scans x 25 readings each over 25 distinct chakras (the size of a real submit).
        List<UUID> chakraIds = new ArrayList<>();
        List<ScanRequest.ChakraReadingRequest> readings = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            UUID id = UUID.randomUUID();
            chakraIds.add(id);
            readings.add(new ScanRequest.ChakraReadingRequest(id, "Chakra " + i, "N", "N", ""));
        }
        CreateSessionRequest request = new CreateSessionRequest(
                caseId, LocalDate.of(2026, 5, 13), LocalTime.of(9, 30), LocalTime.of(10, 45),
                List.of(new ScanRequest(ScanType.PRE, "", readings), new ScanRequest(ScanType.POST, "", readings)),
                List.of(),
                List.of(new QualitativeFeedbackRequest(FeedbackType.INTERIM, "", "")));
        HealingSession session = completedSessionWithReadings(
                LocalDate.of(2026, 5, 13), LocalTime.of(9, 30), LocalTime.of(10, 45));

        when(sessionRepository.findByHealingCaseIdOrderBySessionNumberAsc(caseId)).thenReturn(List.of());
        when(caseService.findAndLockOrThrow(caseId)).thenReturn(healingCase(caseId));
        when(chakraCatalogRepository.findAllById(any()))
                .thenReturn(chakraIds.stream().map(this::chakra).toList());
        when(mapper.toEntity(request)).thenReturn(session);
        when(sessionRepository.save(any(HealingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        sessionService.create(request);

        // One round trip for all 50 readings (each DB round trip is ~240 ms in prod), never per reading.
        verify(chakraCatalogRepository, times(1)).findAllById(any());
        verify(chakraCatalogRepository, never()).existsById(any());
    }

    @Test
    void createRejectsWhenOnlySomeChakrasExist() {
        UUID caseId = UUID.randomUUID();
        UUID knownId = UUID.randomUUID();
        UUID unknownId = UUID.randomUUID();
        var readings = List.of(
                new ScanRequest.ChakraReadingRequest(knownId, "Known", "N", "N", ""),
                new ScanRequest.ChakraReadingRequest(unknownId, "Unknown", "N", "N", ""));
        CreateSessionRequest request = new CreateSessionRequest(
                caseId, LocalDate.of(2026, 5, 13), LocalTime.of(9, 30), LocalTime.of(10, 45),
                List.of(new ScanRequest(ScanType.PRE, "", readings), new ScanRequest(ScanType.POST, "", readings)),
                List.of(),
                List.of(new QualitativeFeedbackRequest(FeedbackType.INTERIM, "", "")));

        when(sessionRepository.findByHealingCaseIdOrderBySessionNumberAsc(caseId)).thenReturn(List.of());
        when(caseService.findAndLockOrThrow(caseId)).thenReturn(healingCase(caseId));
        when(chakraCatalogRepository.findAllById(any())).thenReturn(List.of(chakra(knownId)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> sessionService.create(request));
        assertEquals("Invalid chakraId: " + unknownId, ex.getMessage());
        verify(mapper, never()).toEntity(any());
    }

    @Test
    void createWithIdempotencyKeyStoresTheKeyOnTheNewSession() {
        UUID caseId = UUID.randomUUID();
        UUID chakraId = UUID.randomUUID();
        UUID colorId = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        CreateSessionRequest request = request(
                caseId, LocalDate.of(2026, 5, 13), LocalTime.of(9, 30), LocalTime.of(10, 45),
                chakraId, colorId);
        HealingSession session = completedSessionWithReadings(
                LocalDate.of(2026, 5, 13), LocalTime.of(9, 30), LocalTime.of(10, 45));

        when(sessionRepository.findByClientRequestId(key)).thenReturn(Optional.empty());
        when(sessionRepository.findByHealingCaseIdOrderBySessionNumberAsc(caseId)).thenReturn(List.of());
        when(caseService.findAndLockOrThrow(caseId)).thenReturn(healingCase(caseId));
        when(chakraCatalogRepository.findAllById(any())).thenReturn(List.of(chakra(chakraId)));
        when(mapper.toEntity(request)).thenReturn(session);
        when(sessionRepository.save(any(HealingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        sessionService.create(request, key);

        ArgumentCaptor<HealingSession> saved = ArgumentCaptor.forClass(HealingSession.class);
        verify(sessionRepository).save(saved.capture());
        assertEquals(key, saved.getValue().getClientRequestId());
    }

    @Test
    void createWithAKeyAlreadyUsedReturnsTheOriginalSessionWithoutCreatingAnother() {
        UUID caseId = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        CreateSessionRequest request = request(
                caseId, LocalDate.of(2026, 5, 13), LocalTime.of(9, 30), LocalTime.of(10, 45),
                UUID.randomUUID(), UUID.randomUUID());
        HealingSession original = completedSessionWithReadings(
                LocalDate.of(2026, 5, 13), LocalTime.of(9, 30), LocalTime.of(10, 45));
        original.setHealingCase(healingCase(caseId));
        original.setSessionNumber(1);
        original.setClientRequestId(key);

        when(sessionRepository.findByClientRequestId(key)).thenReturn(Optional.of(original));

        SessionResponse response = sessionService.create(request, key);

        assertEquals(original.getId(), response.id());
        assertEquals(1, response.sessionNumber());
        verify(sessionRepository, never()).save(any());
        verify(notificationService, never()).sendFeedbackNotification(any(), anyBoolean());
    }

    @Test
    void createRejectsAKeyAlreadyUsedForAnotherCase() {
        UUID key = UUID.randomUUID();
        CreateSessionRequest request = request(
                UUID.randomUUID(), LocalDate.of(2026, 5, 13), LocalTime.of(9, 30), LocalTime.of(10, 45),
                UUID.randomUUID(), UUID.randomUUID());
        HealingSession other = completedSession(
                LocalDate.of(2026, 5, 13), LocalTime.of(9, 30), LocalTime.of(10, 45));
        other.setHealingCase(healingCase(UUID.randomUUID()));

        when(sessionRepository.findByClientRequestId(key)).thenReturn(Optional.of(other));

        assertThrows(IllegalArgumentException.class, () -> sessionService.create(request, key));
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void createLocksTheCaseBeforeCheckingTheKeyAndNumberingTheSession() {
        UUID caseId = UUID.randomUUID();
        UUID chakraId = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        CreateSessionRequest request = request(
                caseId, LocalDate.of(2026, 5, 13), LocalTime.of(9, 30), LocalTime.of(10, 45),
                chakraId, UUID.randomUUID());

        when(sessionRepository.findByClientRequestId(key)).thenReturn(Optional.empty());
        when(sessionRepository.findByHealingCaseIdOrderBySessionNumberAsc(caseId)).thenReturn(List.of());
        when(caseService.findAndLockOrThrow(caseId)).thenReturn(healingCase(caseId));
        when(chakraCatalogRepository.findAllById(any())).thenReturn(List.of(chakra(chakraId)));
        when(mapper.toEntity(request)).thenReturn(completedSessionWithReadings(
                LocalDate.of(2026, 5, 13), LocalTime.of(9, 30), LocalTime.of(10, 45)));
        when(sessionRepository.save(any(HealingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        sessionService.create(request, key);

        InOrder inOrder = inOrder(caseService, sessionRepository);
        inOrder.verify(caseService).findAndLockOrThrow(caseId);
        inOrder.verify(sessionRepository).findByClientRequestId(key);
        inOrder.verify(sessionRepository).findByHealingCaseIdOrderBySessionNumberAsc(caseId);
    }

    @Test
    void createNumbersAfterTheHighestLiveSessionNotTheCount() {
        UUID caseId = UUID.randomUUID();
        UUID chakraId = UUID.randomUUID();
        CreateSessionRequest request = request(
                caseId, LocalDate.of(2026, 5, 13), LocalTime.of(9, 30), LocalTime.of(10, 45),
                chakraId, UUID.randomUUID());
        HealingSession first = new HealingSession();
        first.setSessionNumber(1);
        HealingSession third = new HealingSession();
        third.setSessionNumber(3); // session 2 was soft-deleted

        when(sessionRepository.findByHealingCaseIdOrderBySessionNumberAsc(caseId)).thenReturn(List.of(first, third));
        when(caseService.findAndLockOrThrow(caseId)).thenReturn(healingCase(caseId));
        when(chakraCatalogRepository.findAllById(any())).thenReturn(List.of(chakra(chakraId)));
        when(mapper.toEntity(request)).thenReturn(completedSessionWithReadings(
                LocalDate.of(2026, 5, 13), LocalTime.of(9, 30), LocalTime.of(10, 45)));
        when(sessionRepository.save(any(HealingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SessionResponse response = sessionService.create(request);

        assertEquals(4, response.sessionNumber());
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
        return session;
    }

    /** Same as {@link #completedSession} but with a reading on each scan, satisfying validatePrePostAndProtocolExists. */
    private HealingSession completedSessionWithReadings(
            LocalDate sessionDate,
            LocalTime startTime,
            LocalTime endTime
    ) {
        HealingSession session = completedSession(sessionDate, startTime, endTime);
        session.getScans().forEach(scan ->
                scan.setReadings(List.of(ChakraReading.builder().chakraName("Crown Chakra").build())));
        return session;
    }
}
