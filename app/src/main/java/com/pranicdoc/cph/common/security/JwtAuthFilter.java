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
 * Cookie-based auth:
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

            UsernamePasswordAuthenticationToken authToken;

            if ("PATIENT_CASE".equals(tokenType)) {
                // Narrow case-scoped token: subject = patientId
                UUID patientId = UUID.fromString(claims.getSubject());
                UUID caseId = UUID.fromString(claims.get("caseId", String.class));
                // Pitfall 4 fix: no unconditional tenant-claim parse. healerId is not yet
                // a real claim on this token type (26-11/D-26-04 adds it) — tolerate null,
                // do NOT fail closed.
                String healerIdClaim = claims.get("healerId", String.class);
                UUID healerId = healerIdClaim != null ? UUID.fromString(healerIdClaim) : null;
                authToken = new UsernamePasswordAuthenticationToken(
                        patientId.toString(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                authToken.setDetails(new SecurityContextUtil.TenantDetails(
                        healerId, patientId, caseId));

            } else if ("ACCESS".equals(tokenType) || "SELF_REG".equals(tokenType)) {
                UUID healerId = UUID.fromString(claims.getSubject());
                authToken = new UsernamePasswordAuthenticationToken(
                        healerId.toString(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                authToken.setDetails(new SecurityContextUtil.TenantDetails(
                        healerId, null, null));

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
