package com.pranicdoc.cph.common.security;

import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.service.HealerService;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final HealerService healerService;
    private final CookieUtil cookieUtil;

    @Value("${app.base.url:http://localhost:3000}")
    private String appBaseUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        Healer healer;
        try {
            healer = healerService.findHealerByEmail(email);
        } catch (ResourceNotFoundException ex) {
            // Healer not pre-registered in the system — reject SSO and redirect with error.
            // Spring's oauth2Login() already persisted the raw Google identity into the
            // HTTP session before this handler ran; clear it so it can't leak into later
            // requests as a stand-in for our own JWT-based auth (JwtAuthFilter only knows
            // how to authenticate via the accessToken cookie, which we never set here).
            clearSessionAuth(request);
            response.sendRedirect(appBaseUrl + "/login");
            return;
        }

        // D-01 link-gate: Google login is only permitted when the healer has explicitly
        // linked this Google account (providerUserId matches the authenticated sub).
        // Without this gate, any healer row whose email happens to match a Google account
        // could log in without ever having linked — this closes that pre-existing bypass.
        String googleSub = oAuth2User.getAttribute("sub");
        if (healer.getProviderUserId() == null || !healer.getProviderUserId().equals(googleSub)) {
            clearSessionAuth(request);
            response.sendRedirect(appBaseUrl + "/login?error=not_linked");
            return;
        }

        String accessToken = jwtService.generateAccessToken(healer.getId(), healer.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(healer.getId(), healer.getRole().name());
        cookieUtil.setAuthCookies(response, accessToken, refreshToken);
        // The app is JWT-cookie-authenticated from here on — drop the session-based Google
        // identity now that it's done its job, so it can never stand in for the JWT auth
        // JwtAuthFilter expects on every subsequent request.
        clearSessionAuth(request);
        response.sendRedirect(appBaseUrl + "/oauth2/success");
    }

    private void clearSessionAuth(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
