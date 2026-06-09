package com.pranicdoc.cph.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Utility to set/clear auth cookies on HttpServletResponse.
 * Follows same pattern as the JIT project's cookie-based auth.
 *
 * Cookies:
 *   accessToken  → HttpOnly, Secure, SameSite
 *   refreshToken → HttpOnly, Secure, SameSite
 */
@Component
public class CookieUtil {

    private final boolean secure;
    private final String sameSite;
    private final long accessTokenMaxAgeSec;
    private final long refreshTokenMaxAgeSec;

    public CookieUtil(
            @Value("${app.cookies.secure:false}") boolean secure,
            @Value("${app.cookies.same-site:Lax}") String sameSite,
            @Value("${app.jwt.access-token-expiration-ms:3600000}") long accessTokenExpirationMs,
            @Value("${app.jwt.refresh-token-expiration-ms:604800000}") long refreshTokenExpirationMs) {
        this.secure = secure;
        this.sameSite = sameSite;
        this.accessTokenMaxAgeSec = accessTokenExpirationMs / 1000;
        this.refreshTokenMaxAgeSec = refreshTokenExpirationMs / 1000;
    }

    /**
     * Set both accessToken and refreshToken as HttpOnly cookies.
     */
    public void setAuthCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        ResponseCookie accessCookie = ResponseCookie.from("accessToken", accessToken)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(accessTokenMaxAgeSec)
                .sameSite(sameSite)
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(refreshTokenMaxAgeSec)
                .sameSite(sameSite)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    public void setSelfRegSessionCookie(HttpServletResponse response, String accessToken) {
        ResponseCookie accessCookie = ResponseCookie.from("accessToken", accessToken)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(refreshTokenMaxAgeSec)
                .sameSite(sameSite)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
    }

    /**
     * Set PATIENT_CASE session cookie — persistent (7 days), survives browser close.
     */
    public void setPatientCaseSessionCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("accessToken", token)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(refreshTokenMaxAgeSec)
                .sameSite(sameSite)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Clear the patient-case cookie after health form submission.
     */
    public void clearPatientCaseCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("accessToken", "")
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(0)
                .sameSite(sameSite)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Clear auth cookies (for logout).
     */
    public void clearAuthCookies(HttpServletResponse response) {
        ResponseCookie accessCookie = ResponseCookie.from("accessToken", "")
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(0)
                .sameSite(sameSite)
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(0)
                .sameSite(sameSite)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }
}
