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
import com.pranicdoc.cph.notification.channel.NotificationMessage;
import com.pranicdoc.cph.notification.service.NotificationSender;
import com.pranicdoc.cph.payments.dto.OwnerType;
import com.pranicdoc.cph.payments.service.CreditService;
import com.pranicdoc.cph.rbac.baseline.BaselineRoleAssigner;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CookieUtil cookieUtil;
    private final CreditService creditService;
    private final BaselineRoleAssigner baselineRoleAssigner;
    private final NotificationSender notificationSender;

    @Value("${app.invite.url}")
    private String inviteUrl;

    @Value("${app.invite.token-expiry-hours:72}")
    private int inviteExpiryHours;

    /**
     * Fail startup rather than silently ship broken invite emails. `@Value` alone
     * doesn't catch this: an env var explicitly set to an empty string (as opposed
     * to unset) resolves fine as far as Spring's placeholder resolver is concerned —
     * it only errors when the key is missing from every property source. A blank
     * inviteUrl produced exactly this kind of silent failure before (invite emails
     * "succeeded" with a broken activation link), so refuse to come up at all rather
     * than repeat it. (The sender address is validated by EmailChannel.)
     */
    @PostConstruct
    void validateMailConfig() {
        if (StringUtils.isBlank(inviteUrl)) {
            throw new IllegalStateException(
                    "app.invite.url must be set to a non-blank value — invite/self-signup "
                            + "emails need a real activation URL. Refusing to start.");
        }
    }

    /**
     * Single-sourced HEALER-only retirement invariant (Phase 41 / MIGRATE-04 / D-05, D-06,
     * WR-01). {@code createInvite}, {@code requestSelfSignup} and {@code acceptInvite} all
     * call this immediately before they persist, so the three write paths share one guard
     * and one message instead of drifting apart. Tripping it means a stale pre-retirement
     * row or a regression, never a client error — hence {@link IllegalStateException}
     * (rendered HTTP 500 via the GlobalExceptionHandler catch-all, PD-41-01).
     */
    private static void assertInviteMintsHealer(Role role) {
        if (role != Role.HEALER) {
            throw new IllegalStateException(
                    "Invite must mint a plain HEALER — the retired non-HEALER invite tiers were "
                            + "removed in Phase 41; grant elevated capability via a separate role "
                            + "assignment, never at invite time. Got: " + role);
        }
    }

    // ─── Create ─────────────────────────────────────────────────

    /**
     * Creates a new admin invite (or rotates the caller's existing PENDING row).
     * Every invite mints a plain {@link Role#HEALER}; elevated capability is granted
     * afterward via the Phase 37/38 role-assignment path, never at invite time
     * (Phase 41 / MIGRATE-04, D-06). A pre-persist invariant fails closed if anything
     * other than a plain healer is about to be saved.
     */
    @Auditable(action = "CREATE_INVITE")
    public InviteResponse createInvite(String email, UUID invitedByHealerId) {
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
            invite.setRole(Role.HEALER);
            invite.setExpiresAt(expiresAt);
        } else {
            invite = HealerInviteLink.builder()
                    .token(token)
                    .email(email)
                    .role(Role.HEALER)
                    .invitedByHealerId(invitedByHealerId)
                    .status(HealerInviteLink.Status.PENDING)
                    .expiresAt(expiresAt)
                    .build();
        }

        // Retirement invariant (Phase 41 / MIGRATE-04 / D-06), single-sourced in
        // assertInviteMintsHealer. This call is structurally unreachable: the rotate
        // branch runs invite.setRole(Role.HEALER) and the new-row branch hardcodes
        // .role(Role.HEALER) above, so getRole() is always HEALER here. Kept as
        // documented defence-in-depth — a future refactor that stops pinning the role
        // is still caught — and a reader should not treat this line as load-bearing.
        assertInviteMintsHealer(invite.getRole());

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

            // Retirement invariant (Phase 41 / MIGRATE-04 / WR-01), single-sourced in
            // assertInviteMintsHealer. The rotate branch does not touch role, so a
            // self-signup row persisted before the retirement is the realistic way a
            // non-HEALER value could still reach this save.
            assertInviteMintsHealer(invite.getRole());

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

        // Retirement invariant (Phase 41 / MIGRATE-04 / D-05, D-06), single-sourced in
        // assertInviteMintsHealer. NOTE (CR-01): this does NOT fail closed for every
        // non-HEALER value. healer_invite_links.role is @Enumerated(STRING) with no DB
        // CHECK, so a row carrying one of the retired non-HEALER tiers throws during ORM
        // hydration at findByTokenAndStatusAndExpiresAtAfter above — before this line is
        // reached. The only non-HEALER value this guard can actually observe is SUPADM
        // (still a live enum constant); for the retired tiers the real remediation is
        // V36's data cleanup plus the extended D-03 preflight, not this check.
        assertInviteMintsHealer(role);

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

        // Give the new healer the baseline capability grants so the Phase-42 gates
        // don't lock them out of the core app. Fail-soft (same posture as credit
        // provisioning above) — the assigner swallows its own errors.
        baselineRoleAssigner.assignTo(saved.getId());

        setAuthCookies(response, saved);

        // First activation through the registration link: welcome the healer once the account
        // is committed. Async and fail-soft, so it can never delay or undo the activation.
        notificationSender.sendAfterCommit(
                AccountMessages.welcome(saved.getEmail(), saved.getFullName(), inviteUrl));

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
        return notificationSender.sendNow(NotificationMessage.email(email,
                "You're invited to HealingARK",
                "You've been invited to join HealingARK.\n\n"
                        + "Click the link below to set up your account:\n"
                        + inviteUrl + "?token=" + token + "\n\n"
                        + "This link expires in 72 hours."));
    }

    // Don't let SMTP downtime block the request - resubmitting /signup rotates the token.
    private boolean sendSelfSignupEmail(String email, String token) {
        return notificationSender.sendNow(NotificationMessage.email(email,
                "Verify your email to complete HealingARK registration",
                "You requested to register as a healer on HealingARK.\n\n"
                        + "Click the link below to verify your email and complete registration:\n"
                        + inviteUrl + "?token=" + token + "\n\n"
                        + "This link expires in 72 hours."));
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
