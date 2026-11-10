package com.pranicdoc.cph.healing.service.impl;

import com.pranicdoc.cph.analytics.dto.DashboardSessionTrend;
import com.pranicdoc.cph.analytics.dto.DashboardUpcomingSession;
import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.enums.CaseStatus;
import com.pranicdoc.cph.common.enums.SessionStatus;
import com.pranicdoc.cph.common.enums.ScanType;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.exception.SessionIncompleteException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.healing.dto.CreateSessionRequest;
import com.pranicdoc.cph.healing.dto.ScanRequest;
import com.pranicdoc.cph.healing.dto.SessionResponse;
import com.pranicdoc.cph.healing.dto.UpdateSessionRequest;
import com.pranicdoc.cph.healing.mapper.HealingSessionMapper;
import com.pranicdoc.cph.common.enums.FeedbackType;
import com.pranicdoc.cph.healing.model.ChakraScan;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.model.HealingSession;
import com.pranicdoc.cph.healing.model.QualitativeFeedback;
import com.pranicdoc.cph.healing.repository.SessionRepository;
import com.pranicdoc.cph.healing.service.NotificationService;
import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import com.pranicdoc.cph.masterdata.repository.ChakraCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.PranaColorCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SessionService {

    private final SessionRepository sessionRepository;
    private final com.pranicdoc.cph.healing.repository.ProtocolEntryRepository protocolEntryRepository;
    private final CaseService caseService;
    private final HealingSessionMapper mapper;
    private final ChakraCatalogRepository chakraCatalogRepository;
    private final PranaColorCatalogRepository pranaColorCatalogRepository;
    private final NotificationService notificationService;
    private final SecurityContextUtil securityContextUtil;
    private final ApplicationEventPublisher eventPublisher;
    private final com.pranicdoc.cph.auth.service.HealerCacheService healerCacheService;

    @Auditable(action = "CREATE_SESSION_OLD")
    public SessionResponse create(UUID caseId) {
        HealingCase healingCase = caseService.findAndLockOrThrow(caseId);
        caseService.requireConsent(caseId);

        // Determine next session number
        List<HealingSession> existing = sessionRepository
                .findByHealingCaseIdOrderBySessionNumberAsc(caseId);
        int nextNumber = nextSessionNumber(existing);

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
        return create(sessionRequest, null);
    }

    /**
     * Creates a completed session. {@code idempotencyKey} (the client's Idempotency-Key header)
     * makes a retried submit safe: if a session was already created with that key, it is
     * returned unchanged instead of creating a duplicate (2026-09-21: a submit that hit the
     * nginx 504 had in fact committed, and the healer's resubmit made a second session 1).
     * The case is loaded with a row lock first (same query, no extra round trip), so a
     * concurrent retry waits for the original to commit and then finds it by key.
     */
    @Auditable(action = "CREATE_SESSION")
    @Transactional
    public SessionResponse create(CreateSessionRequest sessionRequest, UUID idempotencyKey) {
        HealingCase healingCase = caseService.findAndLockOrThrow(sessionRequest.caseId());
        if (idempotencyKey != null) {
            Optional<HealingSession> replay = sessionRepository.findByClientRequestId(idempotencyKey);
            if (replay.isPresent()) {
                HealingSession original = replay.get();
                if (!original.getHealingCase().getId().equals(sessionRequest.caseId())) {
                    throw new IllegalArgumentException("Idempotency-Key was already used for a different case");
                }
                return toResponse(original);
            }
        }
        List<HealingSession> existing = sessionRepository
                .findByHealingCaseIdOrderBySessionNumberAsc(sessionRequest.caseId());
        if(existing.isEmpty()) {
            caseService.requireConsent(sessionRequest.caseId());
        }
        requireUniqueNumber(sessionRequest.caseId(), sessionRequest.sessionNumber(), null);
        validateMasterDataReferences(sessionRequest.scans());
        HealingSession session = mapper.toEntity(sessionRequest);
        session.setClientRequestId(idempotencyKey);
        // TODO this is a basic validation, add few more checks comparing with the base data
        validatePrePostAndProtocolExists(session);
        session.setStatus(SessionStatus.COMPLETED);
        session.setHealingCase(healingCase);
        activateCaseIfDraftOrContinued(healingCase);
        HealingSession saved = sessionRepository.save(session);
        List<HealingSession> all = new ArrayList<>(existing);
        all.add(saved);
        reanchorFinalFeedback(all);
        healingCase.getSessions().add(saved);
        caseService.refreshClosureFromSessions(healingCase);
        notificationService.sendFeedbackNotification(saved, false);
        return toResponse(saved);
    }

    /**
     * Corrects the PRE/POST scans of a session that was already saved, on a case of any status.
     * Unlike {@link #create} it leaves the case status, the session number and times alone and
     * sends no notification: an edit is not a new healing.
     */
    @Auditable(action = "UPDATE_SESSION")
    public SessionResponse update(UUID sessionId, UpdateSessionRequest request) {
        // Warm the auditor email before any entity is added, so the @CreatedBy lookup on new child rows
        // is a cache hit instead of a DB query mid-flush.
        healerCacheService.getEmail(securityContextUtil.getCurrentHealerId());
        HealingSession session = findOrThrow(sessionId);
        HealingCase healingCase = caseService.findAndLockOrThrow(session.getHealingCase().getId());
        // Loading by id skips the tenant filter, so check the owner here.
        if (!healingCase.getHealerId().equals(securityContextUtil.getCurrentHealerId())) {
            throw new AccessDeniedException("Case does not belong to you");
        }
        validateMasterDataReferences(request.scans());
        if (request.sessionDate() == null) {
            throw new IllegalArgumentException("Session date is required");
        }
        if (request.sessionDate().isAfter(LocalDate.now().plusDays(1))) {
            throw new IllegalArgumentException("Session date cannot be in the future");
        }
        session.setSessionDate(request.sessionDate());
        // Interim feedback carries a copy of the session date taken when it was recorded; keep it in
        // step. A final feedback has its own healer-set date and is left alone.
        session.getSymptomFeedbacks().forEach(f -> f.setHealingDate(request.sessionDate()));
        session.getQualitativeFeedbacks().stream()
                .filter(q -> q.getFeedbackType() == FeedbackType.INTERIM)
                .forEach(q -> q.setHealingDate(request.sessionDate()));
        if (request.sessionNumber() != null
                && !request.sessionNumber().equals(session.getSessionNumber())) {
            requireUniqueNumber(healingCase.getId(), request.sessionNumber(), session.getId());
            session.setSessionNumber(request.sessionNumber());
        }
        for (ScanRequest scanRequest : request.scans()) {
            ChakraScan edited = mapper.toChakraScan(scanRequest);
            ChakraScan scan = session.getScans().stream()
                    .filter(s -> s.getScanType() == scanRequest.scanType())
                    .findFirst()
                    .orElse(null);
            if (scan == null) {
                edited.setHealingSession(session);
                session.getScans().add(edited);
                continue;
            }
            scan.setNotes(edited.getNotes());
            scan.getReadings().clear();
            // Delete the old readings first: (chakra_scan_id, chakra_id) is unique.
            sessionRepository.flush();
            edited.getReadings().forEach(reading -> {
                reading.setChakraScan(scan);
                scan.getReadings().add(reading);
            });
        }
        validatePrePostAndProtocolExists(session);
        reanchorFinalFeedback(healingCase.getSessions());
        HealingSession updated = sessionRepository.save(session);
        caseService.refreshClosureFromSessions(healingCase);
        return toResponse(updated);
    }

    /**
     * Permanently deletes a session with its scans, readings, feedback and protocol entries.
     * Not allowed once the case is closed. Session numbers are healer-entered, so the remaining
     * sessions are left exactly as they are.
     */
    @Auditable(action = "DELETE_SESSION")
    public void delete(UUID sessionId) {
        HealingSession session = findOrThrow(sessionId);
        HealingCase healingCase = caseService.findAndLockOrThrow(session.getHealingCase().getId());
        if (!healingCase.getHealerId().equals(securityContextUtil.getCurrentHealerId())) {
            throw new AccessDeniedException("Case does not belong to you");
        }
        if (healingCase.getStatus() == CaseStatus.CLOSED) {
            throw new IllegalArgumentException("Cannot delete a session of a closed case");
        }
        // ProtocolEntry is not cascaded from HealingSession (colours cascade from the entry).
        protocolEntryRepository.deleteAll(protocolEntryRepository.findByHealingSessionId(sessionId));
        healingCase.getSessions().remove(session);
        // The case's final feedback hangs on a session; move it off this one before it is deleted.
        reanchorFinalFeedback(healingCase.getSessions());
        sessionRepository.delete(session);
        sessionRepository.flush();
        caseService.refreshClosureFromSessions(healingCase);
    }

    /**
     * A case has one final feedback, kept on its latest session (the highest session number). Session
     * numbers are healer-entered and can be changed afterwards, so the latest session can change under
     * it: move the final feedback to whichever session is the latest now, so it never reads as
     * belonging to an earlier one. Its own healer-set date is left as it is.
     */
    private void reanchorFinalFeedback(List<HealingSession> sessions) {
        HealingSession latest = sessions.stream()
                .filter(s -> s.getSessionNumber() != null)
                .max(Comparator.comparing(HealingSession::getSessionNumber))
                .orElse(null);
        if (latest == null) return;
        for (HealingSession other : sessions) {
            if (other == latest) continue;
            List<QualitativeFeedback> finals = other.getQualitativeFeedbacks().stream()
                    .filter(q -> q.getFeedbackType() == FeedbackType.FINAL)
                    .toList();
            for (QualitativeFeedback finalFeedback : finals) {
                other.getQualitativeFeedbacks().remove(finalFeedback);
                finalFeedback.setHealingSession(latest);
                latest.getQualitativeFeedbacks().add(finalFeedback);
            }
        }
    }

    private void requireUniqueNumber(UUID caseId, Integer number, UUID excludeSessionId) {
        if (number == null || number < 1) {
            throw new IllegalArgumentException("Session number is required");
        }
        boolean taken = excludeSessionId == null
                ? sessionRepository.existsByHealingCaseIdAndSessionNumber(caseId, number)
                : sessionRepository.existsByHealingCaseIdAndSessionNumberAndIdNot(caseId, number, excludeSessionId);
        if (taken) {
            throw new IllegalArgumentException("Session number " + number + " is already used in this case");
        }
    }

    /**
     * A completed session is proof the case is under active treatment --
     * cases otherwise only leave DRAFT via the separate case-details PATCH,
     * which a healer may never call before finishing the first session.
     */
    private void activateCaseIfDraftOrContinued(HealingCase healingCase) {
        if (healingCase.getStatus() == CaseStatus.DRAFT
                || healingCase.getStatus() == CaseStatus.CONTINUED) {
            healingCase.setStatus(CaseStatus.ACTIVE);
        }
    }

    private void validatePrePostAndProtocolExists(HealingSession session) {
        boolean isPreScanEmpty = session.getScans().stream()
                .filter(s -> s.getScanType() == ScanType.PRE)
                .findFirst()
                .map(scan -> scan.getReadings().isEmpty())
                .orElse(false);
        boolean isPostScanEmpty = session.getScans().stream()
                .filter(s -> s.getScanType() == ScanType.POST)
                .findFirst()
                .map(scan -> scan.getReadings().isEmpty())
                .orElse(false);
        if (isPreScanEmpty) throw new SessionIncompleteException("Pre-scan is required");
        if (isPostScanEmpty) throw new SessionIncompleteException("Post-scan is required");
    }

    /**
     * Every submitted reading must point at a real chakra_catalog row. Resolved with ONE
     * {@code findAllById} query for the distinct ids, not one {@code existsById} per reading:
     * with the DB ~240 ms away, a 2 x 25-reading scan was ~50 sequential round trips (~12 s)
     * and, together with the commit, pushed POST /sessions/case past nginx's 30 s timeout.
     */
    private void validateMasterDataReferences(List<ScanRequest> scans) {
        if (scans == null) return;
        Set<UUID> requested = new LinkedHashSet<>();
        scans.forEach(scan -> {
            if (scan.readings() == null) return;
            scan.readings().forEach(reading -> {
                if (reading.chakraId() == null) {
                    throw new IllegalArgumentException("Invalid chakraId: null");
                }
                requested.add(reading.chakraId());
            });
        });
        if (requested.isEmpty()) return;

        Set<UUID> known = chakraCatalogRepository.findAllById(requested).stream()
                .map(ChakraCatalog::getId)
                .collect(Collectors.toSet());
        for (UUID chakraId : requested) {
            if (!known.contains(chakraId)) {
                throw new IllegalArgumentException("Invalid chakraId: " + chakraId);
            }
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
        activateCaseIfDraftOrContinued(session.getHealingCase());
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

    /**
     * Highest existing number + 1, not count + 1: once a session has been soft-deleted the
     * count is lower than the highest number, and count + 1 would reuse a live number (which
     * the unique (healing_case_id, session_number) index now rejects).
     */
    private static int nextSessionNumber(List<HealingSession> existing) {
        return existing.stream()
                .map(HealingSession::getSessionNumber)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0) + 1;
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

    @Auditable(action = "GET_ALL_SCANNED_CHAKRA")
    @Transactional(readOnly = true)
    public Set<String> getAllScannedChakra(UUID caseId) {
        return sessionRepository.findAllScannedChakras(caseId);
    }
}