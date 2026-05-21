package com.pranicdoc.cph.healing.service;

import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.enums.SessionStatus;
import com.pranicdoc.cph.common.enums.ScanType;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.exception.SessionIncompleteException;
import com.pranicdoc.cph.healing.dto.CreateSessionRequest;
import com.pranicdoc.cph.healing.dto.SessionResponse;
import com.pranicdoc.cph.healing.mapper.HealingSessionMapper;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.model.HealingSession;
import com.pranicdoc.cph.healing.repository.SessionRepository;
import com.pranicdoc.cph.masterdata.repository.ChakraCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.PranaColorCatalogRepository;
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
    private final HealingSessionMapper mapper;
    private final ChakraCatalogRepository chakraCatalogRepository;
    private final PranaColorCatalogRepository pranaColorCatalogRepository;

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
        boolean hasPreScan = session.getScans().stream()
                .anyMatch(s -> s.getScanType() == ScanType.PRE);
        boolean hasPostScan = session.getScans().stream()
                .anyMatch(s -> s.getScanType() == ScanType.POST);
        boolean hasProtocol = !session.getProtocolEntries().isEmpty();
        if (!hasPreScan) throw new SessionIncompleteException("Pre-scan is required");
        if (!hasProtocol) throw new SessionIncompleteException("At least one protocol entry is required");
        if (!hasPostScan) throw new SessionIncompleteException("Post-scan is required");
        session.setStatus(SessionStatus.COMPLETED);
        session.setHealingCase(healingCase);
        healingCase.getSessions().add(session);
        return toResponse(sessionRepository.save(session));
    }

    private void validateMasterDataReferences(CreateSessionRequest request) {
        if (request.scans() != null) {
            request.scans().forEach(scan -> {
                if (scan.readings() != null) {
                    scan.readings().forEach(reading -> validateChakraId(reading.chakraId()));
                }
            });
        }

        if (request.protocolEntries() != null) {
            request.protocolEntries().forEach(entry -> {
                if (entry.chakraId() != null) {
                    validateChakraId(entry.chakraId());
                }
                if (entry.colors() != null) {
                    entry.colors().forEach(color -> validateColorId(color.colorId()));
                }
            });
        }
    }

    private void validateChakraId(UUID chakraId) {
        if (!chakraCatalogRepository.existsById(chakraId)) {
            throw new IllegalArgumentException("Invalid chakraId: " + chakraId);
        }
    }

    private void validateColorId(UUID colorId) {
        if (!pranaColorCatalogRepository.existsById(colorId)) {
            throw new IllegalArgumentException("Invalid colorId: " + colorId);
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
