package com.pranicdoc.cph.healing.service.impl;

import com.pranicdoc.cph.analytics.dto.DashboardSessionTrend;
import com.pranicdoc.cph.analytics.dto.DashboardUpcomingSession;
import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.enums.SessionStatus;
import com.pranicdoc.cph.common.enums.ScanType;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.exception.SessionIncompleteException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.healing.dto.CreateSessionRequest;
import com.pranicdoc.cph.healing.dto.SessionResponse;
import com.pranicdoc.cph.healing.event.HealingSessionCompletedEvent;
import com.pranicdoc.cph.healing.mapper.HealingSessionMapper;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.model.HealingSession;
import com.pranicdoc.cph.healing.repository.SessionRepository;
import com.pranicdoc.cph.healing.service.NotificationService;
import com.pranicdoc.cph.masterdata.repository.ChakraCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.PranaColorCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SessionService {

    private final SessionRepository sessionRepository;
    private final CaseService caseService;
    private final HealingSessionMapper mapper;
    private final ChakraCatalogRepository chakraCatalogRepository;
    private final PranaColorCatalogRepository pranaColorCatalogRepository;
    private final NotificationService notificationService;
    private final SecurityContextUtil securityContextUtil;
    private final ApplicationEventPublisher eventPublisher;

    @Auditable(action = "CREATE_SESSION_OLD")
    public SessionResponse create(UUID caseId) {
        caseService.requireConsent(caseId);
        HealingCase healingCase = caseService.findOrThrow(caseId);

        // Determine next session number
        List<HealingSession> existing = sessionRepository
                .findByHealingCaseIdOrderBySessionNumberAsc(caseId);
        int nextNumber = existing.size() + 1;

        HealingSession session = HealingSession.builder()
                .healingCase(healingCase)
                .sessionNumber(nextNumber)
                .sessionDate(LocalDate.now())
                .startTime(LocalTime.now())
                .status(SessionStatus.DRAFT)
                .build();

        return toResponse(sessionRepository.save(session));
    }

    @Auditable(action = "CREATE_SESSION")
    @Transactional
    public SessionResponse create(CreateSessionRequest sessionRequest) {
        List<HealingSession> existing = sessionRepository
                .findByHealingCaseIdOrderBySessionNumberAsc(sessionRequest.caseId());
        if(existing.isEmpty()) {
            caseService.requireConsent(sessionRequest.caseId());
        }
        HealingCase healingCase = caseService.findOrThrow(sessionRequest.caseId());
        int nextNumber = existing.size() + 1;
        validateMasterDataReferences(sessionRequest);
        HealingSession session = mapper.toEntity(sessionRequest);
        session.setSessionNumber(nextNumber);
        // TODO this is a basic validation, add few more checks comparing with the base data
        validatePrePostAndProtocolExists(session);
        session.setStatus(SessionStatus.COMPLETED);
        session.setHealingCase(healingCase);
        HealingSession saved = sessionRepository.save(session);
        notificationService.sendFeedbackNotification(saved, false);
        return toResponse(saved);
    }

    private void validatePrePostAndProtocolExists(HealingSession session) {
        boolean hasPreScan = session.getScans().stream()
                .anyMatch(s -> s.getScanType() == ScanType.PRE);
        boolean hasPostScan = session.getScans().stream()
                .anyMatch(s -> s.getScanType() == ScanType.POST);
        if (!hasPreScan) throw new SessionIncompleteException("Pre-scan is required");
        if (!hasPostScan) throw new SessionIncompleteException("Post-scan is required");
    }

    private void validateMasterDataReferences(CreateSessionRequest request) {
        if (request.scans() != null) {
            request.scans().forEach(scan -> {
                if (scan.readings() != null) {
                    scan.readings().forEach(reading -> validateChakraId(reading.chakraId()));
                }
            });
        }
    }

    private void validateChakraId(UUID chakraId) {
        if (!chakraCatalogRepository.existsById(chakraId)) {
            throw new IllegalArgumentException("Invalid chakraId: " + chakraId);
        }
    }

    @Transactional(readOnly = true)
    public SessionResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> getByCaseId(UUID caseId) {
        return sessionRepository.findByHealingCaseIdOrderBySessionNumberAsc(caseId)
                .stream().map(this::toResponse).toList();
    }

    /**
     * Complete a session -- validates all required steps are present.
     */
    @Auditable(action = "COMPLETE_SESSION")
    public SessionResponse complete(UUID sessionId) {
        HealingSession session = findOrThrow(sessionId);
        // Validate completeness
        validatePrePostAndProtocolExists(session);
        session.setEndTime(LocalTime.now());
        session.setStatus(SessionStatus.COMPLETED);
        HealingSession saved = sessionRepository.save(session);
        return toResponse(saved);
    }

    public HealingSession findOrThrow(UUID id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("HealingSession", id));
    }

    @Transactional(readOnly = true)
    public HealingSession findLatestByCaseIdOrThrow(UUID caseId) {
        List<HealingSession> sessions = sessionRepository
                .findByHealingCaseIdOrderBySessionNumberAsc(caseId);
        if (sessions.isEmpty()) {
            throw new ResourceNotFoundException("HealingSession for case", caseId);
        }
        return sessions.get(sessions.size() - 1);
    }

    private SessionResponse toResponse(HealingSession s) {
        return new SessionResponse(
                s.getId(), s.getHealingCase().getId(),
                s.getSessionNumber(), s.getSessionDate(),
                s.getStartTime(), s.getEndTime(), s.getStatus()
        );
    }

    @Auditable(action = "FETCH_ALL_SESSIONS_THIS_MONTH")
    @Transactional(readOnly = true)
    public long getAllSessionsCountThisMonth() {
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

        return sessionRepository.getAllSessionsCountThisMonth(healerId, startOfMonth, startOfNextMonth);
    }

    @Auditable(action = "FETCH_SESSION_TRENDS")
    @Transactional(readOnly = true)
    public List<DashboardSessionTrend> getSessionTrends(UUID healerId, LocalDate from, LocalDate to) {
        return sessionRepository.getSessionTrends(healerId, from, to);
    }

    @Auditable(action = "FETCH_DRAFT_SESSIONS")
    @Transactional(readOnly = true)
    public List<DashboardUpcomingSession> getDraftSessions(UUID healerId) {
        return sessionRepository.getDraftSessions(healerId);
    }
}