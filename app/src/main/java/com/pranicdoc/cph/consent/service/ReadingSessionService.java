package com.pranicdoc.cph.consent.service;

import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.consent.dto.ReadingSessionCloseRequest;
import com.pranicdoc.cph.consent.dto.ReadingSessionOpenRequest;
import com.pranicdoc.cph.consent.dto.ReadingSessionResponse;
import com.pranicdoc.cph.consent.model.ReadingSession;
import com.pranicdoc.cph.consent.repository.PolicyVersionRepository;
import com.pranicdoc.cph.consent.repository.ReadingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * ReadingSessionService — the business logic behind READ-01 (a document only
 * counts as read once actually opened) and READ-02 (open timestamp, close
 * timestamp, and duration recorded per practitioner per document version).
 *
 * <p>{@code openedAt}, {@code closedAt}, and {@code durationSeconds} are
 * ALWAYS derived from {@code Instant.now()} / {@code Duration.between} —
 * never accepted from the client (T-33-01). {@code subjectId} comes only
 * from {@link SecurityContextUtil#getCurrentHealerId()} (T-33-02) — never
 * from the request body. {@code scrollCompleted} is the one legitimately
 * client-reported field on this class; it is a claim, not a server-verified
 * fact, and Phase 34 decides how much weight to give it.
 *
 * <p>Per 33-RESEARCH.md Pitfall 4, this class never touches {@code Consent}
 * — a reading session is not an acceptance, and this service deliberately
 * injects no repository or service for that entity.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ReadingSessionService {

    private final ReadingSessionRepository readingSessionRepository;
    private final PolicyVersionRepository policyVersionRepository;
    private final SecurityContextUtil securityContextUtil;

    /**
     * Opens a reading session against a published document version. Rejects
     * unknown/unpublished {@code (consentType, policyVersion)} pairs before
     * any insert (T-33-08). The subject is resolved server-side only.
     */
    @Auditable(action = "OPEN_READING_SESSION")
    public ReadingSessionResponse open(ReadingSessionOpenRequest request) {
        UUID healerId = securityContextUtil.getCurrentHealerId();

        policyVersionRepository.findByConsentTypeAndPolicyVersion(request.consentType(), request.policyVersion())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PolicyVersion", request.consentType() + " v" + request.policyVersion()));

        ReadingSession session = ReadingSession.builder()
                .subjectId(healerId)
                .consentType(request.consentType())
                .policyVersion(request.policyVersion())
                .openedAt(Instant.now())
                .scrollCompleted(false)
                .build();

        return toResponse(readingSessionRepository.save(session));
    }

    /**
     * Closes a reading session, stamping a server-clock {@code closedAt} and
     * computing {@code durationSeconds} from the server-recorded
     * {@code openedAt} (T-33-01). Ownership-guarded (T-33-05, BOLA/IDOR) —
     * a mismatch throws 404, not 403, to avoid cross-subject session-existence
     * enumeration. Idempotent: a session already closed returns its existing
     * values unchanged and never saves again, so a late-arriving duplicate
     * close (unmount + sendBeacon both firing) never overwrites a good record.
     */
    @Auditable(action = "CLOSE_READING_SESSION")
    public ReadingSessionResponse close(UUID id, ReadingSessionCloseRequest request) {
        ReadingSession session = readingSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReadingSession", id));

        if (!session.getSubjectId().equals(securityContextUtil.getCurrentHealerId())) {
            throw new ResourceNotFoundException("ReadingSession", id);
        }

        if (session.getClosedAt() != null) {
            return toResponse(session);
        }

        Instant closedAt = Instant.now();
        session.setClosedAt(closedAt);
        session.setDurationSeconds(Duration.between(session.getOpenedAt(), closedAt).getSeconds());
        session.setScrollCompleted(request != null && Boolean.TRUE.equals(request.scrollCompleted()));

        return toResponse(readingSessionRepository.save(session));
    }

    private ReadingSessionResponse toResponse(ReadingSession s) {
        return new ReadingSessionResponse(
                s.getId(),
                s.getSubjectId(),
                s.getConsentType(),
                s.getPolicyVersion(),
                s.getOpenedAt(),
                s.getClosedAt(),
                s.getDurationSeconds(),
                s.isScrollCompleted()
        );
    }
}
