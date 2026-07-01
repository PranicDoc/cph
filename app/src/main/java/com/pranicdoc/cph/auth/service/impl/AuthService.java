package com.pranicdoc.cph.auth.service.impl;

import com.pranicdoc.cph.auth.dto.HealerCreateRequest;
import com.pranicdoc.cph.auth.dto.HealerRequest;
import com.pranicdoc.cph.auth.dto.HealerResponse;
import com.pranicdoc.cph.auth.dto.LoginRequest;
import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.clinic.model.Clinic;
import com.pranicdoc.cph.clinic.repository.ClinicRepository;
import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.common.enums.LinkStatus;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.exception.TokenExpiredException;
import com.pranicdoc.cph.common.security.CookieUtil;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.notification.entity.FeedbackLink;
import com.pranicdoc.cph.notification.repository.FeedbackLinkRepository;
import com.pranicdoc.cph.patient.repository.RegistrationLinkRepository;
import com.pranicdoc.cph.payments.dto.OwnerType;
import com.pranicdoc.cph.payments.service.CreditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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
    private final ClinicRepository clinicRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CookieUtil cookieUtil;
    private final SecurityContextUtil securityContextUtil;
    private final RegistrationLinkRepository registrationLinkRepository;
    private final CreditService creditService;
    private final FeedbackLinkRepository feedbackLinkRepository;
    private static final String PATIENT = "PATIENT";

    // ─── Register ───────────────────────────────────────────────

    @Auditable(action = "REGISTER_HEALER")
    public HealerResponse register(HealerCreateRequest request, HttpServletResponse response) {
        // Resolve clinic code → clinic entity
        Clinic clinic = clinicRepository.findByCode(request.clinicCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid clinic code: " + request.clinicCode()));

        // Check for duplicate email within the same clinic
        if (healerRepository.existsByEmailAndClinicId(request.email(), clinic.getId())) {
            throw new IllegalArgumentException(
                    "A healer with this email already exists in clinic " + request.clinicCode());
        }

        Healer healer = Healer.builder()
                .fullName(request.fullName())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .phone(request.phone())
                .role(request.role())
                .healingLevel(request.healingLevel())
                .active(true)
                .build();

        healer.setClinicId(clinic.getId());
        Healer saved = healerRepository.save(healer);
        creditService.createAccount(OwnerType.HEALER, healer.getId());
        // Set auth cookies
        setAuthCookies(response, saved, clinic.getCode());

        return toResponse(saved, clinic.getCode());
    }

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

        Clinic clinic = clinicRepository.findById(healer.getClinicId())
                .orElseThrow(() -> new ResourceNotFoundException("Clinic", healer.getClinicId()));

        // Set auth cookies
        setAuthCookies(response, healer, clinic.getCode());

        return toResponse(healer, clinic.getCode());
    }

    @Auditable(action = "FEEDBACK_SESSION")
    public void initSessionForFeedback(String token, HttpServletResponse response) {
        FeedbackLink link = feedbackLinkRepository
                .findByTokenAndStatusAndExpiresAtAfter(token, LinkStatus.ACTIVE, Instant.now())
                .orElseThrow(() -> new TokenExpiredException("Feedback link is expired or already used"));
        String selfRegToken = jwtService.generateSelfRegToken(link.getClinicId().toString());
        cookieUtil.setSelfRegSessionCookie(response, selfRegToken);
    }

    @Auditable(action = "SELF_REG_SESSION")
    public ApiResponse<String> initSessionForSelfReg(String uuid, String clinicId, HttpServletResponse response) {
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
        String selfRegToken = jwtService.generateSelfRegToken(clinicId, uuid);
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

        Clinic clinic = clinicRepository.findById(healer.getClinicId())
                .orElseThrow(() -> new ResourceNotFoundException("Clinic", healer.getClinicId()));

        // Set new auth cookies
        setAuthCookies(response, healer, clinic.getCode());
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
        String clinicCode = securityContextUtil.getCurrentClinicCode();
        return toResponse(healer, clinicCode);
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
        return toResponse(saved, securityContextUtil.getCurrentClinicCode());
    }

    // ─── Get healer by ID ───────────────────────────────────────

    @Transactional(readOnly = true)
    public HealerResponse getHealerById(UUID id) {
        Healer healer = findOrThrow(id);
        Clinic clinic = clinicRepository.findById(healer.getClinicId())
                .orElseThrow(() -> new ResourceNotFoundException("Clinic", healer.getClinicId()));
        return toResponse(healer, clinic.getCode());
    }

    // ─── Internal helpers ───────────────────────────────────────

    private void setAuthCookies(HttpServletResponse response, Healer healer, String clinicCode) {
        String accessToken = jwtService.generateAccessToken(
                healer.getId(), healer.getClinicId(), clinicCode, healer.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(
                healer.getId(), healer.getClinicId(), clinicCode, healer.getRole().name());
        cookieUtil.setAuthCookies(response, accessToken, refreshToken);
    }

    private Healer findOrThrow(UUID id) {
        return healerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Healer", id));
    }

    private HealerResponse toResponse(Healer h, String clinicCode) {
        return new HealerResponse(
                h.getId(), h.getFullName(), h.getEmail(), h.getPhone(),
                h.getRole(), h.getHealingLevel(), h.isActive(),
                h.getClinicId(), clinicCode, h.getCreatedAt()
        );
    }
}
