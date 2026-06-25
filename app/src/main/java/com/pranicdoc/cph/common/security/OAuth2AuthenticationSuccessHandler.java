package com.pranicdoc.cph.common.security;

import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.service.HealerService;
import com.pranicdoc.cph.clinic.service.ClinicService;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final HealerService healerService;
    private final ClinicService clinicService;
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
            // Healer not pre-registered in the system — reject SSO and redirect with error
            response.sendRedirect(appBaseUrl + "/login");
            return;
        }

        String clinicCode = clinicService.getById(healer.getClinicId()).code();
        String accessToken = jwtService.generateAccessToken(
                healer.getId(), healer.getClinicId(), clinicCode, healer.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(
                healer.getId(), healer.getClinicId(), clinicCode, healer.getRole().name());
        cookieUtil.setAuthCookies(response, accessToken, refreshToken);
        response.sendRedirect(appBaseUrl + "/oauth2/success");
    }
}
