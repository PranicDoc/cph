package com.pranicdoc.cph.common.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * JWT authentication filter — reads accessToken from HttpOnly cookie,
 * validates via JwtService, and populates SecurityContext.
 *
 * Cookie-based auth (matching JIT project pattern):
 *   - accessToken  → HttpOnly cookie set on login/register/bootstrap
 *   - refreshToken → HttpOnly cookie set on login/register/bootstrap
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // 1. Extract accessToken from cookie
        String token = extractTokenFromCookie(request, "accessToken");
        if(StringUtils.isBlank(token)) token = extractTokenFromCookie(request, "selfRegToken");

        // No token → let Spring Security handle (401 for protected endpoints)
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (!jwtService.isTokenValid(token)) {
                filterChain.doFilter(request, response);
                return;
            }

            String tokenType = jwtService.extractTokenType(token);
            Claims claims = jwtService.parseToken(token);
            String role = claims.get("role", String.class);
            UUID clinicId = UUID.fromString(claims.get("clinicId", String.class));

            UsernamePasswordAuthenticationToken authToken;

            if ("PATIENT_CASE".equals(tokenType)) {
                // Narrow case-scoped token: subject = patientId
                UUID patientId = UUID.fromString(claims.getSubject());
                UUID caseId = UUID.fromString(claims.get("caseId", String.class));
                authToken = new UsernamePasswordAuthenticationToken(
                        patientId.toString(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                authToken.setDetails(new SecurityContextUtil.TenantDetails(
                        clinicId, null, null, patientId, caseId));

            } else if ("ACCESS".equals(tokenType) || "SELF_REG".equals(tokenType)) {
                UUID healerId = UUID.fromString(claims.getSubject());
                String clinicCode = claims.get("clinicCode", String.class);
                authToken = new UsernamePasswordAuthenticationToken(
                        healerId.toString(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                authToken.setDetails(new SecurityContextUtil.TenantDetails(
                        clinicId, healerId, clinicCode, null, null));

            } else {
                filterChain.doFilter(request, response);
                return;
            }

            SecurityContextHolder.getContext().setAuthentication(authToken);

        } catch (Exception ex) {
            // Token parsing failed — don't set auth, let Spring Security reject downstream
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract a named cookie value from the request.
     */
    public static String extractTokenFromCookie(HttpServletRequest request, String cookieName) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
