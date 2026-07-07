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

        Clinic clinic = clinicRepository.findById(invite.getClinicId())
                .orElseThrow(() -> new ResourceNotFoundException("Clinic", invite.getClinicId()));

        if (healerRepository.existsByEmail(invite.getEmail())) {
            // An account already exists (any clinic) — the invite is effectively already consumed.
            throw new TokenExpiredException("This invite link is invalid, expired, or already used.");
        }

        Healer healer = Healer.builder()
                .fullName(request.fullName())
                .email(invite.getEmail())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(invite.getRole())
                .active(true)
                .build();
        healer.setClinicId(invite.getClinicId());
        Healer saved = healerRepository.save(healer);

        creditService.createAccount(OwnerType.HEALER, saved.getId());

        setAuthCookies(response, saved, clinic.getCode());

        return toHealerResponse(saved, clinic.getCode());
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
