package com.pranicdoc.cph.auth.service;

import com.pranicdoc.cph.auth.dto.AcceptInviteRequest;
import com.pranicdoc.cph.auth.dto.HealerResponse;
import com.pranicdoc.cph.auth.dto.InviteResponse;
import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.model.HealerInviteLink;
import com.pranicdoc.cph.auth.repository.HealerInviteLinkRepository;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.auth.service.impl.InviteService;
import com.pranicdoc.cph.common.dto.ApiResponse;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    private final UUID invitedByHealerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        inviteService = new InviteService(inviteRepository, healerRepository,
                mailSender, passwordEncoder, jwtService, cookieUtil, creditService);
        ReflectionTestUtils.setField(inviteService, "inviteUrl", "http://localhost:3000/accept-invite");
        ReflectionTestUtils.setField(inviteService, "inviteExpiryHours", 72);
    }

    // ─── createInvite ───────────────────────────────────────────

    @Test
    void createInvite_noExistingPending_savesNewRowAndSendsEmail() {
        String email = "newhealer@example.com";
        when(healerRepository.existsByEmail(email)).thenReturn(false);
        when(inviteRepository.findByEmailAndStatus(email, HealerInviteLink.Status.PENDING))
                .thenReturn(Optional.empty());

        ArgumentCaptor<HealerInviteLink> captor = ArgumentCaptor.forClass(HealerInviteLink.class);
        when(inviteRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        InviteResponse response = inviteService.createInvite(email, invitedByHealerId);

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
                .invitedByHealerId(invitedByHealerId)
                .status(HealerInviteLink.Status.PENDING)
                .expiresAt(Instant.now().plus(10, ChronoUnit.HOURS))
                .build();

        when(healerRepository.existsByEmail(email)).thenReturn(false);
        when(inviteRepository.findByEmailAndStatus(email, HealerInviteLink.Status.PENDING))
                .thenReturn(Optional.of(existing));
        ArgumentCaptor<HealerInviteLink> captor = ArgumentCaptor.forClass(HealerInviteLink.class);
        when(inviteRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        InviteResponse response = inviteService.createInvite(email, invitedByHealerId);

        // Only ONE save call — the existing row is mutated and re-saved, no second row inserted.
        verify(inviteRepository, times(1)).save(any(HealerInviteLink.class));
        // 41-04: the rotate-existing-PENDING-row branch must still pin Role.HEALER on the persisted row —
        // this is the branch a three-argument-era regression would most easily miss.
        assertEquals(Role.HEALER, captor.getValue().getRole());
        assertNotEquals(oldToken, existing.getToken());
        assertEquals(email, response.email());
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void createInvite_healerAlreadyExists_throwsIAE_noSaveNoMail() {
        String email = "already@example.com";
        when(healerRepository.existsByEmail(email)).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> inviteService.createInvite(email, invitedByHealerId));

        verify(inviteRepository, never()).save(any());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void createInvite_mailSendThrows_inviteStillPersists() {
        String email = "mailfail@example.com";
        when(healerRepository.existsByEmail(email)).thenReturn(false);
        when(inviteRepository.findByEmailAndStatus(email, HealerInviteLink.Status.PENDING))
                .thenReturn(Optional.empty());
        when(inviteRepository.save(any(HealerInviteLink.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        InviteResponse response = inviteService.createInvite(email, invitedByHealerId);

        assertEquals(email, response.email());
        verify(inviteRepository, times(1)).save(any(HealerInviteLink.class));
    }

    // ─── requestSelfSignup ──────────────────────────────────────

    @Test
    void requestSelfSignup_existingAccount_returnsGenericNoEmail() {
        String email = "existing-healer@example.com";
        when(healerRepository.existsByEmail(email)).thenReturn(true);

        ApiResponse<String> response = inviteService.requestSelfSignup(email);

        assertTrue(response.success());
        assertEquals("If that email is eligible, a verification link has been sent.", response.message());
        verify(inviteRepository, never()).save(any());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void requestSelfSignup_pendingAdminInvite_returnsGenericNoEmail() {
        String email = "collides-with-admin-invite@example.com";
        when(healerRepository.existsByEmail(email)).thenReturn(false);
        when(inviteRepository.existsByEmailAndSourceAndStatus(
                email, HealerInviteLink.Source.ADMIN_INVITE, HealerInviteLink.Status.PENDING))
                .thenReturn(true);

        ApiResponse<String> response = inviteService.requestSelfSignup(email);

        assertTrue(response.success());
        assertEquals("If that email is eligible, a verification link has been sent.", response.message());
        verify(inviteRepository, never()).save(any());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void requestSelfSignup_newEmail_savesSelfSignupRowAndSendsEmail() {
        String email = "new-self-signup@example.com";
        when(healerRepository.existsByEmail(email)).thenReturn(false);
        when(inviteRepository.existsByEmailAndSourceAndStatus(
                email, HealerInviteLink.Source.ADMIN_INVITE, HealerInviteLink.Status.PENDING))
                .thenReturn(false);
        when(inviteRepository.findByEmailAndSourceAndStatus(
                email, HealerInviteLink.Source.SELF_SIGNUP, HealerInviteLink.Status.PENDING))
                .thenReturn(Optional.empty());

        ArgumentCaptor<HealerInviteLink> captor = ArgumentCaptor.forClass(HealerInviteLink.class);
        when(inviteRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        ApiResponse<String> response = inviteService.requestSelfSignup(email);

        assertTrue(response.success());
        HealerInviteLink saved = captor.getValue();
        assertEquals(HealerInviteLink.Source.SELF_SIGNUP, saved.getSource());
        assertNull(saved.getInvitedByHealerId());
        assertEquals(Role.HEALER, saved.getRole());
        assertEquals(HealerInviteLink.Status.PENDING, saved.getStatus());
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void requestSelfSignup_existingPendingToken_rotatesInPlace() {
        String email = "resubmits-self-signup@example.com";
        String oldToken = "old-self-signup-token";
        HealerInviteLink existing = HealerInviteLink.builder()
                .id(UUID.randomUUID())
                .token(oldToken)
                .email(email)
                .role(Role.HEALER)
                .source(HealerInviteLink.Source.SELF_SIGNUP)
                .status(HealerInviteLink.Status.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        when(healerRepository.existsByEmail(email)).thenReturn(false);
        when(inviteRepository.existsByEmailAndSourceAndStatus(
                email, HealerInviteLink.Source.ADMIN_INVITE, HealerInviteLink.Status.PENDING))
                .thenReturn(false);
        when(inviteRepository.findByEmailAndSourceAndStatus(
                email, HealerInviteLink.Source.SELF_SIGNUP, HealerInviteLink.Status.PENDING))
                .thenReturn(Optional.of(existing));
        when(inviteRepository.save(any(HealerInviteLink.class))).thenAnswer(inv -> inv.getArgument(0));

        ApiResponse<String> response = inviteService.requestSelfSignup(email);

        assertTrue(response.success());
        // Only ONE save call — the existing row is mutated and re-saved, no second row inserted.
        verify(inviteRepository, times(1)).save(any(HealerInviteLink.class));
        assertNotEquals(oldToken, existing.getToken());
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    /**
     * Negative branch of the {@code requestSelfSignup} HEALER-only invariant (Phase 41
     * code review WR-01). The rotate-existing-PENDING-row branch never touches {@code role},
     * so a self-signup row persisted before the retirement carrying {@link Role#SUPADM} —
     * the only non-HEALER value left after the enum shrink — is the realistic way a
     * non-HEALER role could still reach the {@code save(...)}. The single-sourced
     * {@code assertInviteMintsHealer} guard must throw {@link IllegalStateException} before
     * any row is persisted or any email is sent. Mirrors
     * {@code acceptInvite_staleInviteWithSupadmRole_throws}.
     */
    @Test
    void requestSelfSignup_staleSelfSignupRowWithSupadmRole_throws() {
        String email = "stale-self-signup-elevated@example.com";
        HealerInviteLink existing = HealerInviteLink.builder()
                .id(UUID.randomUUID())
                .token("old-self-signup-token")
                .email(email)
                .role(Role.SUPADM)
                .source(HealerInviteLink.Source.SELF_SIGNUP)
                .status(HealerInviteLink.Status.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        when(healerRepository.existsByEmail(email)).thenReturn(false);
        when(inviteRepository.existsByEmailAndSourceAndStatus(
                email, HealerInviteLink.Source.ADMIN_INVITE, HealerInviteLink.Status.PENDING))
                .thenReturn(false);
        when(inviteRepository.findByEmailAndSourceAndStatus(
                email, HealerInviteLink.Source.SELF_SIGNUP, HealerInviteLink.Status.PENDING))
                .thenReturn(Optional.of(existing));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> inviteService.requestSelfSignup(email));
        assertTrue(ex.getMessage().contains("HEALER"),
                "invariant message must name the survivor tier HEALER — got: " + ex.getMessage());

        verify(inviteRepository, never()).save(any());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    // ─── acceptInvite (SELF_SIGNUP) ─────────────────────────────

    @Test
    void acceptInvite_selfSignupSource_noClinicCreated_healerRoleForced() {
        String token = "self-signup-accept-token";
        UUID inviteId = UUID.randomUUID();
        HealerInviteLink invite = HealerInviteLink.builder()
                .id(inviteId)
                .token(token)
                .email("solo-healer@example.com")
                .role(Role.HEALER)
                .source(HealerInviteLink.Source.SELF_SIGNUP)
                .status(HealerInviteLink.Status.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
        UUID savedHealerId = UUID.randomUUID();

        when(inviteRepository.findByTokenAndStatusAndExpiresAtAfter(
                eq(token), eq(HealerInviteLink.Status.PENDING), any()))
                .thenReturn(Optional.of(invite));
        when(inviteRepository.markAccepted(eq(inviteId), any())).thenReturn(1);
        when(healerRepository.existsByEmail(invite.getEmail())).thenReturn(false);
        when(passwordEncoder.encode("password1")).thenReturn("hashedPw");

        ArgumentCaptor<Healer> healerCaptor = ArgumentCaptor.forClass(Healer.class);
        when(healerRepository.save(healerCaptor.capture())).thenAnswer(inv -> {
            Healer h = inv.getArgument(0);
            h.setId(savedHealerId);
            return h;
        });

        AcceptInviteRequest request = new AcceptInviteRequest("Solo Healer", "password1", "555-1234");
        HealerResponse response = inviteService.acceptInvite(token, request, httpServletResponse);

        Healer savedHealer = healerCaptor.getValue();
        assertEquals(Role.HEALER, savedHealer.getRole());

        assertEquals(savedHealerId, response.id());
        verify(creditService, never()).createAccount(eq(OwnerType.CLINIC), any());
        verify(creditService).createAccount(OwnerType.HEALER, savedHealerId);
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
                .invitedByHealerId(invitedByHealerId)
                .status(HealerInviteLink.Status.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        when(inviteRepository.findById(inviteId)).thenReturn(Optional.of(invite));
        when(inviteRepository.save(any(HealerInviteLink.class))).thenAnswer(inv -> inv.getArgument(0));

        InviteResponse response = inviteService.resend(inviteId);

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
                .invitedByHealerId(invitedByHealerId)
                .status(HealerInviteLink.Status.ACCEPTED)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        when(inviteRepository.findById(inviteId)).thenReturn(Optional.of(invite));

        assertThrows(IllegalArgumentException.class, () -> inviteService.resend(inviteId));
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
                .invitedByHealerId(invitedByHealerId)
                .status(HealerInviteLink.Status.REVOKED)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        when(inviteRepository.findById(inviteId)).thenReturn(Optional.of(invite));

        assertThrows(IllegalArgumentException.class, () -> inviteService.resend(inviteId));
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
                .invitedByHealerId(invitedByHealerId)
                .status(HealerInviteLink.Status.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        when(inviteRepository.findById(inviteId)).thenReturn(Optional.of(invite));
        when(inviteRepository.save(any(HealerInviteLink.class))).thenAnswer(inv -> inv.getArgument(0));

        InviteResponse response = inviteService.revoke(inviteId);

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
                .invitedByHealerId(invitedByHealerId)
                .status(HealerInviteLink.Status.ACCEPTED)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        when(inviteRepository.findById(inviteId)).thenReturn(Optional.of(invite));

        assertThrows(IllegalArgumentException.class, () -> inviteService.revoke(inviteId));
        verify(inviteRepository, never()).save(any());
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
                .invitedByHealerId(invitedByHealerId)
                .status(HealerInviteLink.Status.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
        UUID savedHealerId = UUID.randomUUID();

        when(inviteRepository.findByTokenAndStatusAndExpiresAtAfter(
                eq(token), eq(HealerInviteLink.Status.PENDING), any()))
                .thenReturn(Optional.of(invite));
        when(inviteRepository.markAccepted(eq(inviteId), any())).thenReturn(1);
        when(healerRepository.existsByEmail(invite.getEmail())).thenReturn(false);
        when(passwordEncoder.encode("password1")).thenReturn("hashedPw");

        ArgumentCaptor<Healer> healerCaptor = ArgumentCaptor.forClass(Healer.class);
        when(healerRepository.save(healerCaptor.capture())).thenAnswer(inv -> {
            Healer h = inv.getArgument(0);
            h.setId(savedHealerId);
            return h;
        });

        AcceptInviteRequest request = new AcceptInviteRequest("New Healer", "password1", null);
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

        AcceptInviteRequest request = new AcceptInviteRequest("New Healer", "password1", null);

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

        AcceptInviteRequest request = new AcceptInviteRequest("New Healer", "password1", null);

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

        AcceptInviteRequest request = new AcceptInviteRequest("New Healer", "password1", null);

        assertThrows(TokenExpiredException.class,
                () -> inviteService.acceptInvite(token, request, httpServletResponse));

        verifyNoInteractions(healerRepository);
        verify(creditService, never()).createAccount(any(), any());
    }

    @Test
    void acceptInvite_healerExistsGlobally_throwsTokenExpiredException_noSecondActivation() {
        // Regression test for Phase 22 code review Critical finding, preserved post-clinic-removal:
        // the same email must not be activatable twice once it exists anywhere in the system.
        String token = "valid-token-2";
        UUID inviteId = UUID.randomUUID();
        HealerInviteLink invite = HealerInviteLink.builder()
                .id(inviteId)
                .token(token)
                .email("already-elsewhere@example.com")
                .role(Role.HEALER)
                .invitedByHealerId(invitedByHealerId)
                .status(HealerInviteLink.Status.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        when(inviteRepository.findByTokenAndStatusAndExpiresAtAfter(
                eq(token), eq(HealerInviteLink.Status.PENDING), any()))
                .thenReturn(Optional.of(invite));
        when(inviteRepository.markAccepted(eq(inviteId), any())).thenReturn(1);
        when(healerRepository.existsByEmail(invite.getEmail())).thenReturn(true);

        AcceptInviteRequest request = new AcceptInviteRequest("New Healer", "password1", null);

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

        AcceptInviteRequest request = new AcceptInviteRequest("New Healer", "password1", null);

        assertThrows(TokenExpiredException.class,
                () -> inviteService.acceptInvite(token, request, httpServletResponse));

        verify(healerRepository, never()).save(any());
        verify(creditService, never()).createAccount(any(), any());
    }

    // ─── acceptInvite — role retirement invariant (Phase 41 / MIGRATE-04 / D-05, D-06) ───

    /**
     * ADMIN_INVITE-source branch of {@code acceptInvite}: the ternary in acceptInvite keeps
     * {@code invite.getRole()} for a non-self-signup invite, so this is the branch that proves
     * an admin invite still mints a plain {@link Role#HEALER} account after the Phase 41 enum
     * shrink. Complements {@code acceptInvite_selfSignupSource_noClinicCreated_healerRoleForced},
     * which locks the SELF_SIGNUP half.
     */
    @Test
    void acceptInvite_adminInviteSource_forcesHealer() {
        String token = "admin-invite-accept-token";
        UUID inviteId = UUID.randomUUID();
        HealerInviteLink invite = HealerInviteLink.builder()
                .id(inviteId)
                .token(token)
                .email("admin-invited@example.com")
                .role(Role.HEALER)
                .source(HealerInviteLink.Source.ADMIN_INVITE)
                .status(HealerInviteLink.Status.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
        UUID savedHealerId = UUID.randomUUID();

        when(inviteRepository.findByTokenAndStatusAndExpiresAtAfter(
                eq(token), eq(HealerInviteLink.Status.PENDING), any()))
                .thenReturn(Optional.of(invite));
        when(inviteRepository.markAccepted(eq(inviteId), any())).thenReturn(1);
        when(healerRepository.existsByEmail(invite.getEmail())).thenReturn(false);
        when(passwordEncoder.encode("password1")).thenReturn("hashedPw");

        ArgumentCaptor<Healer> healerCaptor = ArgumentCaptor.forClass(Healer.class);
        when(healerRepository.save(healerCaptor.capture())).thenAnswer(inv -> {
            Healer h = inv.getArgument(0);
            h.setId(savedHealerId);
            return h;
        });

        AcceptInviteRequest request = new AcceptInviteRequest("Admin Invited", "password1", "555-9876");
        HealerResponse response = inviteService.acceptInvite(token, request, httpServletResponse);

        Healer savedHealer = healerCaptor.getValue();
        assertEquals(Role.HEALER, savedHealer.getRole());
        assertEquals(savedHealerId, response.id());
        verify(creditService).createAccount(OwnerType.HEALER, savedHealerId);
    }

    /**
     * Negative branch of the {@code acceptInvite} retirement invariant (PD-41-01).
     * {@code healer_invite_link.role} has no DB CHECK constraint (D-05), so a PENDING row
     * written before this phase is the one realistic way a non-HEALER role could still reach
     * {@code acceptInvite}; this is that guard's regression test. A stale ADMIN_INVITE row
     * carrying {@link Role#SUPADM} — the only non-HEALER value that still exists after the
     * enum shrink — must throw {@link IllegalStateException} before any {@code Healer} (or
     * credit account) is persisted.
     */
    @Test
    void acceptInvite_staleInviteWithSupadmRole_throws() {
        String token = "stale-pre-retirement-token";
        UUID inviteId = UUID.randomUUID();
        HealerInviteLink invite = HealerInviteLink.builder()
                .id(inviteId)
                .token(token)
                .email("stale-elevated@example.com")
                .role(Role.SUPADM)
                .source(HealerInviteLink.Source.ADMIN_INVITE)
                .status(HealerInviteLink.Status.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        when(inviteRepository.findByTokenAndStatusAndExpiresAtAfter(
                eq(token), eq(HealerInviteLink.Status.PENDING), any()))
                .thenReturn(Optional.of(invite));
        when(inviteRepository.markAccepted(eq(inviteId), any())).thenReturn(1);
        when(healerRepository.existsByEmail(invite.getEmail())).thenReturn(false);

        AcceptInviteRequest request = new AcceptInviteRequest("Stale Elevated", "password1", null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> inviteService.acceptInvite(token, request, httpServletResponse));
        assertTrue(ex.getMessage().contains("HEALER"),
                "invariant message must name the survivor tier HEALER — got: " + ex.getMessage());

        verify(healerRepository, never()).save(any());
        verify(creditService, never()).createAccount(any(), any());
    }
}
