package com.pranicdoc.cph.healing.service;

import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.enums.SessionStatus;
import com.pranicdoc.cph.common.enums.ScanType;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.exception.SessionIncompleteException;
import com.pranicdoc.cph.healing.dto.SessionResponse;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.model.HealingSession;
import com.pranicdoc.cph.healing.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SessionService {

    private final SessionRepository sessionRepository;
    private final CaseService caseService;

    @Auditable(action = "CREATE_SESSION")
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
     * Complete a session — validates all required steps are present.
     */
    @Auditable(action = "COMPLETE_SESSION")
    public SessionResponse complete(UUID sessionId) {
        HealingSession session = findOrThrow(sessionId);

        // Validate completeness
        boolean hasPreScan = session.getScans().stream()
                .anyMatch(s -> s.getScanType() == ScanType.PRE);
        boolean hasPostScan = session.getScans().stream()
                .anyMatch(s -> s.getScanType() == ScanType.POST);
        boolean hasProtocol = !session.getProtocolEntries().isEmpty();

        if (!hasPreScan) throw new SessionIncompleteException("Pre-scan is required");
        if (!hasProtocol) throw new SessionIncompleteException("At least one protocol entry is required");
        if (!hasPostScan) throw new SessionIncompleteException("Post-scan is required");

        session.setEndTime(LocalTime.now());
        session.setStatus(SessionStatus.COMPLETED);
        return toResponse(sessionRepository.save(session));
    }

    public HealingSession findOrThrow(UUID id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("HealingSession", id));
    }

    private SessionResponse toResponse(HealingSession s) {
        return new SessionResponse(
                s.getId(), s.getHealingCase().getId(),
                s.getSessionNumber(), s.getSessionDate(),
                s.getStartTime(), s.getEndTime(), s.getStatus()
        );
    }
}
