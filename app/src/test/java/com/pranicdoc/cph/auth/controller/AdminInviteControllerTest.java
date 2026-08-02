package com.pranicdoc.cph.auth.controller;

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
import static org.mockito.Mockito.*;

/**
 * Plain Mockito unit test — deliberately not a Spring MVC slice test, per STATE.md's
 * documented broken WebMvcTest + SecurityConfig import pattern (missing OAuth2 bean mocks).
 *
 * Solo-healer world (CUTOVER-05/D-26-02): invite visibility is no longer clinic-scoped —
 * AdminAuthz's clinic-filter plumbing was removed from the controller entirely. The only
 * remaining authorization distinction covered here is role-based (ADMIN cannot invite SUPADM).
 */
@ExtendWith(MockitoExtension.class)
class AdminInviteControllerTest {

    @Mock
    private InviteService inviteService;

    @Mock
    private SecurityContextUtil securityContextUtil;

    @InjectMocks
    private AdminInviteController controller;

    private static final UUID CALLER_HEALER_ID = UUID.randomUUID();

    // ─── create() ────────────────────────────────────────────────

    @Test
    void create_asAdmin_createsClinicFreeInvite() {
        InviteCreateRequest request = new InviteCreateRequest("healer@example.com", Role.HEALER);

        when(securityContextUtil.getCurrentHealerId()).thenReturn(CALLER_HEALER_ID);
        InviteResponse expected = sampleResponse();
        when(inviteService.createInvite(eq("healer@example.com"), eq(Role.HEALER), eq(CALLER_HEALER_ID)))
                .thenReturn(expected);

        ResponseEntity<InviteResponse> response = controller.create(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(inviteService).createInvite("healer@example.com", Role.HEALER, CALLER_HEALER_ID);
    }

    @Test
    void create_asSupadm_createsClinicFreeInvite() {
        InviteCreateRequest request = new InviteCreateRequest("healer@example.com", Role.HEALER);

        when(securityContextUtil.getCurrentHealerId()).thenReturn(CALLER_HEALER_ID);
        InviteResponse expected = sampleResponse();
        when(inviteService.createInvite(eq("healer@example.com"), eq(Role.HEALER), eq(CALLER_HEALER_ID)))
                .thenReturn(expected);

        ResponseEntity<InviteResponse> response = controller.create(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(inviteService).createInvite("healer@example.com", Role.HEALER, CALLER_HEALER_ID);
    }

    @Test
    void adminCannotInviteSupadm() {
        InviteCreateRequest request = new InviteCreateRequest("healer@example.com", Role.SUPADM);

        when(securityContextUtil.getCurrentRole()).thenReturn("ADMIN");

        assertThatThrownBy(() -> controller.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verifyNoInteractions(inviteService);
    }

    @Test
    void supadmCanInviteSupadm() {
        InviteCreateRequest request = new InviteCreateRequest("healer@example.com", Role.SUPADM);

        when(securityContextUtil.getCurrentRole()).thenReturn("SUPADM");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(CALLER_HEALER_ID);
        InviteResponse expected = sampleResponse();
        when(inviteService.createInvite(eq("healer@example.com"), eq(Role.SUPADM), eq(CALLER_HEALER_ID)))
                .thenReturn(expected);

        ResponseEntity<InviteResponse> response = controller.create(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(inviteService).createInvite("healer@example.com", Role.SUPADM, CALLER_HEALER_ID);
    }

    // ─── list() ──────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void list_crossHealerNoClinicFilter() {
        Page<InviteResponse> page = mock(Page.class);
        when(inviteService.list(any(Pageable.class))).thenReturn(page);

        Page<InviteResponse> result = controller.list(0, 20);

        assertThat(result).isEqualTo(page);
        verify(inviteService).list(any(Pageable.class));
    }

    // ─── resend() / revoke() ─────────────────────────────────────

    @Test
    void resend_delegatesWithNoClinicFilter() {
        UUID inviteId = UUID.randomUUID();
        InviteResponse expected = sampleResponse();
        when(inviteService.resend(inviteId)).thenReturn(expected);

        ResponseEntity<InviteResponse> response = controller.resend(inviteId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(inviteService).resend(inviteId);
    }

    @Test
    void revoke_delegatesWithNoClinicFilter() {
        UUID inviteId = UUID.randomUUID();
        InviteResponse expected = sampleResponse();
        when(inviteService.revoke(inviteId)).thenReturn(expected);

        ResponseEntity<InviteResponse> response = controller.revoke(inviteId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(inviteService).revoke(inviteId);
    }

    private static InviteResponse sampleResponse() {
        return new InviteResponse(
                UUID.randomUUID(), "healer@example.com", Role.HEALER.name(),
                "PENDING", Instant.now(), Instant.now().plusSeconds(3600), true);
    }
}
