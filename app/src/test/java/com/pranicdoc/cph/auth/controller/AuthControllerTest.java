package com.pranicdoc.cph.auth.controller;

import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.auth.service.impl.AuthService;
import com.pranicdoc.cph.auth.service.impl.InviteService;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.security.OAuth2AuthenticationSuccessHandler;
import com.pranicdoc.cph.common.security.SecurityConfig;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.tenant.TenantInterceptor;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import com.pranicdoc.cph.rbac.service.MyPermissionsService;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private InviteService inviteService;

    @MockBean
    private SecurityContextUtil securityContextUtil;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private TenantInterceptor tenantInterceptor;

    @MockBean
    private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockBean
    private HealerCacheService healerCacheService;

    @MockBean
    private MyPermissionsService myPermissionsService;


    private static final String ACCESS_TOKEN = "access-token";

    private static Cookie accessCookie() {
        return new Cookie("accessToken", ACCESS_TOKEN);
    }

    /** Makes the JWT filter accept {@link #accessCookie()} as a signed-in healer. */
    private void signIn() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(UUID.randomUUID().toString());
        when(claims.get("role", String.class)).thenReturn("HEALER");
        when(jwtService.isTokenValid(ACCESS_TOKEN)).thenReturn(true);
        when(jwtService.extractTokenType(ACCESS_TOKEN)).thenReturn("ACCESS");
        when(jwtService.parseToken(ACCESS_TOKEN)).thenReturn(claims);
        when(healerCacheService.isActive(any())).thenReturn(true);
        when(tenantInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    void changePassword_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"newPass123\",\"confirmPassword\":\"newPass123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void register_deletedEndpoint_authenticatedCaller_returns404() throws Exception {
        // D-10: /register is deleted outright, not gated — with a request that clears the
        // security-authorization gate (authenticated), the request reaches the dispatcher,
        // which finds no handler for the removed mapping and returns 404. Never 403 (403
        // would mean the mapping still exists but is access-controlled).
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void register_deletedEndpoint_unauthenticatedCaller_returns404() throws Exception {
        // /api/v1/auth/register is on SecurityConfig's permitAll list purely so the request
        // reaches the dispatcher instead of being rejected by the auth filter — no mapping
        // exists for it (D-10), so it falls through to NoHandlerFoundException -> 404, giving
        // unauthenticated callers the same literal 404 as authenticated ones (ROADMAP SC-4).
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateUiVersion_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/api/v1/auth/ui-version")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uiVersion\":\"NEW\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateUiVersion_validChoice_returns200() throws Exception {
        signIn();
        mockMvc.perform(put("/api/v1/auth/ui-version")
                        .cookie(accessCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uiVersion\":\"NEW\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateUiVersion_unknownValue_returns400() throws Exception {
        signIn();
        mockMvc.perform(put("/api/v1/auth/ui-version")
                        .cookie(accessCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uiVersion\":\"FUTURE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateUiVersion_missingValue_returns400() throws Exception {
        signIn();
        mockMvc.perform(put("/api/v1/auth/ui-version")
                        .cookie(accessCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
