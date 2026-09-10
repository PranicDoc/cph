package com.pranicdoc.cph.auth.service;

import com.pranicdoc.cph.auth.dto.ChangePasswordRequest;
import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.auth.service.impl.AuthService;
import com.pranicdoc.cph.common.enums.LinkStatus;
import com.pranicdoc.cph.common.security.CookieUtil;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.notification.entity.FeedbackLink;
import com.pranicdoc.cph.notification.repository.FeedbackLinkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private HealerRepository healerRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SecurityContextUtil securityContextUtil;

    @Mock
    private JwtService jwtService;

    @Mock
    private CookieUtil cookieUtil;

    @Mock
    private FeedbackLinkRepository feedbackLinkRepository;

    @InjectMocks
    private AuthService authService;

    @Test
    void changePassword_wrongCurrentPassword_throwsIAE() {
        UUID healerId = UUID.randomUUID();
        Healer healer = Healer.builder().passwordHash("hashedOld").build();
        healer.setId(healerId);

        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        when(healerRepository.findById(healerId)).thenReturn(Optional.of(healer));
        when(passwordEncoder.matches("wrongPwd", "hashedOld")).thenReturn(false);

        ChangePasswordRequest request = new ChangePasswordRequest("wrongPwd", "newPass123", "newPass123");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.changePassword(request));
        assertEquals("Current password is incorrect", ex.getMessage());
    }

    @Test
    void changePassword_googleOnly_skipsCurrentPasswordCheck() {
        UUID healerId = UUID.randomUUID();
        Healer healer = Healer.builder().passwordHash(null).build();
        healer.setId(healerId);

        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        when(healerRepository.findById(healerId)).thenReturn(Optional.of(healer));
        when(passwordEncoder.encode("newPass123")).thenReturn("hashedNew");

        ChangePasswordRequest request = new ChangePasswordRequest(null, "newPass123", "newPass123");

        assertDoesNotThrow(() -> authService.changePassword(request));

        verify(passwordEncoder, never()).matches(any(), any());
        verify(healerRepository, times(1)).save(any(Healer.class));
    }

    @Test
    void changePassword_confirmMismatch_throwsIAE() {
        UUID healerId = UUID.randomUUID();
        Healer healer = Healer.builder().passwordHash("hashedOld").build();
        healer.setId(healerId);

        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        when(healerRepository.findById(healerId)).thenReturn(Optional.of(healer));
        when(passwordEncoder.matches("oldPass1", "hashedOld")).thenReturn(true);

        ChangePasswordRequest request = new ChangePasswordRequest("oldPass1", "newPass123", "different");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.changePassword(request));
        assertEquals("Passwords do not match", ex.getMessage());
    }

    @Test
    void changePassword_tooShort_throwsIAE() {
        UUID healerId = UUID.randomUUID();
        Healer healer = Healer.builder().passwordHash("hashedOld").build();
        healer.setId(healerId);

        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        when(healerRepository.findById(healerId)).thenReturn(Optional.of(healer));
        when(passwordEncoder.matches("oldPass1", "hashedOld")).thenReturn(true);

        ChangePasswordRequest request = new ChangePasswordRequest("oldPass1", "short", "short");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.changePassword(request));
        assertEquals("Password must be at least 8 characters", ex.getMessage());
    }

    @Test
    void changePassword_happyPath_savesHealer() {
        UUID healerId = UUID.randomUUID();
        Healer healer = Healer.builder().passwordHash("hashedOld").build();
        healer.setId(healerId);

        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        when(healerRepository.findById(healerId)).thenReturn(Optional.of(healer));
        when(passwordEncoder.matches("oldPass1", "hashedOld")).thenReturn(true);
        when(passwordEncoder.encode("newPass123")).thenReturn("hashedNew");

        ChangePasswordRequest request = new ChangePasswordRequest("oldPass1", "newPass123", "newPass123");

        assertDoesNotThrow(() -> authService.changePassword(request));

        verify(healerRepository, times(1)).save(any(Healer.class));
    }

    @Test
    void changePassword_newEqualsCurrent_throwsIAE() {
        UUID healerId = UUID.randomUUID();
        Healer healer = Healer.builder().passwordHash("hashedOld").build();
        healer.setId(healerId);

        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        when(healerRepository.findById(healerId)).thenReturn(Optional.of(healer));
        when(passwordEncoder.matches("samePass1", "hashedOld")).thenReturn(true);

        ChangePasswordRequest request = new ChangePasswordRequest("samePass1", "samePass1", "samePass1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.changePassword(request));
        assertEquals("New password must differ from current password", ex.getMessage());
    }

    @Test
    void initSessionForFeedback_mintsATokenBoundToTheLinksCase() {
        UUID caseId = UUID.randomUUID();
        UUID healerId = UUID.randomUUID();
        FeedbackLink link = FeedbackLink.builder()
                .token("tok")
                .caseId(caseId)
                .healerId(healerId)
                .status(LinkStatus.ACTIVE)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(feedbackLinkRepository.findByTokenAndStatusAndExpiresAtAfter(
                eq("tok"), eq(LinkStatus.ACTIVE), any(Instant.class)))
                .thenReturn(Optional.of(link));
        String sentinel = "sentinel-token";
        when(jwtService.generateFeedbackSessionToken(anyString(), any())).thenReturn(sentinel);

        authService.initSessionForFeedback("tok", new MockHttpServletResponse());

        verify(jwtService).generateFeedbackSessionToken(healerId.toString(), caseId);
        verify(cookieUtil).setSelfRegSessionCookie(any(), eq(sentinel));
        verify(jwtService, never()).generateSelfRegToken(anyString());
    }
}
