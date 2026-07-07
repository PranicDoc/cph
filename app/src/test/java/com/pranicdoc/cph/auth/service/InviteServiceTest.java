package com.pranicdoc.cph.auth.service;

import com.pranicdoc.cph.auth.dto.AcceptInviteRequest;
import com.pranicdoc.cph.auth.dto.HealerResponse;
import com.pranicdoc.cph.auth.dto.InviteResponse;
import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.model.HealerInviteLink;
import com.pranicdoc.cph.auth.repository.HealerInviteLinkRepository;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.auth.service.impl.InviteService;
import com.pranicdoc.cph.clinic.model.Clinic;
import com.pranicdoc.cph.clinic.repository.ClinicRepository;
import com.pranicdoc.cph.common.enums.Role;
import com.pranicdoc.cph.common.exception.TokenExpiredException;
import com.pranicdoc.cph.common.security.CookieUtil;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.payments.dto.OwnerType;
import com.pranicdoc.cph.payments.service.CreditService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InviteServiceTest {

    @Mock
    private HealerInviteLinkRepository inviteRepository;

    @Mock
    private HealerRepository healerRepository;

    @Mock
    private ClinicRepository clinicRepository;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private CookieUtil cookieUtil;

    @Mock
    private CreditService creditService;

    @Mock
    private HttpServletResponse httpServletResponse;

    private InviteService inviteService;

    private final UUID clinicId = UUID.randomUUID();
    private final UUID invitedByHealerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        inviteService = new InviteService(inviteRepository, healerRepository, clinicRepository,
                mailSender, passwordEncoder, jwtService, cookieUtil, creditService);
        ReflectionTestUtils.setField(inviteService, "inviteUrl", "http://localhost:3000/accept-invite");
        ReflectionTestUtils.setField(inviteService, "inviteExpiryHours", 72);
    }

    // ─── createInvite ───────────────────────────────────────────

    @Test
    void createInvite_noExistingPending_savesNewRowAndSendsEmail() {
        String email = "newhealer@example.com";
        when(healerRepository.existsByEmail(email)).thenReturn(false);
        when(inviteRepository.findByEmailAndClinicIdAndStatus(email, clinicId, HealerInviteLink.Status.PENDING))
                .thenReturn(Optional.empty());

        ArgumentCaptor<HealerInviteLink> captor = ArgumentCaptor.forClass(HealerInviteLink.class);
        when(inviteRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        InviteResponse response = inviteService.createInvite(email, Role.HEALER, clinicId, invitedByHealerId);

        HealerInviteLink saved = captor.getValue();
        assertEquals(email, saved.getEmail());
        assertEquals(Role.HEALER, saved.getRole());
        assertEquals(HealerInviteLink.Status.PENDING, saved.getStatus());
        assertEquals("PENDING", response.status());

        verify(inviteRepository, times(1)).save(any(HealerInviteLink.class));
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void createInvite_existingPendingRow_rotatesInPlace_doesNotInsertSecondRow() {
        String email = "existing@example.com";
        String oldToken = "old-token-value";
        HealerInviteLink existing = HealerInviteLink.builder()
                .id(UUID.randomUUID())
                .token(oldToken)
                .email(email)
                .role(Role.HEALER)
                .clinicId(clinicId)
                .invitedByHealerId(invitedByHealerId)
                .status(HealerInviteLink.Status.PENDING)
                .expiresAt(Instant.now().plus(10, ChronoUnit.HOURS))
                .build();

        when(healerRepository.existsByEmail(email)).thenReturn(false);
        when(inviteRepository.findByEmailAndClinicIdAndStatus(email, clinicId, HealerInviteLink.Status.PENDING))
                .thenReturn(Optional.of(existing));
        when(inviteRepository.save(any(HealerInviteLink.class))).thenAnswer(inv -> inv.getArgument(0));

        InviteResponse response = inviteService.createInvite(email, Role.HEALER, clinicId, invitedByHealerId);

        // Only ONE save call — the existing row is mutated and re-saved, no second row inserted.
        verify(inviteRepository, times(1)).save(any(HealerInviteLink.class));
        assertNotEquals(oldToken, existing.getToken());
        assertEquals(email, response.email());
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void createInvite_healerAlreadyExists_throwsIAE_noSaveNoMail() {
        String email = "already@example.com";
        when(healerRepository.existsByEmail(email)).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> inviteService.createInvite(email, Role.HEALER, clinicId, invitedByHealerId));

        verify(inviteRepository, never()).save(any());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void createInvite_healerExistsInDifferentClinic_throwsIAE_noSaveNoMail() {
        // Regression test for Phase 22 code review Critical finding: the dedup check
        // must be global, not scoped to the invite's clinic — AuthService.login() looks
        // healers up by email alone, so the same email active in two clinics breaks login.
        String email = "cross-clinic@example.com";
        UUID otherClinicId = UUID.randomUUID();
        when(healerRepository.existsByEmail(email)).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> inviteService.createInvite(email, Role.HEALER, otherClinicId, invitedByHealerId));

        verify(inviteRepository, never()).save(any());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void createInvite_mailSendThrows_inviteStillPersists() {
        String email = "mailfail@example.com";
        when(healerRepository.existsByEmail(email)).thenReturn(false);
        when(inviteRepository.findByEmailAndClinicIdAndStatus(email, clinicId, HealerInviteLink.Status.PENDING))
                .thenReturn(Optional.empty());
        when(inviteRepository.save(any(HealerInviteLink.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        InviteResponse response = inviteService.createInvite(email, Role.HEALER, clinicId, invitedByHealerId);

        assertEquals(email, response.email());
        verify(inviteRepository, times(1)).save(any(HealerInviteLink.class));
    }

    // ─── resend ─────────────────────────────────────────────────

    @Test
    void resend_pendingInvite_rotatesTokenAndResetsExpiry() {
        UUID inviteId = UUID.randomUUID();
        String oldToken = "old-token";
        HealerInviteLink invite = HealerInviteLink.builder()
                .id(inviteId)
                .token(oldToken)
                .email("resend@example.com")
                .role(Role.HEALER)
                .clinicId(clinicId)
                .invitedByHealerId(invitedByHealerId)
                .status(HealerInviteLink.Status.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        when(inviteRepository.findById(inviteId)).thenReturn(Optional.of(invite));
        when(inviteRepository.save(any(HealerInviteLink.class))).thenAnswer(inv -> inv.getArgument(0));

        InviteResponse response = inviteService.resend(inviteId, null);

        assertNotEquals(oldToken, invite.getToken());
        assertEquals("PENDING", response.status());
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void resend_acceptedInvite_throwsIAE() {
        UUID inviteId = UUID.randomUUID();
        HealerInviteLink invite = HealerInviteLink.builder()
                .id(inviteId)
                .token("t")
                .email("e@example.com")
                .role(Role.HEALER)
                .clinicId(clinicId)
                .invitedByHealerId(invitedByHealerId)
                .status(HealerInviteLink.Status.ACCEPTED)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        when(inviteRepository.findById(inviteId)).thenReturn(Optional.of(invite));

        assertThrows(IllegalArgumentException.class, () -> inviteService.resend(inviteId, null));
        verify(inviteRepository, never()).save(any());
    }

    @Test
    void resend_revokedInvite_throwsIAE() {
        UUID inviteId = UUID.randomUUID();
        HealerInviteLink invite = HealerInviteLink.builder()
                .id(inviteId)
                .token("t")
                .email("e@example.com")
                .role(Role.HEALER)
                .clinicId(clinicId)
                .invitedByHealerId(invitedByHealerId)
                .status(HealerInviteLink.Status.REVOKED)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        when(inviteRepository.findById(inviteId)).thenReturn(Optional.of(invite));

        assertThrows(IllegalArgumentException.class, () -> inviteService.resend(inviteId, null));
    }

    @Test
    void resend_wrongClinicFilter_throwsForbidden() {
        UUID inviteId = UUID.randomUUID();
        UUID otherClinic = UUID.randomUUID();
        HealerInviteLink invite = HealerInviteLink.builder()
                .id(inviteId)
                .token("t")
                .email("e@example.com")
                .role(Role.HEALER)
                .clinicId(clinicId)
                .invitedByHealerId(invitedByHealerId)
                .status(HealerInviteLink.Status.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        when(inviteRepository.findById(inviteId)).thenReturn(Optional.of(invite));

        assertThrows(ResponseStatusException.class, () -> inviteService.resend(inviteId, otherClinic));
        verify(inviteRepository, never()).save(any());
    }

    // ─── revoke ─────────────────────────────────────────────────

    @Test
    void revoke_pendingInvite_flipsToRevoked_noMailSent() {
        UUID inviteId = UUID.randomUUID();
        HealerInviteLink invite = HealerInviteLink.builder()
                .id(inviteId)
                .token("t")
                .email("e@example.com")
                .role(Role.HEALER)
                .clinicId(clinicId)
                .invitedByHealerId(invitedByHealerId)
                .status(HealerInviteLink.Status.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        when(inviteRepository.findById(inviteId)).thenReturn(Optional.of(invite));
        when(inviteRepository.save(any(HealerInviteLink.class))).thenAnswer(inv -> inv.getArgument(0));

        InviteResponse response = inviteService.revoke(inviteId, null);

        assertEquals(HealerInviteLink.Status.REVOKED, invite.getStatus());
        assertEquals("REVOKED", response.status());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void revoke_nonPendingInvite_throwsIAE() {
        UUID inviteId = UUID.randomUUID();
        HealerInviteLink invite = HealerInviteLink.builder()
                .id(inviteId)
                .token("t")
                .email("e@example.com")
                .role(Role.HEALER)
                .clinicId(clinicId)
                .invitedByHealerId(invitedByHealerId)
                .status(HealerInviteLink.Status.ACCEPTED)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        when(inviteRepository.findById(inviteId)).thenReturn(Optional.of(invite));

        assertThrows(IllegalArgumentException.class, () -> inviteService.revoke(inviteId, null));
        verify(inviteRepository, never()).save(any());
    }

    @Test
    void revoke_wrongClinicFilter_throwsForbidden() {
        UUID inviteId = UUID.randomUUID();
        UUID otherClinic = UUID.randomUUID();
        HealerInviteLink invite = HealerInviteLink.builder()
                .id(inviteId)
                .token("t")
                .email("e@example.com")
                .role(Role.HEALER)
                .clinicId(clinicId)
                .invitedByHealerId(invitedByHealerId)
                .status(HealerInviteLink.Status.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        when(inviteRepository.findById(inviteId)).thenReturn(Optional.of(invite));

        assertThrows(ResponseStatusException.class, () -> inviteService.revoke(inviteId, otherClinic));
    }

    // ─── acceptInvite ───────────────────────────────────────────

    @Test
    void acceptInvite_happyPath_createsHealerAndCreditAccountAndSetsAuthCookies() {
        String token = "valid-token";
        UUID inviteId = UUID.randomUUID();
        HealerInviteLink invite = HealerInviteLink.builder()
                .id(inviteId)
                .token(token)
                .email("invitee@example.com")
                .role(Role.HEALER)
                .clinicId(clinicId)
                .invitedByHealerId(invitedByHealerId)
                .status(HealerInviteLink.Status.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
        Clinic clinic = Clinic.builder().code("CHNY01").name("Chennai Clinic").build();
        UUID savedHealerId = UUID.randomUUID();

        when(inviteRepository.findByTokenAndStatusAndExpiresAtAfter(
                eq(token), eq(HealerInviteLink.Status.PENDING), any()))
                .thenReturn(Optional.of(invite));
        when(inviteRepository.markAccepted(eq(inviteId), any())).thenReturn(1);
        when(clinicRepository.findById(clinicId)).thenReturn(Optional.of(clinic));
        when(healerRepository.existsByEmail(invite.getEmail())).thenReturn(false);
        when(passwordEncoder.encode("password1")).thenReturn("hashedPw");

        ArgumentCaptor<Healer> healerCaptor = ArgumentCaptor.forClass(Healer.class);
        when(healerRepository.save(healerCaptor.capture())).thenAnswer(inv -> {
            Healer h = inv.getArgument(0);
            h.setId(savedHealerId);
            return h;
        });

        AcceptInviteRequest request = new AcceptInviteRequest("New Healer", "password1");
        HealerResponse response = inviteService.acceptInvite(token, request, httpServletResponse);

        assertEquals(savedHealerId, response.id());
        assertEquals("invitee@example.com", response.email());
        verify(creditService).createAccount(OwnerType.HEALER, savedHealerId);
        verify(cookieUtil).setAuthCookies(any(), any(), any());
        Healer savedHealer = healerCaptor.getValue();
        assertEquals("New Healer", savedHealer.getFullName());
        assertEquals("hashedPw", savedHealer.getPasswordHash());
    }

    @Test
    void acceptInvite_expired_throwsTokenExpiredException_noHealerSaved() {
        String token = "expired-token";
        when(inviteRepository.findByTokenAndStatusAndExpiresAtAfter(
                eq(token),
                eq(HealerInviteLink.Status.PENDING), any()))
                .thenReturn(Optional.empty());

        AcceptInviteRequest request = new AcceptInviteRequest("New Healer", "password1");

        assertThrows(TokenExpiredException.class,
                () -> inviteService.acceptInvite(token, request, httpServletResponse));

        verify(healerRepository, never()).save(any());
        verify(creditService, never()).createAccount(any(), any());
    }

    @Test
    void acceptInvite_revoked_throwsTokenExpiredException_sameMessage() {
        String token = "revoked-token";
        // A revoked invite no longer matches the PENDING-only lookup — same as expired.
        when(inviteRepository.findByTokenAndStatusAndExpiresAtAfter(
                eq(token),
                eq(HealerInviteLink.Status.PENDING), any()))
                .thenReturn(Optional.empty());

        AcceptInviteRequest request = new AcceptInviteRequest("New Healer", "password1");

        TokenExpiredException ex = assertThrows(TokenExpiredException.class,
                () -> inviteService.acceptInvite(token, request, httpServletResponse));
        assertEquals("This invite link is invalid, expired, or already used.", ex.getMessage());
        verify(healerRepository, never()).save(any());
    }

    @Test
    void acceptInvite_replay_alreadyAcceptedToken_throwsTokenExpiredException_noSecondActivation() {
        String token = "already-used-token";
        // Post-accept state: the PENDING-only lookup returns empty for an already-ACCEPTED token.
        when(inviteRepository.findByTokenAndStatusAndExpiresAtAfter(
                eq(token),
                eq(HealerInviteLink.Status.PENDING), any()))
                .thenReturn(Optional.empty());

        AcceptInviteRequest request = new AcceptInviteRequest("New Healer", "password1");

        assertThrows(TokenExpiredException.class,
                () -> inviteService.acceptInvite(token, request, httpServletResponse));

        verifyNoInteractions(healerRepository);
        verify(creditService, never()).createAccount(any(), any());
    }

    @Test
    void acceptInvite_healerExistsInAnotherClinic_throwsTokenExpiredException_noSecondActivation() {
        // Regression test for Phase 22 code review Critical finding: the same email must
        // not be activatable in a second clinic once it exists anywhere in the system.
        String token = "valid-token-2";
        UUID inviteId = UUID.randomUUID();
        HealerInviteLink invite = HealerInviteLink.builder()
                .id(inviteId)
                .token(token)
                .email("already-elsewhere@example.com")
                .role(Role.HEALER)
                .clinicId(clinicId)
                .invitedByHealerId(invitedByHealerId)
                .status(HealerInviteLink.Status.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
        Clinic clinic = Clinic.builder().code("CHNY01").name("Chennai Clinic").build();

        when(inviteRepository.findByTokenAndStatusAndExpiresAtAfter(
                eq(token), eq(HealerInviteLink.Status.PENDING), any()))
                .thenReturn(Optional.of(invite));
        when(inviteRepository.markAccepted(eq(inviteId), any())).thenReturn(1);
        when(clinicRepository.findById(clinicId)).thenReturn(Optional.of(clinic));
        when(healerRepository.existsByEmail(invite.getEmail())).thenReturn(true);

        AcceptInviteRequest request = new AcceptInviteRequest("New Healer", "password1");

        assertThrows(TokenExpiredException.class,
                () -> inviteService.acceptInvite(token, request, httpServletResponse));

        verify(healerRepository, never()).save(any());
        verify(creditService, never()).createAccount(any(), any());
    }

    @Test
    void acceptInvite_concurrentRace_loserGetsTokenExpiredException() {
        String token = "race-token";
        UUID inviteId = UUID.randomUUID();
        HealerInviteLink invite = HealerInviteLink.builder()
                .id(inviteId)
                .token(token)
                .email("race@example.com")
                .role(Role.HEALER)
                .clinicId(clinicId)
                .invitedByHealerId(invitedByHealerId)
                .status(HealerInviteLink.Status.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        when(inviteRepository.findByTokenAndStatusAndExpiresAtAfter(
                eq(token),
                eq(HealerInviteLink.Status.PENDING), any()))
                .thenReturn(Optional.of(invite));
        // The loser: markAccepted returns 0 because a concurrent accept already flipped the row.
        when(inviteRepository.markAccepted(eq(inviteId), any())).thenReturn(0);

        AcceptInviteRequest request = new AcceptInviteRequest("New Healer", "password1");

        assertThrows(TokenExpiredException.class,
                () -> inviteService.acceptInvite(token, request, httpServletResponse));

        verify(healerRepository, never()).save(any());
        verify(creditService, never()).createAccount(any(), any());
    }
}
