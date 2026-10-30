package com.pranicdoc.cph.auth.service.impl;

import com.pranicdoc.cph.auth.dto.ResetPasswordRequest;
import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.model.PasswordResetToken;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.auth.repository.PasswordResetTokenRepository;
import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.common.exception.TokenExpiredException;
import com.pranicdoc.cph.notification.service.NotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Forgot password. {@link #requestReset} emails a one-time link; {@link #resetPassword} spends it.
 *
 * <p>Safeguards: the response never reveals whether an account exists; the token is 256 random bits and
 * only its hash is stored; it is short-lived and single-use; a newer link replaces older ones; an
 * address can be mailed at most once a minute; the email is sent after commit on a background thread
 * (so response time does not tell an existing address from an unknown one); the healer is told by
 * email when the password changes. Nobody is signed in by a reset, they sign in with the new password.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PasswordResetService {

    static final String GENERIC_MESSAGE = "If that email belongs to an account, a password reset link has been sent.";
    private static final String INVALID_LINK = "This password reset link is invalid, expired, or already used.";
    private static final Duration RESEND_COOLDOWN = Duration.ofMinutes(1);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PasswordResetTokenRepository tokenRepository;
    private final HealerRepository healerRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationSender notificationSender;
    private final AccountEmails accountEmails;

    /** Where the app lives: the origin of the invite URL, which is already required and checked at startup. */
    @Value("${app.invite.url}")
    private String inviteUrl;

    @Value("${app.password-reset.token-expiry-minutes:30}")
    private long expiryMinutes;

    /** Public. Always answers the same, whatever the address. */
    @Auditable(action = "REQUEST_PASSWORD_RESET", auditPayload = false)
    public ApiResponse<String> requestReset(String email) {
        ApiResponse<String> generic = new ApiResponse<>(true, GENERIC_MESSAGE, null);

        Healer healer = healerRepository.findByEmail(email.trim()).orElse(null);
        if (healer == null || !healer.isActive()) {
            return generic;
        }
        Instant now = Instant.now();
        if (tokenRepository.existsByHealerIdAndCreatedAtAfter(healer.getId(), now.minus(RESEND_COOLDOWN))) {
            return generic;
        }

        tokenRepository.invalidateOutstanding(healer.getId(), now);
        String token = newToken();
        tokenRepository.save(PasswordResetToken.builder()
                .healerId(healer.getId())
                .tokenHash(hash(token))
                .expiresAt(now.plus(expiryMinutes, ChronoUnit.MINUTES))
                .build());

        notificationSender.sendAfterCommit(
                accountEmails.passwordReset(healer.getEmail(), appOrigin() + "/reset-password?token=" + token, expiryMinutes));
        return generic;
    }

    /** Public, token-authenticated. */
    @Auditable(action = "RESET_PASSWORD", auditPayload = false)
    public ApiResponse<String> resetPassword(ResetPasswordRequest request) {
        // Check the password first, so a typo does not burn the link.
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        Instant now = Instant.now();
        PasswordResetToken stored = tokenRepository
                .findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(hash(request.token()), now)
                .orElseThrow(() -> new TokenExpiredException(INVALID_LINK));
        // Spend it before changing anything: a concurrent use of the same link gets 0 rows here.
        if (tokenRepository.markUsed(stored.getId(), now) == 0) {
            throw new TokenExpiredException(INVALID_LINK);
        }

        Healer healer = healerRepository.findById(stored.getHealerId())
                .filter(Healer::isActive)
                .orElseThrow(() -> new TokenExpiredException(INVALID_LINK));
        if (healer.getPasswordHash() != null && passwordEncoder.matches(request.newPassword(), healer.getPasswordHash())) {
            throw new IllegalArgumentException("New password must differ from the current password");
        }

        healer.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        healerRepository.save(healer);
        tokenRepository.invalidateOutstanding(healer.getId(), now);

        notificationSender.sendAfterCommit(accountEmails.passwordChanged(healer.getEmail(), appOrigin() + "/login"));
        log.info("PASSWORD_RESET healerId={}", healer.getId());
        return new ApiResponse<>(true, "Password updated. Sign in with your new password.", null);
    }

    private String appOrigin() {
        URI uri = URI.create(inviteUrl);
        return uri.getScheme() + "://" + uri.getAuthority();
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
