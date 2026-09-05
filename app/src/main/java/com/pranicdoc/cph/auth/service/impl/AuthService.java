package com.pranicdoc.cph.auth.service.impl;

import com.pranicdoc.cph.auth.dto.ChangePasswordRequest;
import com.pranicdoc.cph.auth.dto.HealerRequest;
import com.pranicdoc.cph.auth.dto.HealerResponse;
import com.pranicdoc.cph.auth.dto.LinkGoogleRequest;
import com.pranicdoc.cph.auth.dto.LoginRequest;
import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.common.enums.LinkStatus;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.exception.TokenExpiredException;
import com.pranicdoc.cph.common.security.CookieUtil;
import com.pranicdoc.cph.common.security.GoogleTokenVerifier;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.notification.entity.FeedbackLink;
import com.pranicdoc.cph.notification.repository.FeedbackLinkRepository;
import com.pranicdoc.cph.patient.repository.RegistrationLinkRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.UUID;

import static com.pranicdoc.cph.common.security.JwtAuthFilter.extractTokenFromCookie;

/**
 * Authentication service — register, login, token refresh, current healer.
 * All token delivery via HttpOnly cookies (no token in response body).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final HealerRepository healerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CookieUtil cookieUtil;
    private final SecurityContextUtil securityContextUtil;
    private final RegistrationLinkRepository registrationLinkRepository;
    private final FeedbackLinkRepository feedbackLinkRepository;
    private final GoogleTokenVerifier googleTokenVerifier;
    private static final String PATIENT = "PATIENT";

    // ─── Login ──────────────────────────────────────────────────

    @Auditable(action = "LOGIN")
    public HealerResponse login(LoginRequest request, HttpServletResponse response) {
        Healer healer = healerRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!healer.isActive()) {
            throw new IllegalArgumentException("Account is deactivated");
        }

        if (!passwordEncoder.matches(request.password(), healer.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        setAuthCookies(response, healer);

        return toResponse(healer);
    }

    @Auditable(action = "FEEDBACK_SESSION")
    public void initSessionForFeedback(String token, HttpServletResponse response) {
        FeedbackLink link = feedbackLinkRepository
                .findByTokenAndStatusAndExpiresAtAfter(token, LinkStatus.ACTIVE, Instant.now())
                .orElseThrow(() -> new TokenExpiredException("Feedback link is expired or already used"));
        // 42-15/CR-02: caseId is taken from the persisted FeedbackLink row, so a caller
        // cannot influence which case the resulting session is bound to.
        String selfRegToken = jwtService.generateFeedbackSessionToken(
                link.getHealerId().toString(), link.getCaseId());
        cookieUtil.setSelfRegSessionCookie(response, selfRegToken);
    }

    @Auditable(action = "SELF_REG_SESSION")
    public ApiResponse<String> initSessionForSelfReg(String uuid, String healerId, HttpServletResponse response) {
        if (uuid == null || uuid.isBlank()) {
            throw new IllegalArgumentException("Invalid UUID");
        }
        Instant now = Instant.now();
        int activated = registrationLinkRepository.consume(uuid, now);
        if (activated == 0) {
            // Returning patient — link already IN_PROGRESS; allow re-auth if not expired
            registrationLinkRepository.findInProgress(uuid, now)
                    .orElseThrow(() -> new TokenExpiredException("Invalid or expired link"));
        }
        // Embed reg UUID in token so CaseService can identify and consume the link on case creation
        String selfRegToken = jwtService.generateSelfRegToken(healerId, uuid);
        cookieUtil.setSelfRegSessionCookie(response, selfRegToken);
        return ApiResponse.of(PATIENT);
    }

    public void clearPatientSession(HttpServletResponse response) {
        cookieUtil.clearPatientCaseCookie(response);
    }

    // ─── Refresh ────────────────────────────────────────────────

    public void refresh(HttpServletRequest request, HttpServletResponse response) {
        String token = extractTokenFromCookie(request, "refreshToken");

        if (token == null || !jwtService.isTokenValid(token)) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        String tokenType = jwtService.extractTokenType(token);
        if (!"REFRESH".equals(tokenType)) {
            throw new IllegalArgumentException("Not a refresh token");
        }

        UUID healerId = jwtService.extractHealerId(token);
        Healer healer = findOrThrow(healerId);

        if (!healer.isActive()) {
            throw new IllegalArgumentException("Account is deactivated");
        }

        setAuthCookies(response, healer);
    }

    // ─── Logout ─────────────────────────────────────────────────

    public void logout(HttpServletResponse response) {
        cookieUtil.clearAuthCookies(response);
    }

    // ─── Get current healer (/me) ───────────────────────────────

    @Auditable(action = "GET_PROFILE")
    @Transactional(readOnly = true)
    public HealerResponse getCurrentHealer() {
        UUID healerId = securityContextUtil.getCurrentHealerId();
        Healer healer = findOrThrow(healerId);
        return toResponse(healer);
    }

    // ─── Update current healer profile ─────────────────────────

    @Auditable(action = "UPDATE_PROFILE")
    public HealerResponse updateProfile(HealerRequest request) {
        UUID healerId = securityContextUtil.getCurrentHealerId();
        Healer healer = findOrThrow(healerId);
        healer.setFullName(request.fullName());
        healer.setEmail(request.email());
        healer.setPhone(request.phone());
        Healer saved = healerRepository.save(healer);
        return toResponse(saved);
    }

    // ─── Change current healer password ─────────────────────────

    @Auditable(action = "CHANGE_PASSWORD")
    public void changePassword(ChangePasswordRequest request) {
        UUID healerId = securityContextUtil.getCurrentHealerId();
        Healer healer = findOrThrow(healerId);

        boolean hasPassword = healer.getPasswordHash() != null;

        if (hasPassword) {
            if (request.currentPassword() == null || request.currentPassword().isBlank()) {
                throw new IllegalArgumentException("Current password is required");
            }
            if (!passwordEncoder.matches(request.currentPassword(), healer.getPasswordHash())) {
                throw new IllegalArgumentException("Current password is incorrect");
            }
        }

        if (request.newPassword().length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        if (hasPassword && request.newPassword().equals(request.currentPassword())) {
            throw new IllegalArgumentException("New password must differ from current password");
        }

        healer.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        healerRepository.save(healer);
    }

    // ─── Link / unlink Google account ───────────────────────────

    @Auditable(action = "LINK_GOOGLE")
    public void linkGoogleAccount(LinkGoogleRequest request) {
        UUID healerId = securityContextUtil.getCurrentHealerId();
        Healer healer = findOrThrow(healerId);

        GoogleTokenVerifier.VerifiedGoogleUser verified = googleTokenVerifier.verify(request.idToken());

        if (!verified.email().equalsIgnoreCase(healer.getEmail())) {
            throw new IllegalArgumentException("That Google account's email doesn't match your profile email.");
        }

        if (healerRepository.existsByProviderUserId(verified.sub())) {
            throw new IllegalArgumentException("This Google account is already linked to another PranicDoc account.");
        }

        healer.setProviderUserId(verified.sub());
        try {
            healerRepository.save(healer);
        } catch (DataIntegrityViolationException ex) {
            // Race: two concurrent link requests both passed the pre-check; the DB UNIQUE
            // constraint (uk_healer_provider_user_id) is the suspenders here.
            throw new IllegalArgumentException("This Google account is already linked to another PranicDoc account.");
        }
    }

    @Auditable(action = "UNLINK_GOOGLE")
    public void unlinkGoogleAccount() {
        UUID healerId = securityContextUtil.getCurrentHealerId();
        Healer healer = findOrThrow(healerId);

        if (healer.getPasswordHash() == null) {
            throw new IllegalArgumentException("Set a password first");
        }

        healer.setProviderUserId(null);
        healerRepository.save(healer);
    }

    // ─── Get healer by ID ───────────────────────────────────────

    @Transactional(readOnly = true)
    public HealerResponse getHealerById(UUID id) {
        Healer healer = findOrThrow(id);
        return toResponse(healer);
    }

    // ─── Internal helpers ───────────────────────────────────────

    private void setAuthCookies(HttpServletResponse response, Healer healer) {
        String accessToken = jwtService.generateAccessToken(healer.getId(), healer.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(healer.getId(), healer.getRole().name());
        cookieUtil.setAuthCookies(response, accessToken, refreshToken);
    }

    private Healer findOrThrow(UUID id) {
        return healerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Healer", id));
    }

    private HealerResponse toResponse(Healer h) {
        return new HealerResponse(
                h.getId(), h.getFullName(), h.getEmail(), h.getPhone(),
                h.getRole(), h.getHealingLevel(), h.isActive(), h.getCreatedAt(),
                h.getPasswordHash() != null, h.getProviderUserId() != null
        );
    }
}
