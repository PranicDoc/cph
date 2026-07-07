package com.pranicdoc.cph.auth.controller;

import com.pranicdoc.cph.admin.AdminAuthz;
import com.pranicdoc.cph.auth.dto.InviteCreateRequest;
import com.pranicdoc.cph.auth.dto.InviteResponse;
import com.pranicdoc.cph.auth.service.impl.InviteService;
import com.pranicdoc.cph.common.enums.Role;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Plain Mockito unit test — deliberately not a Spring MVC slice test, per STATE.md's
 * documented broken WebMvcTest + SecurityConfig import pattern (missing OAuth2 bean mocks).
 */
@ExtendWith(MockitoExtension.class)
class AdminInviteControllerTest {

    @Mock
    private InviteService inviteService;

    @Mock
    private AdminAuthz adminAuthz;

    @Mock
    private SecurityContextUtil securityContextUtil;

    @InjectMocks
    private AdminInviteController controller;

    private static final UUID ADMIN_CLINIC_ID = UUID.randomUUID();
    private static final UUID SUPADM_CHOSEN_CLINIC_ID = UUID.randomUUID();
    private static final UUID CALLER_HEALER_ID = UUID.randomUUID();

    // ─── create() ────────────────────────────────────────────────

    @Test
    void create_asAdmin_ignoresBodyClinicIdAndUsesOwnClinic() {
        UUID bodyClinicId = UUID.randomUUID(); // deliberately different from ADMIN_CLINIC_ID
        InviteCreateRequest request = new InviteCreateRequest("healer@example.com", Role.HEALER, bodyClinicId);

        when(adminAuthz.effectiveClinicFilter()).thenReturn(ADMIN_CLINIC_ID);
        when(securityContextUtil.getCurrentHealerId()).thenReturn(CALLER_HEALER_ID);
        InviteResponse expected = sampleResponse(ADMIN_CLINIC_ID);
        when(inviteService.createInvite(eq("healer@example.com"), eq(Role.HEALER), eq(ADMIN_CLINIC_ID), eq(CALLER_HEALER_ID)))
                .thenReturn(expected);

        ResponseEntity<InviteResponse> response = controller.create(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(inviteService).createInvite("healer@example.com", Role.HEALER, ADMIN_CLINIC_ID, CALLER_HEALER_ID);
        verify(adminAuthz, never()).assertClinicAccess(any());
    }

    @Test
    void create_asSupadm_missingClinicId_throwsIllegalArgumentException() {
        InviteCreateRequest request = new InviteCreateRequest("healer@example.com", Role.HEALER, null);

        when(adminAuthz.effectiveClinicFilter()).thenReturn(null);

        assertThatThrownBy(() -> controller.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clinicId is required for SUPADM invites");

        verifyNoInteractions(inviteService);
    }

    @Test
    void create_asSupadm_withClinicId_assertsAccessAndCreatesInvite() {
        InviteCreateRequest request = new InviteCreateRequest("healer@example.com", Role.HEALER, SUPADM_CHOSEN_CLINIC_ID);

        when(adminAuthz.effectiveClinicFilter()).thenReturn(null);
        when(securityContextUtil.getCurrentHealerId()).thenReturn(CALLER_HEALER_ID);
        InviteResponse expected = sampleResponse(SUPADM_CHOSEN_CLINIC_ID);
        when(inviteService.createInvite(eq("healer@example.com"), eq(Role.HEALER), eq(SUPADM_CHOSEN_CLINIC_ID), eq(CALLER_HEALER_ID)))
                .thenReturn(expected);

        ResponseEntity<InviteResponse> response = controller.create(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(adminAuthz).assertClinicAccess(SUPADM_CHOSEN_CLINIC_ID);
        verify(inviteService).createInvite("healer@example.com", Role.HEALER, SUPADM_CHOSEN_CLINIC_ID, CALLER_HEALER_ID);
    }

    @Test
    void adminCannotInviteSupadm() {
        InviteCreateRequest request = new InviteCreateRequest("healer@example.com", Role.SUPADM, null);

        when(securityContextUtil.getCurrentRole()).thenReturn("ADMIN");

        assertThatThrownBy(() -> controller.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verifyNoInteractions(inviteService);
        verify(adminAuthz, never()).effectiveClinicFilter();
    }

    @Test
    void supadmCanInviteSupadm() {
        InviteCreateRequest request = new InviteCreateRequest("healer@example.com", Role.SUPADM, SUPADM_CHOSEN_CLINIC_ID);

        when(securityContextUtil.getCurrentRole()).thenReturn("SUPADM");
        when(adminAuthz.effectiveClinicFilter()).thenReturn(null);
        when(securityContextUtil.getCurrentHealerId()).thenReturn(CALLER_HEALER_ID);
        InviteResponse expected = sampleResponse(SUPADM_CHOSEN_CLINIC_ID);
        when(inviteService.createInvite(eq("healer@example.com"), eq(Role.SUPADM), eq(SUPADM_CHOSEN_CLINIC_ID), eq(CALLER_HEALER_ID)))
                .thenReturn(expected);

        ResponseEntity<InviteResponse> response = controller.create(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(inviteService).createInvite("healer@example.com", Role.SUPADM, SUPADM_CHOSEN_CLINIC_ID, CALLER_HEALER_ID);
    }

    // ─── list() ──────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void list_asAdmin_filtersByOwnClinicId() {
        when(adminAuthz.effectiveClinicFilter()).thenReturn(ADMIN_CLINIC_ID);
        Page<InviteResponse> page = mock(Page.class);
        when(inviteService.list(eq(ADMIN_CLINIC_ID), any(Pageable.class))).thenReturn(page);

        Page<InviteResponse> result = controller.list(0, 20);

        assertThat(result).isEqualTo(page);
        verify(inviteService).list(eq(ADMIN_CLINIC_ID), any(Pageable.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void list_asSupadm_passesNullFilter() {
        when(adminAuthz.effectiveClinicFilter()).thenReturn(null);
        Page<InviteResponse> page = mock(Page.class);
        when(inviteService.list(isNull(), any(Pageable.class))).thenReturn(page);

        Page<InviteResponse> result = controller.list(0, 20);

        assertThat(result).isEqualTo(page);
        verify(inviteService).list(isNull(), any(Pageable.class));
    }

    // ─── resend() / revoke() ─────────────────────────────────────

    @Test
    void resend_delegatesWithEffectiveClinicFilter() {
        UUID inviteId = UUID.randomUUID();
        when(adminAuthz.effectiveClinicFilter()).thenReturn(ADMIN_CLINIC_ID);
        InviteResponse expected = sampleResponse(ADMIN_CLINIC_ID);
        when(inviteService.resend(inviteId, ADMIN_CLINIC_ID)).thenReturn(expected);

        ResponseEntity<InviteResponse> response = controller.resend(inviteId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(inviteService).resend(inviteId, ADMIN_CLINIC_ID);
    }

    @Test
    void revoke_delegatesWithEffectiveClinicFilter() {
        UUID inviteId = UUID.randomUUID();
        when(adminAuthz.effectiveClinicFilter()).thenReturn(null);
        InviteResponse expected = sampleResponse(SUPADM_CHOSEN_CLINIC_ID);
        when(inviteService.revoke(inviteId, null)).thenReturn(expected);

        ResponseEntity<InviteResponse> response = controller.revoke(inviteId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(inviteService).revoke(inviteId, null);
    }

    private static InviteResponse sampleResponse(UUID clinicId) {
        return new InviteResponse(
                UUID.randomUUID(), "healer@example.com", Role.HEALER.name(), clinicId,
                "PENDING", Instant.now(), Instant.now().plusSeconds(3600));
    }
}
