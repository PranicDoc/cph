package com.pranicdoc.cph.auth.service;

import com.pranicdoc.cph.auth.dto.ChangePasswordRequest;
import com.pranicdoc.cph.auth.dto.HealerRequest;
import com.pranicdoc.cph.auth.dto.HealerResponse;
import com.pranicdoc.cph.auth.dto.UiVersionRequest;
import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.auth.service.impl.AuthService;
import com.pranicdoc.cph.common.enums.LinkStatus;
import com.pranicdoc.cph.common.enums.Role;
import com.pranicdoc.cph.common.enums.UiVersion;
import com.pranicdoc.cph.common.exception.InsufficientCreditsException;
import com.pranicdoc.cph.common.security.CookieUtil;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.healing.model.HealingSession;
import com.pranicdoc.cph.healing.repository.SessionRepository;
import com.pranicdoc.cph.notification.entity.FeedbackLink;
import com.pranicdoc.cph.notification.repository.FeedbackLinkRepository;
import com.pranicdoc.cph.payments.CreditServiceClient;
import com.pranicdoc.cph.payments.dto.DeductResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private CreditServiceClient creditServiceClient;

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

    @Test
    void initSessionForFeedback_returnsTheLatestSessionDateAsTheHealingDate() {
        UUID caseId = UUID.randomUUID();
        FeedbackLink link = FeedbackLink.builder()
                .token("tok")
                .caseId(caseId)
                .healerId(UUID.randomUUID())
                .status(LinkStatus.ACTIVE)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(feedbackLinkRepository.findByTokenAndStatusAndExpiresAtAfter(
                eq("tok"), eq(LinkStatus.ACTIVE), any(Instant.class)))
                .thenReturn(Optional.of(link));
        when(sessionRepository.findByHealingCaseIdOrderBySessionNumberAsc(caseId)).thenReturn(List.of(
                HealingSession.builder().sessionNumber(1).sessionDate(LocalDate.of(2026, 10, 1)).build(),
                HealingSession.builder().sessionNumber(2).sessionDate(LocalDate.of(2026, 10, 8)).build()));

        assertEquals(LocalDate.of(2026, 10, 8),
                authService.initSessionForFeedback("tok", new MockHttpServletResponse()).healingDate());
    }

    @Test
    void newHealer_defaultsToTheClassicView() {
        assertEquals(UiVersion.CLASSIC, Healer.builder().build().getUiVersion());
    }

    @Test
    void updateUiVersion_savesTheChoiceOnTheCurrentHealerAndReturnsIt() {
        UUID healerId = UUID.randomUUID();
        Healer healer = Healer.builder().fullName("Asha Rao").email("asha@example.com").role(Role.HEALER).build();
        healer.setId(healerId);

        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        when(healerRepository.findById(healerId)).thenReturn(Optional.of(healer));
        when(healerRepository.save(healer)).thenReturn(healer);

        HealerResponse response = authService.updateUiVersion(new UiVersionRequest(UiVersion.NEW));

        assertEquals(UiVersion.NEW, healer.getUiVersion());
        assertEquals(UiVersion.NEW, response.uiVersion());
        verify(healerRepository).save(healer);
    }

    // ─── Name change credits ─────────────────────────────────────

    private Healer currentHealer(String name, int nameChangeCount) {
        Healer healer = Healer.builder().fullName(name).email("asha@example.com").role(Role.HEALER)
                .nameChangeCount(nameChangeCount).build();
        healer.setId(UUID.randomUUID());
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healer.getId());
        when(healerRepository.findById(healer.getId())).thenReturn(Optional.of(healer));
        return healer;
    }

    @Test
    void nameChangeCost_isFreeFirstThenDoublesFrom25AndNeverOverflows() {
        assertEquals(0, Healer.nameChangeCost(1));
        assertEquals(25, Healer.nameChangeCost(2));
        assertEquals(50, Healer.nameChangeCost(3));
        assertEquals(200, Healer.nameChangeCost(5));
        assertEquals(1_677_721_600, Healer.nameChangeCost(28));
        assertEquals(Integer.MAX_VALUE, Healer.nameChangeCost(29));
        assertEquals(Integer.MAX_VALUE, Healer.nameChangeCost(Integer.MAX_VALUE));
    }

    @Test
    void updateProfile_firstNameChangeIsFreeAndNeverCallsTheCreditService() {
        Healer healer = currentHealer("Asha Rao", 0);
        when(healerRepository.save(healer)).thenReturn(healer);

        HealerResponse response = authService.updateProfile(new HealerRequest("Asha R. Rao", "asha@example.com", null));

        assertEquals("Asha R. Rao", healer.getFullName());
        assertEquals(1, healer.getNameChangeCount());
        assertEquals(25, response.nextNameChangeCost());
        verifyNoInteractions(creditServiceClient);
    }

    @ParameterizedTest
    @CsvSource({"1, 2, 25, 50", "2, 3, 50, 100", "3, 4, 100, 200"})
    void updateProfile_laterNameChangesDeductTheLadderPriceBeforeSaving(int done, int n, int cost, int next) {
        Healer healer = currentHealer("Asha Rao", done);
        String key = "name-change:" + healer.getId() + ":" + n;
        when(creditServiceClient.deduct(healer.getId(), key, cost)).thenReturn(new DeductResponse("APPROVED", 10));
        when(healerRepository.save(healer)).thenReturn(healer);

        HealerResponse response = authService.updateProfile(new HealerRequest("Asha Devi", "asha@example.com", null));

        verify(creditServiceClient).deduct(healer.getId(), key, cost);
        assertEquals("Asha Devi", healer.getFullName());
        assertEquals(n, healer.getNameChangeCount());
        assertEquals(next, response.nextNameChangeCost());
    }

    @Test
    void updateProfile_anEmailTakenByAnotherAccountIsRefusedBeforeAnyCharge() {
        Healer healer = currentHealer("Asha Rao", 1);
        when(healerRepository.existsByEmail("taken@example.com")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.updateProfile(new HealerRequest("Asha Devi", "taken@example.com", null)));

        assertEquals("A healer with this email already exists", ex.getMessage());
        assertEquals("Asha Rao", healer.getFullName());
        verifyNoInteractions(creditServiceClient);
        verify(healerRepository, never()).save(any());
    }

    @Test
    void updateProfile_insufficientCreditsBlocksTheChangeAndSavesNothing() {
        Healer healer = currentHealer("Asha Rao", 1);
        when(creditServiceClient.deduct(healer.getId(), "name-change:" + healer.getId() + ":2", 25))
                .thenReturn(new DeductResponse("INSUFFICIENT", 10));

        InsufficientCreditsException ex = assertThrows(InsufficientCreditsException.class,
                () -> authService.updateProfile(new HealerRequest("Asha Devi", "new@example.com", null)));

        assertEquals("You need 25 credits to change your name. Buy credits to continue.", ex.getMessage());
        assertEquals("Asha Rao", healer.getFullName());
        assertEquals("asha@example.com", healer.getEmail());
        assertEquals(1, healer.getNameChangeCount());
        verify(healerRepository, never()).save(any());
    }

    @Test
    void updateProfile_sameNameIgnoringSpacesWithNewEmailIsFreeAndNotCounted() {
        Healer healer = currentHealer("Asha Rao", 2);
        when(healerRepository.save(healer)).thenReturn(healer);

        HealerResponse response = authService.updateProfile(new HealerRequest("  Asha Rao ", "new@example.com", "999"));

        assertEquals("Asha Rao", healer.getFullName());
        assertEquals("new@example.com", healer.getEmail());
        assertEquals("999", healer.getPhone());
        assertEquals(2, healer.getNameChangeCount());
        assertEquals(50, response.nextNameChangeCost());
        verifyNoInteractions(creditServiceClient);
    }

    @Test
    void getCurrentHealer_reportsWhatTheNextNameChangeCosts() {
        currentHealer("Asha Rao", 0);

        assertEquals(0, authService.getCurrentHealer().nextNameChangeCost());
    }
}
