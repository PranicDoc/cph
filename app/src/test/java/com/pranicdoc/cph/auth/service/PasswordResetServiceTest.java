package com.pranicdoc.cph.auth.service;

import com.pranicdoc.cph.auth.dto.ResetPasswordRequest;
import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.model.PasswordResetToken;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.auth.repository.PasswordResetTokenRepository;
import com.pranicdoc.cph.auth.service.impl.AccountEmails;
import com.pranicdoc.cph.auth.service.impl.PasswordResetService;
import com.pranicdoc.cph.common.exception.TokenExpiredException;
import com.pranicdoc.cph.notification.channel.NotificationMessage;
import com.pranicdoc.cph.notification.email.EmailTemplates;
import com.pranicdoc.cph.notification.service.NotificationSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final String GENERIC = "If that email belongs to an account, a password reset link has been sent.";
    private static final Pattern TOKEN_IN_LINK = Pattern.compile("reset-password\\?token=([A-Za-z0-9_-]+)");

    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private HealerRepository healerRepository;
    @Mock private NotificationSender notificationSender;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final AccountEmails accountEmails = new AccountEmails(new EmailTemplates());

    private PasswordResetService service;
    private Healer healer;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(tokenRepository, healerRepository, passwordEncoder, notificationSender, accountEmails);
        ReflectionTestUtils.setField(service, "inviteUrl", "https://app.healingark.in/accept-invite");
        ReflectionTestUtils.setField(service, "expiryMinutes", 30L);
        healer = Healer.builder().fullName("Asha").email("asha@example.com")
                .passwordHash(passwordEncoder.encode("old-password")).active(true).build();
        healer.setId(UUID.randomUUID());
    }

    // ─── forgot password ────────────────────────────────────────

    @Test
    void knownAddressGetsALinkAndOnlyTheHashIsStored() {
        when(healerRepository.findByEmail("asha@example.com")).thenReturn(Optional.of(healer));

        var response = service.requestReset(" asha@example.com ");

        assertEquals(GENERIC, response.message());
        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(saved.capture());
        ArgumentCaptor<NotificationMessage> mail = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationSender).sendAfterCommit(mail.capture());

        Matcher m = TOKEN_IN_LINK.matcher(mail.getValue().body());
        assertTrue(m.find(), "email carries the reset link");
        String token = m.group(1);
        assertTrue(mail.getValue().body().contains("https://app.healingark.in/reset-password?token="));
        assertEquals(64, saved.getValue().getTokenHash().length());
        assertNotEquals(token, saved.getValue().getTokenHash(), "the raw token is never stored");
        assertFalse(saved.getValue().getTokenHash().contains(token));
        assertTrue(saved.getValue().getExpiresAt().isBefore(Instant.now().plus(Duration.ofMinutes(31))));
        assertTrue(saved.getValue().getExpiresAt().isAfter(Instant.now().plus(Duration.ofMinutes(29))));
        verify(tokenRepository).invalidateOutstanding(eq(healer.getId()), any());
    }

    @Test
    void unknownAddressGetsTheSameAnswerAndNothingIsSent() {
        when(healerRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        var response = service.requestReset("nobody@example.com");

        assertEquals(GENERIC, response.message());
        verifyNoInteractions(notificationSender);
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void inactiveAccountIsTreatedLikeUnknown() {
        healer.setActive(false);
        when(healerRepository.findByEmail("asha@example.com")).thenReturn(Optional.of(healer));

        var response = service.requestReset("asha@example.com");

        assertEquals(GENERIC, response.message());
        verifyNoInteractions(notificationSender);
    }

    @Test
    void secondRequestWithinAMinuteSendsNothingMore() {
        when(healerRepository.findByEmail("asha@example.com")).thenReturn(Optional.of(healer));
        when(tokenRepository.existsByHealerIdAndCreatedAtAfter(eq(healer.getId()), any())).thenReturn(true);

        var response = service.requestReset("asha@example.com");

        assertEquals(GENERIC, response.message());
        verifyNoInteractions(notificationSender);
        verify(tokenRepository, never()).save(any());
    }

    // ─── reset password ─────────────────────────────────────────

    @Test
    void validTokenSetsThePasswordSpendsTheLinkAndNotifiesTheHealer() {
        PasswordResetToken stored = storedTokenFor("good-token");
        when(tokenRepository.findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(eq(hashOf("good-token")), any()))
                .thenReturn(Optional.of(stored));
        when(tokenRepository.markUsed(eq(stored.getId()), any())).thenReturn(1);
        when(healerRepository.findById(healer.getId())).thenReturn(Optional.of(healer));

        service.resetPassword(new ResetPasswordRequest("good-token", "new-password-1", "new-password-1"));

        assertTrue(passwordEncoder.matches("new-password-1", healer.getPasswordHash()));
        verify(healerRepository).save(healer);
        verify(tokenRepository).invalidateOutstanding(eq(healer.getId()), any());
        ArgumentCaptor<NotificationMessage> mail = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationSender).sendAfterCommit(mail.capture());
        assertEquals("Your HealingARK password was changed", mail.getValue().subject());
    }

    @Test
    void unknownOrExpiredTokenIsRejected() {
        when(tokenRepository.findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(anyString(), any()))
                .thenReturn(Optional.empty());

        assertThrows(TokenExpiredException.class,
                () -> service.resetPassword(new ResetPasswordRequest("nope", "new-password-1", "new-password-1")));
        verify(healerRepository, never()).save(any());
    }

    @Test
    void aLinkUsedConcurrentlyIsRejected() {
        PasswordResetToken stored = storedTokenFor("raced");
        when(tokenRepository.findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(anyString(), any()))
                .thenReturn(Optional.of(stored));
        when(tokenRepository.markUsed(eq(stored.getId()), any())).thenReturn(0);

        assertThrows(TokenExpiredException.class,
                () -> service.resetPassword(new ResetPasswordRequest("raced", "new-password-1", "new-password-1")));
        verify(healerRepository, never()).save(any());
    }

    @Test
    void mismatchedConfirmationFailsBeforeTheLinkIsTouched() {
        assertThrows(IllegalArgumentException.class,
                () -> service.resetPassword(new ResetPasswordRequest("t", "new-password-1", "different-1")));
        verifyNoInteractions(tokenRepository);
    }

    @Test
    void reusingTheCurrentPasswordIsRejected() {
        PasswordResetToken stored = storedTokenFor("same");
        when(tokenRepository.findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(anyString(), any()))
                .thenReturn(Optional.of(stored));
        when(tokenRepository.markUsed(eq(stored.getId()), any())).thenReturn(1);
        when(healerRepository.findById(healer.getId())).thenReturn(Optional.of(healer));

        assertThrows(IllegalArgumentException.class,
                () -> service.resetPassword(new ResetPasswordRequest("same", "old-password", "old-password")));
        verify(healerRepository, never()).save(any());
    }

    @Test
    void deactivatedAccountCannotReset() {
        healer.setActive(false);
        PasswordResetToken stored = storedTokenFor("inactive");
        when(tokenRepository.findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(anyString(), any()))
                .thenReturn(Optional.of(stored));
        when(tokenRepository.markUsed(eq(stored.getId()), any())).thenReturn(1);
        when(healerRepository.findById(healer.getId())).thenReturn(Optional.of(healer));

        assertThrows(TokenExpiredException.class,
                () -> service.resetPassword(new ResetPasswordRequest("inactive", "new-password-1", "new-password-1")));
    }

    private PasswordResetToken storedTokenFor(String token) {
        return PasswordResetToken.builder().healerId(healer.getId()).tokenHash(hashOf(token))
                .expiresAt(Instant.now().plusSeconds(600)).build();
    }

    private static String hashOf(String token) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}
