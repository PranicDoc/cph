package com.pranicdoc.cph.auth.service.impl;

import com.pranicdoc.cph.auth.dto.AcceptInviteRequest;
import com.pranicdoc.cph.auth.dto.HealerResponse;
import com.pranicdoc.cph.auth.dto.InviteResponse;
import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.model.HealerInviteLink;
import com.pranicdoc.cph.auth.repository.HealerInviteLinkRepository;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.common.enums.Role;
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
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CookieUtil cookieUtil;
    private final CreditService creditService;

    @Value("${app.invite.url}")
    private String inviteUrl;

    @Value("${app.invite.token-expiry-hours:72}")
    private int inviteExpiryHours;

    // JavaMail has no implicit From — without setFrom() it synthesizes
    // <os-user>@<container-hostname> as the envelope sender, which most SMTP
    // relays (correctly) reject. Every outgoing message must set this explicitly.
    @Value("${spring.mail.username}")
    private String mailFromAddress;

    // ─── Create ─────────────────────────────────────────────────

    @Auditable(action = "CREATE_INVITE")
    public InviteResponse createInvite(String email, Role role, UUID invitedByHealerId) {
        // Global check: Healer.email has no per-clinic scoping anywhere else in the system —
        // AuthService.login()/HealerServiceImpl.findHealerByEmail() both look up by email alone,
        // so a per-clinic dedup here would let the same email activate in two different clinics
        // and break login for both (ambiguous result on the global lookup).
        if (healerRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("A healer with this email already exists");
        }

        HealerInviteLink invite = inviteRepository
                .findByEmailAndStatus(email, HealerInviteLink.Status.PENDING)
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
                    .invitedByHealerId(invitedByHealerId)
                    .status(HealerInviteLink.Status.PENDING)
                    .expiresAt(expiresAt)
                    .build();
        }

        HealerInviteLink saved = inviteRepository.save(invite);
        boolean emailSent = sendInviteEmail(saved.getEmail(), saved.getToken());

        return toResponse(saved, emailSent);
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
    public InviteResponse resend(UUID inviteId) {
        HealerInviteLink invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new IllegalArgumentException("Invite not found"));

        if (invite.getStatus() != HealerInviteLink.Status.PENDING) {
            throw new IllegalArgumentException("Only pending invites can be resent");
        }

        // Old token string now matches no row's token column — naturally invalidated (D-06).
        invite.setToken(UUID.randomUUID().toString());
        invite.setExpiresAt(Instant.now().plus(inviteExpiryHours, ChronoUnit.HOURS));
        HealerInviteLink saved = inviteRepository.save(invite);

        boolean emailSent = sendInviteEmail(saved.getEmail(), saved.getToken());

        return toResponse(saved, emailSent);
    }

    // ─── Revoke ─────────────────────────────────────────────────

    @Auditable(action = "REVOKE_INVITE")
    public InviteResponse revoke(UUID inviteId) {
        HealerInviteLink invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new IllegalArgumentException("Invite not found"));

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

        if (healerRepository.existsByEmail(invite.getEmail())) {
            // An account already exists — the invite is effectively already consumed.
            throw new TokenExpiredException("This invite link is invalid, expired, or already used.");
        }

        // D-04: never trust the token's role for self-signup — always plain HEALER.
        Role role = invite.getSource() == HealerInviteLink.Source.SELF_SIGNUP ? Role.HEALER : invite.getRole();

        // Solo-healer world (CUTOVER-05): no clinic to create or join — the healer IS the tenant.
        Healer healer = Healer.builder()
                .fullName(request.fullName())
                .email(invite.getEmail())
                .phone(request.phone())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(role)
                .active(true)
                .build();
        Healer saved = healerRepository.save(healer);

        // T-22.1-09: a creditService outage must never fail account activation.
        try {
            creditService.createAccount(OwnerType.HEALER, saved.getId());
        } catch (Exception e) {
            log.warn("CREDIT_ACCOUNT_PROVISION_FAILED healerId={} reason={} (account activated; manual provisioning may be needed)",
                    saved.getId(), e.getMessage());
        }

        setAuthCookies(response, saved);

        return toHealerResponse(saved);
    }

    // ─── List ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<InviteResponse> list(Pageable pageable) {
        return inviteRepository.findAll(pageable).map(InviteService::toResponse);
    }

    // ─── Internal helpers ───────────────────────────────────────

    private void setAuthCookies(HttpServletResponse response, Healer healer) {
        String accessToken = jwtService.generateAccessToken(healer.getId(), healer.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(healer.getId(), healer.getRole().name());
        cookieUtil.setAuthCookies(response, accessToken, refreshToken);
    }

    private HealerResponse toHealerResponse(Healer h) {
        return new HealerResponse(
                h.getId(), h.getFullName(), h.getEmail(), h.getPhone(),
                h.getRole(), h.getHealingLevel(), h.isActive(), h.getCreatedAt(),
                h.getPasswordHash() != null, h.getProviderUserId() != null
        );
    }

    /**
     * @return true if the email was handed off to the SMTP server successfully,
     * false if it failed (logged, never thrown — a mail outage must never block
     * invite creation, the admin has Resend).
     */
    private boolean sendInviteEmail(String email, String token) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFromAddress);
            message.setTo(email);
            message.setSubject("You're invited to PranicDoc");
            message.setText(
                    "You've been invited to join PranicDoc.\n\n"
                            + "Click the link below to set up your account:\n"
                            + inviteUrl + "?token=" + token + "\n\n"
                            + "This link expires in 72 hours.");
            mailSender.send(message);
            return true;
        } catch (MailException ex) {
            // Don't let SMTP downtime block invite creation — the admin has Resend (D-06/Pitfall 3).
            log.warn("Failed to send invite email to {}: {}", email, ex.getMessage());
            return false;
        }
    }

    private boolean sendSelfSignupEmail(String email, String token) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFromAddress);
            message.setTo(email);
            message.setSubject("Verify your email to complete PranicDoc registration");
            message.setText(
                    "You requested to register as a healer on PranicDoc.\n\n"
                            + "Click the link below to verify your email and complete registration:\n"
                            + inviteUrl + "?token=" + token + "\n\n"
                            + "This link expires in 72 hours.");
            mailSender.send(message);
            return true;
        } catch (MailException ex) {
            // Don't let SMTP downtime block the request — resubmitting /signup rotates the token.
            log.warn("Failed to send self-signup verification email to {}: {}", email, ex.getMessage());
            return false;
        }
    }

    static InviteResponse toResponse(HealerInviteLink invite) {
        // Used by list()/revoke() where no fresh send just happened — true is the
        // neutral default (we don't retroactively know past delivery outcomes).
        return toResponse(invite, true);
    }

    static InviteResponse toResponse(HealerInviteLink invite, boolean emailSent) {
        String status = computeStatus(invite);
        return new InviteResponse(
                invite.getId(),
                invite.getEmail(),
                invite.getRole().name(),
                status,
                invite.getCreatedAt(),
                invite.getExpiresAt(),
                emailSent
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
