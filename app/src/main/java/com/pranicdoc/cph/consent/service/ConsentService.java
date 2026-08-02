package com.pranicdoc.cph.consent.service;

import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.consent.dto.ConsentAcceptRequest;
import com.pranicdoc.cph.consent.dto.ConsentResponse;
import com.pranicdoc.cph.consent.dto.PolicyVersionResponse;
import com.pranicdoc.cph.consent.model.Consent;
import com.pranicdoc.cph.consent.model.ConsentType;
import com.pranicdoc.cph.consent.model.PolicyVersion;
import com.pranicdoc.cph.consent.repository.ConsentRepository;
import com.pranicdoc.cph.consent.repository.PolicyVersionRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ConsentService — the security-critical heart of Phase 30. Records
 * acceptances (capturing request metadata server-side, never from the
 * client body), withdraws consents (ownership-guarded, audit-logged,
 * non-destructive), computes outstanding required consents, and exposes
 * history + active policy versions.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ConsentService {

    private final ConsentRepository consentRepository;
    private final PolicyVersionRepository policyVersionRepository;
    private final SecurityContextUtil securityContextUtil;

    /**
     * Records an acceptance. Every field that could otherwise be spoofed by a
     * malicious caller (method/ipAddress/userAgent/language/acceptedBy) is
     * derived server-side — never trusted from {@code request} (CONSENT-05,
     * T-30-01). The first positional arg is the DTO (not the servlet request)
     * so {@code AuditAspect} serializes something meaningful into the audit
     * payload.
     */
    @Auditable(action = "ACCEPT_CONSENT")
    public ConsentResponse accept(ConsentAcceptRequest request, HttpServletRequest httpRequest) {
        UUID healerId = securityContextUtil.getCurrentHealerId();

        policyVersionRepository.findByConsentTypeAndPolicyVersion(request.consentType(), request.policyVersion())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PolicyVersion", request.consentType() + " v" + request.policyVersion()));

        String ipAddress = resolveClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        String language = httpRequest.getHeader("Accept-Language");

        Consent consent = Consent.builder()
                .subjectType("HEALER")
                .subjectId(healerId)
                .acceptedBy(healerId)
                .consentType(request.consentType())
                .policyVersion(request.policyVersion())
                .acceptedAt(Instant.now())
                .method("CLICKWRAP")
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .language(language)
                .build();

        return toResponse(consentRepository.save(consent));
    }

    /**
     * D-09: withdrawal is unconditional (no lockout/mandatory-block logic —
     * that is Phase 34's job) but ownership-guarded (T-30-02, BOLA/IDOR).
     * A mismatch throws 404 rather than 403 to avoid cross-subject
     * consent-existence enumeration. Never deletes the row.
     */
    @Auditable(action = "WITHDRAW_CONSENT")
    public ConsentResponse withdraw(UUID consentId) {
        Consent consent = consentRepository.findById(consentId)
                .orElseThrow(() -> new ResourceNotFoundException("Consent", consentId));

        if (!consent.getSubjectId().equals(securityContextUtil.getCurrentHealerId())) {
            throw new ResourceNotFoundException("Consent", consentId);
        }

        consent.setWithdrawnAt(Instant.now());
        return toResponse(consentRepository.save(consent));
    }

    /**
     * CONSENT-06 — the authenticated practitioner's full consent record set
     * (withdrawn/superseded included, no filtering). Resolves the subject
     * internally; never accepts a caller-supplied subjectId (T-30-07, BOLA).
     */
    @Transactional(readOnly = true)
    public List<ConsentResponse> history() {
        UUID healerId = securityContextUtil.getCurrentHealerId();
        return consentRepository.findBySubjectIdOrderByAcceptedAtDesc(healerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** CONSENT-07 — the current version of every consent type that has one. */
    @Transactional(readOnly = true)
    public List<PolicyVersionResponse> activePolicyVersions() {
        List<PolicyVersionResponse> active = new ArrayList<>();
        for (ConsentType type : ConsentType.values()) {
            policyVersionRepository.findTopByConsentTypeOrderByPolicyVersionDesc(type)
                    .ifPresent(current -> active.add(toPolicyResponse(current)));
        }
        return active;
    }

    /**
     * CONSENT-04 — the current mandatory policy versions the authenticated
     * practitioner has never accepted, accepted at a superseded version, or
     * withdrew (D-08 supersession: accepted.policyVersion < current.policyVersion).
     * Non-mandatory policies never gate. Resolves the subject internally
     * (T-30-07, BOLA — never accepts a caller-supplied subjectId).
     */
    @Transactional(readOnly = true)
    public List<PolicyVersionResponse> outstandingConsents() {
        UUID healerId = securityContextUtil.getCurrentHealerId();
        List<PolicyVersionResponse> outstanding = new ArrayList<>();

        for (ConsentType type : ConsentType.values()) {
            Optional<PolicyVersion> currentOpt = policyVersionRepository.findTopByConsentTypeOrderByPolicyVersionDesc(type);
            if (currentOpt.isEmpty() || !currentOpt.get().isMandatory()) {
                continue;
            }
            PolicyVersion current = currentOpt.get();

            Optional<Consent> acceptedOpt = consentRepository
                    .findTopBySubjectIdAndConsentTypeAndWithdrawnAtIsNullOrderByPolicyVersionDesc(healerId, type);

            boolean isOutstanding = acceptedOpt.isEmpty()
                    || acceptedOpt.get().getPolicyVersion() < current.getPolicyVersion();

            if (isOutstanding) {
                outstanding.add(toPolicyResponse(current));
            }
        }

        return outstanding;
    }

    /** Reuses RateLimitFilter's trusted-proxy-aware last-hop resolution. */
    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.isNotBlank(forwardedFor)) {
            String[] hops = forwardedFor.split(",");
            String lastHop = hops[hops.length - 1].trim();
            if (StringUtils.isNotBlank(lastHop)) {
                return lastHop;
            }
        }
        return request.getRemoteAddr();
    }

    private ConsentResponse toResponse(Consent c) {
        return new ConsentResponse(
                c.getId(),
                c.getSubjectId(),
                c.getConsentType(),
                c.getPolicyVersion(),
                c.getAcceptedAt(),
                c.getWithdrawnAt(),
                c.getMethod()
        );
    }

    private PolicyVersionResponse toPolicyResponse(PolicyVersion p) {
        return new PolicyVersionResponse(
                p.getId(),
                p.getConsentType(),
                p.getPolicyVersion(),
                p.isMandatory(),
                p.getEffectiveDate()
        );
    }
}
