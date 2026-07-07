package com.pranicdoc.cph.auth.service.impl;

import com.pranicdoc.cph.auth.dto.AcceptInviteRequest;
import com.pranicdoc.cph.auth.dto.HealerResponse;
import com.pranicdoc.cph.auth.dto.InviteResponse;
import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.model.HealerInviteLink;
import com.pranicdoc.cph.auth.repository.HealerInviteLinkRepository;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.clinic.model.Clinic;
import com.pranicdoc.cph.clinic.repository.ClinicRepository;
import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.common.enums.Role;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.exception.TokenExpiredException;
import com.pranicdoc.cph.common.security.CookieUtil;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.payments.dto.OwnerType;
import com.pranicdoc.cph.payments.service.CreditService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Invite lifecycle service (ADMIN-01): create (with dedup upsert), resend
 * (token rotation), revoke (status flip), and accept (healer activation).
 * Mirrors AuthService's structure/conventions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class InviteService {

    private final HealerInviteLinkRepository inviteRepository;
    private final HealerRepository healerRepository;
    private final ClinicRepository clinicRepository;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CookieUtil cookieUtil;
    private final CreditService creditService;

    @Value("${app.invite.url}")
    private String inviteUrl;

    @Value("${app.invite.token-expiry-hours:72}")
    private int inviteExpiryHours;

    // ─── Create ─────────────────────────────────────────────────

    @Auditable(action = "CREATE_INVITE")
    public InviteResponse createInvite(String email, Role role, UUID clinicId, UUID invitedByHealerId) {
        // Global check: Healer.email has no per-clinic scoping anywhere else in the system —
        // AuthService.login()/HealerServiceImpl.findHealerByEmail() both look up by email alone,
        // so a per-clinic dedup here would let the same email activate in two different clinics
        // and break login for both (ambiguous result on the global lookup).
        if (healerRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("A healer with this email already exists");
        }

        HealerInviteLink invite = inviteRepository
                .findByEmailAndClinicIdAndStatus(email, clinicId, HealerInviteLink.Status.PENDING)
                .orElse(null);

        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(inviteExpiryHours, ChronoUnit.HOURS);

        if (invite != null) {
            // Rotate the existing PENDING row in place — SC-1: no duplicate active token.
            invite.setToken(token);
            invite.setRole(role);
            invite.setExpiresAt(expiresAt);
        } else {
            invite = HealerInviteLink.builder()
                    .token(token)
                    .email(email)
                    .role(role)
                    .clinicId(clinicId)
                    .invitedByHealerId(invitedByHealerId)
                    .status(HealerInviteLink.Status.PENDING)
                    .expiresAt(expiresAt)
                    .build();
        }

        HealerInviteLink saved = inviteRepository.save(invite);
        sendInviteEmail(saved.getEmail(), saved.getToken());

        return toResponse(saved);
    }

    // ─── Self-Signup Request (SELFREG-01) ──────────────────────

    private static final String SELF_SIGNUP_GENERIC_MESSAGE =
            "If that email is eligible, a verification link has been sent.";

    /**
     * Public, unauthenticated self-signup request handler (D-08). NEVER throws for a
     * collision — an active account, a pending admin invite (D-10), or a concurrent
     * insert race (D-09's partial unique index) all resolve to the exact same generic
     * response, so a caller cannot distinguish any of these cases from a brand-new email.
     */
    @Auditable(action = "REQUEST_SELF_SIGNUP")
    public ApiResponse<String> requestSelfSignup(String email) {
        ApiResponse<String> genericResponse = new ApiResponse<>(true, SELF_SIGNUP_GENERIC_MESSAGE, null);

        if (healerRepository.existsByEmail(email)) {
            // An active account already exists — no save, no email, no enumeration signal.
            return genericResponse;
        }

        if (inviteRepository.existsByEmailAndSourceAndStatus(
                email, HealerInviteLink.Source.ADMIN_INVITE, HealerInviteLink.Status.PENDING)) {
            // D-10: a pending admin invite wins — self-signup silently no-ops.
            return genericResponse;
        }

        try {
            HealerInviteLink invite = inviteRepository
                    .findByEmailAndSourceAndStatus(email, HealerInviteLink.Source.SELF_SIGNUP, HealerInviteLink.Status.PENDING)
                    .orElse(null);

            String token = UUID.randomUUID().toString();
            Instant expiresAt = Instant.now().plus(inviteExpiryHours, ChronoUnit.HOURS);

            if (invite != null) {
                // D-09: rotate the existing PENDING self-signup row in place — no duplicate row.
                invite.setToken(token);
                invite.setExpiresAt(expiresAt);
            } else {
                invite = HealerInviteLink.builder()
                        .token(token)
                        .email(email)
                        .role(Role.HEALER)
                        .source(HealerInviteLink.Source.SELF_SIGNUP)
                        .status(HealerInviteLink.Status.PENDING)
                        .expiresAt(expiresAt)
                        .build();
            }

            HealerInviteLink saved = inviteRepository.save(invite);
            sendSelfSignupEmail(saved.getEmail(), saved.getToken());
        } catch (DataIntegrityViolationException ex) {
            // Two concurrent first-time requests both tried to insert — the partial unique
            // index (D-09) rejected the loser. Still no enumeration signal on the response.
            log.warn("Self-signup insert race for email {}: {}", email, ex.getMessage());
        }

        return genericResponse;
    }

    // ─── Resend ─────────────────────────────────────────────────

    @Auditable(action = "RESEND_INVITE")
    public InviteResponse resend(UUID inviteId, UUID callerClinicFilter) {
        HealerInviteLink invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new IllegalArgumentException("Invite not found"));

        assertClinicAccess(invite, callerClinicFilter);

        if (invite.getStatus() != HealerInviteLink.Status.PENDING) {
            throw new IllegalArgumentException("Only pending invites can be resent");
        }

        // Old token string now matches no row's token column — naturally invalidated (D-06).
        invite.setToken(UUID.randomUUID().toString());
        invite.setExpiresAt(Instant.now().plus(inviteExpiryHours, ChronoUnit.HOURS));
        HealerInviteLink saved = inviteRepository.save(invite);

        sendInviteEmail(saved.getEmail(), saved.getToken());

        return toResponse(saved);
    }

    // ─── Revoke ─────────────────────────────────────────────────

    @Auditable(action = "REVOKE_INVITE")
    public InviteResponse revoke(UUID inviteId, UUID callerClinicFilter) {
        HealerInviteLink invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new IllegalArgumentException("Invite not found"));

        assertClinicAccess(invite, callerClinicFilter);

        if (invite.getStatus() != HealerInviteLink.Status.PENDING) {
            throw new IllegalArgumentException("Only pending invites can be revoked");
        }

        invite.setStatus(HealerInviteLink.Status.REVOKED);
        HealerInviteLink saved = inviteRepository.save(invite);

        return toResponse(saved);
    }

    // ─── Accept ─────────────────────────────────────────────────

    @Auditable(action = "ACCEPT_INVITE")
    public HealerResponse acceptInvite(String token, AcceptInviteRequest request, HttpServletResponse response) {
        HealerInviteLink invite = inviteRepository
                .findByTokenAndStatusAndExpiresAtAfter(token, HealerInviteLink.Status.PENDING, Instant.now())
                .orElseThrow(() -> new TokenExpiredException("This invite link is invalid, expired, or already used."));

        // Conditional flip BEFORE healer creation, inside the same @Transactional —
        // a concurrent accept that already won the race returns 0 rows here (T-22-04/05).
        int updated = inviteRepository.markAccepted(invite.getId(), Instant.now());
        if (updated == 0) {
            throw new TokenExpiredException("This invite link is invalid, expired, or already used.");
        }

        Clinic clinic;
        if (invite.getSource() == HealerInviteLink.Source.SELF_SIGNUP) {
            // D-05/D-06: auto-provision a personal one-person clinic instead of joining one.
            clinic = createSoloClinic(request.fullName());
        } else {
            clinic = clinicRepository.findById(invite.getClinicId())
                    .orElseThrow(() -> new ResourceNotFoundException("Clinic", invite.getClinicId()));
        }

        if (healerRepository.existsByEmail(invite.getEmail())) {
            // An account already exists (any clinic) — the invite is effectively already consumed.
            throw new TokenExpiredException("This invite link is invalid, expired, or already used.");
        }

        // D-04: never trust the token's role for self-signup — always plain HEALER.
        Role role = invite.getSource() == HealerInviteLink.Source.SELF_SIGNUP ? Role.HEALER : invite.getRole();

        Healer healer = Healer.builder()
                .fullName(request.fullName())
                .email(invite.getEmail())
                .phone(request.phone())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(role)
                .active(true)
                .build();
        healer.setClinicId(clinic.getId());
        Healer saved = healerRepository.save(healer);

        // T-22.1-09: a creditService outage must never fail account activation.
        try {
            creditService.createAccount(OwnerType.HEALER, saved.getId());
        } catch (Exception e) {
            log.warn("CREDIT_ACCOUNT_PROVISION_FAILED healerId={} reason={} (account activated; manual provisioning may be needed)",
                    saved.getId(), e.getMessage());
        }

        setAuthCookies(response, saved, clinic.getCode());

        return toHealerResponse(saved, clinic.getCode());
    }

    // ─── Solo Clinic Auto-Provisioning (D-05/D-06/D-07) ────────

    private static final String SOLO_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I (D-06)
    private static final int SOLO_CODE_SUFFIX_LENGTH = 4;
    private static final int SOLO_CODE_MAX_ATTEMPTS = 10;
    private static final SecureRandom SOLO_CODE_RANDOM = new SecureRandom();

    private Clinic createSoloClinic(String fullName) {
        String code = null;
        for (int attempt = 0; attempt < SOLO_CODE_MAX_ATTEMPTS; attempt++) {
            String candidate = "SOLO-" + randomSoloCodeSuffix();
            if (!clinicRepository.existsByCode(candidate)) {
                code = candidate;
                break;
            }
        }
        if (code == null) {
            throw new IllegalStateException("Unable to generate a unique solo clinic code");
        }

        Clinic clinic = Clinic.builder()
                .code(code)
                .name(fullName + "'s Practice")
                .active(true)
                .build();
        Clinic saved = clinicRepository.save(clinic);

        // T-22.1-09: replicate ClinicService.create()'s non-fatal provisioning idiom —
        // a creditService outage must never roll back the created solo clinic.
        try {
            creditService.createAccount(OwnerType.CLINIC, saved.getId());
        } catch (Exception e) {
            log.warn("CREDIT_ACCOUNT_PROVISION_FAILED clinicId={} reason={} (clinic registration completed; manual provisioning may be needed)",
                    saved.getId(), e.getMessage());
        }

        return saved;
    }

    private String randomSoloCodeSuffix() {
        StringBuilder sb = new StringBuilder(SOLO_CODE_SUFFIX_LENGTH);
        for (int i = 0; i < SOLO_CODE_SUFFIX_LENGTH; i++) {
            sb.append(SOLO_CODE_ALPHABET.charAt(SOLO_CODE_RANDOM.nextInt(SOLO_CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    // ─── List ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<InviteResponse> list(UUID clinicFilter, Pageable pageable) {
        Page<HealerInviteLink> page = clinicFilter != null
                ? inviteRepository.findByClinicId(clinicFilter, pageable)
                : inviteRepository.findAll(pageable);
        return page.map(InviteService::toResponse);
    }

    // ─── Internal helpers ───────────────────────────────────────

    private void setAuthCookies(HttpServletResponse response, Healer healer, String clinicCode) {
        String accessToken = jwtService.generateAccessToken(
                healer.getId(), healer.getClinicId(), clinicCode, healer.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(
                healer.getId(), healer.getClinicId(), clinicCode, healer.getRole().name());
        cookieUtil.setAuthCookies(response, accessToken, refreshToken);
    }

    private HealerResponse toHealerResponse(Healer h, String clinicCode) {
        return new HealerResponse(
                h.getId(), h.getFullName(), h.getEmail(), h.getPhone(),
                h.getRole(), h.getHealingLevel(), h.isActive(),
                h.getClinicId(), clinicCode, h.getCreatedAt(),
                h.getPasswordHash() != null, h.getProviderUserId() != null
        );
    }

    private void assertClinicAccess(HealerInviteLink invite, UUID callerClinicFilter) {
        if (callerClinicFilter != null && !callerClinicFilter.equals(invite.getClinicId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invite belongs to a different clinic");
        }
    }

    private void sendInviteEmail(String email, String token) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("You're invited to PranicDoc");
            message.setText(
                    "You've been invited to join PranicDoc.\n\n"
                            + "Click the link below to set up your account:\n"
                            + inviteUrl + "?token=" + token + "\n\n"
                            + "This link expires in 72 hours.");
            mailSender.send(message);
        } catch (MailException ex) {
            // Don't let SMTP downtime block invite creation — the admin has Resend (D-06/Pitfall 3).
            log.warn("Failed to send invite email to {}: {}", email, ex.getMessage());
        }
    }

    private void sendSelfSignupEmail(String email, String token) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("Verify your email to complete PranicDoc registration");
            message.setText(
                    "You requested to register as a healer on PranicDoc.\n\n"
                            + "Click the link below to verify your email and complete registration:\n"
                            + inviteUrl + "?token=" + token + "\n\n"
                            + "This link expires in 72 hours.");
            mailSender.send(message);
        } catch (MailException ex) {
            // Don't let SMTP downtime block the request — resubmitting /signup rotates the token.
            log.warn("Failed to send self-signup verification email to {}: {}", email, ex.getMessage());
        }
    }

    static InviteResponse toResponse(HealerInviteLink invite) {
        String status = computeStatus(invite);
        return new InviteResponse(
                invite.getId(),
                invite.getEmail(),
                invite.getRole().name(),
                invite.getClinicId(),
                status,
                invite.getCreatedAt(),
                invite.getExpiresAt()
        );
    }

    private static String computeStatus(HealerInviteLink invite) {
        if (invite.getStatus() == HealerInviteLink.Status.REVOKED) {
            return "REVOKED";
        }
        if (invite.getStatus() == HealerInviteLink.Status.ACCEPTED) {
            return "ACCEPTED";
        }
        if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(Instant.now())) {
            return "EXPIRED";
        }
        return "PENDING";
    }
}
