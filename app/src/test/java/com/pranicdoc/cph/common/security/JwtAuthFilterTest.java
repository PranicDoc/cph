package com.pranicdoc.cph.common.security;

import com.pranicdoc.cph.auth.service.HealerCacheService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Deactivated/removed healers must be rejected immediately (not just at next login) —
 * a stale ACCESS/SELF_REG token must stop authenticating as soon as HealerCacheService
 * reflects the change.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private HealerCacheService healerCacheService;

    @Mock
    private FilterChain filterChain;

    @Mock
    private Claims claims;

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        // Must be constructed here, not as a field initializer — @Mock fields aren't
        // injected by MockitoExtension until after instance construction runs.
        filter = new JwtAuthFilter(jwtService, healerCacheService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeHealerAccessTokenAuthenticates() throws Exception {
        UUID healerId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("accessToken", "valid-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.isTokenValid("valid-token")).thenReturn(true);
        when(jwtService.extractTokenType("valid-token")).thenReturn("ACCESS");
        when(jwtService.parseToken("valid-token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn(healerId.toString());
        when(claims.get("role", String.class)).thenReturn("HEALER");
        when(healerCacheService.isActive(healerId)).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo(healerId.toString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void deactivatedHealerAccessTokenIsRejected() throws Exception {
        UUID healerId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("accessToken", "valid-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.isTokenValid("valid-token")).thenReturn(true);
        when(jwtService.extractTokenType("valid-token")).thenReturn("ACCESS");
        when(jwtService.parseToken("valid-token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn(healerId.toString());
        when(claims.get("role", String.class)).thenReturn("HEALER");
        when(healerCacheService.isActive(healerId)).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        // No authentication set — downstream security returns 401 on protected endpoints,
        // same as if no token were present at all.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void removedHealerSelfRegTokenIsRejected() throws Exception {
        // Same isActive() check covers soft-removed healers — HealerCacheService.isActive()
        // treats "not found" (SQLRestriction-hidden) the same as "not active".
        UUID healerId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("accessToken", "self-reg-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.isTokenValid("self-reg-token")).thenReturn(true);
        when(jwtService.extractTokenType("self-reg-token")).thenReturn("SELF_REG");
        when(jwtService.parseToken("self-reg-token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn(healerId.toString());
        when(claims.get("role", String.class)).thenReturn("PATIENT");
        when(healerCacheService.isActive(healerId)).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void feedbackSessionTokenWithCaseIdClaim_populatesCaseIdInTenantDetails() throws Exception {
        UUID healerId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("accessToken", "feedback-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.isTokenValid("feedback-token")).thenReturn(true);
        when(jwtService.extractTokenType("feedback-token")).thenReturn("SELF_REG");
        when(jwtService.parseToken("feedback-token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn(healerId.toString());
        when(claims.get("role", String.class)).thenReturn("PATIENT");
        when(claims.get("caseId", String.class)).thenReturn(caseId.toString());
        when(healerCacheService.isActive(healerId)).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        var details = (SecurityContextUtil.TenantDetails) auth.getDetails();
        assertThat(details.healerId()).isEqualTo(healerId);
        assertThat(details.patientId()).isNull();
        assertThat(details.caseId()).isEqualTo(caseId);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void selfRegTokenWithoutCaseIdClaim_leavesCaseIdNull() throws Exception {
        UUID healerId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("accessToken", "self-reg-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.isTokenValid("self-reg-token")).thenReturn(true);
        when(jwtService.extractTokenType("self-reg-token")).thenReturn("SELF_REG");
        when(jwtService.parseToken("self-reg-token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn(healerId.toString());
        when(claims.get("role", String.class)).thenReturn("PATIENT");
        when(claims.get("caseId", String.class)).thenReturn(null);
        when(healerCacheService.isActive(healerId)).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        var details = (SecurityContextUtil.TenantDetails) auth.getDetails();
        assertThat(details.caseId()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
