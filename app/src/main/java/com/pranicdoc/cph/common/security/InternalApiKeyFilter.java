package com.pranicdoc.cph.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Authenticates machine-to-machine calls (Cloud Scheduler) on /api/v1/internal/** by the
 * {@value #HEADER} header, compared in constant time against {@code app.internal.api-key}.
 * <ul>
 *   <li>no key configured → 404, as if the endpoints didn't exist</li>
 *   <li>missing / wrong key → 401</li>
 *   <li>match → authenticated as {@code ROLE_INTERNAL}</li>
 * </ul>
 * Deliberately not a Spring bean: Boot would register a {@code @Component} filter for every request.
 * Wired only into {@link InternalApiSecurityConfig}'s filter chain.
 */
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Key";
    static final String ROLE = "ROLE_INTERNAL";

    private final byte[] apiKey;

    public InternalApiKeyFilter(String apiKey) {
        this.apiKey = apiKey == null ? new byte[0] : apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (apiKey.length == 0) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String presented = request.getHeader(HEADER);
        if (presented == null || !MessageDigest.isEqual(apiKey, presented.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("internal", null, List.of(new SimpleGrantedAuthority(ROLE))));
        chain.doFilter(request, response);
    }
}
