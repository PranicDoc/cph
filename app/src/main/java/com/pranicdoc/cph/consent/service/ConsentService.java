package com.pranicdoc.cph.consent.service;

import com.pranicdoc.cph.auth.repository.HealerRepository;
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
import com.pranicdoc.cph.consent.repository.ConsentTypeRepository;
import com.pranicdoc.cph.consent.repository.PolicyVersionRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    private final ConsentTypeRepository consentTypeRepository;
    private final SecurityContextUtil securityContextUtil;
    private final HealerRepository healerRepository;

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
     * DOC-01/DOC-02/DOC-04/DOC-05 — the sole write path by which policy content
     * ever enters {@code policy_version} (D-01/D-02). {@code consentType} is
     * deliberately the FIRST parameter: {@code AuditAspect} serializes
     * {@code joinPoint.getArgs()[0]} as the audit payload, so keeping it first
     * avoids serializing the {@code MultipartFile} or the full document body
     * into {@code audit_log} (T-31-12). {@code publisherId} is resolved
     * server-side only via {@code securityContextUtil} (T-31-11) and is never
     * accepted as a method parameter. The {@code @CacheEvict} key must stay
     * byte-identical to {@code activePolicyVersions()}'s {@code @Cacheable}
     * key, or the cached active-policy list is never invalidated (D-10/D-11).
     */
    @Auditable(action = "PUBLISH_POLICY_VERSION")
    @CacheEvict(value = "policy.active-version", key = "'all'")
    public PolicyVersionResponse publish(String consentType, MultipartFile file, String content, Boolean mandatory) {
        consentTypeRepository.findByKeyAndActiveTrue(consentType)
                .orElseThrow(() -> new ResourceNotFoundException("ConsentType", consentType));

        boolean hasFile = file != null && !file.isEmpty();
        boolean hasContent = StringUtils.isNotBlank(content);

        if (hasFile == hasContent) {
            throw new IllegalArgumentException(hasFile
                    ? "Provide either pasted text or a file, not both."
                    : "Provide either pasted text or a file.");
        }

        String resolvedContent;
        if (hasFile) {
            try {
                resolvedContent = new String(file.getBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not read the uploaded file.");
            }
        } else {
            resolvedContent = content;
        }

        int nextVersion = policyVersionRepository.findTopByConsentTypeOrderByPolicyVersionDesc(consentType)
                .map(p -> p.getPolicyVersion() + 1)
                .orElse(1);

        UUID publisherId = securityContextUtil.getCurrentHealerId();

        PolicyVersion saved = policyVersionRepository.save(PolicyVersion.builder()
                .consentType(consentType)
                .policyVersion(nextVersion)
                .mandatory(mandatory == null || mandatory)
                .effectiveDate(Instant.now())
                .content(resolvedContent)
                .publisherId(publisherId)
                .build());

        return toPolicyResponse(saved);
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
    @Cacheable(value = "policy.active-version", key = "'all'", sync = true)
    @Transactional(readOnly = true)
    public List<PolicyVersionResponse> activePolicyVersions() {
        List<PolicyVersionResponse> active = new ArrayList<>();
        for (ConsentType type : consentTypeRepository.findAllByActiveTrueOrderByIdAsc()) {
            String key = type.getKey();
            policyVersionRepository.findTopByConsentTypeOrderByPolicyVersionDesc(key)
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
     *
     * <p>Mandatoriness is type-level (CONSENT-10, Pitfall 1): the gate reads
     * {@code ConsentType.isMandatory()} from the registry row, never
     * {@code PolicyVersion.mandatory} — the version-level flag is vestigial
     * for gating purposes as of this method. The loop only ever iterates
     * the registry's active rows, so a deactivated type is excluded for
     * free; no separate {@code active} check is needed here.
     */
    @Transactional(readOnly = true)
    public List<PolicyVersionResponse> outstandingConsents() {
        UUID healerId = securityContextUtil.getCurrentHealerId();
        List<PolicyVersionResponse> outstanding = new ArrayList<>();

        for (ConsentType type : consentTypeRepository.findAllByActiveTrueOrderByIdAsc()) {
            String key = type.getKey();
            Optional<PolicyVersion> currentOpt = policyVersionRepository.findTopByConsentTypeOrderByPolicyVersionDesc(key);
            if (currentOpt.isEmpty() || !type.isMandatory()) {
                continue;
            }
            PolicyVersion current = currentOpt.get();

            Optional<Consent> acceptedOpt = consentRepository
                    .findTopBySubjectIdAndConsentTypeAndWithdrawnAtIsNullOrderByPolicyVersionDesc(healerId, key);

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
        UUID publisherId = p.getPublisherId();
        String publisherName = publisherId == null ? null : healerRepository.findFullNameById(publisherId);
        return new PolicyVersionResponse(
                p.getId(),
                p.getConsentType(),
                p.getPolicyVersion(),
                p.isMandatory(),
                p.getEffectiveDate(),
                p.getContent(),
                publisherId,
                publisherName
        );
    }
}
