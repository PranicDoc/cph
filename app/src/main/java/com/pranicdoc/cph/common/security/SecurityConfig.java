package com.pranicdoc.cph.common.security;

import com.pranicdoc.cph.common.ratelimit.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Optional;

/**
 * Spring Security configuration.
 * Stateless JWT + HttpOnly cookie auth + CORS with credentials.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${app.base.url:}")
    private String appBaseUrl;

    private final JwtAuthFilter jwtAuthFilter;
    // Optional: RateLimitFilter is conditionally absent when app.ratelimit.enabled=false
    // (see RateLimitConfig/RateLimitFilter — local dev has no Redis instance).
    private final Optional<RateLimitFilter> rateLimitFilter;
    private final OAuth2AuthenticationSuccessHandler authenticationSuccessHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/init-session",
                                "/api/v1/auth/init-feedback-session",
                                "/api/v1/auth/accept-invite",
                                "/api/v1/auth/signup",
                                // No /register mapping exists anymore (D-10) — permitAll here only
                                // lets the request reach the dispatcher's NoHandlerFoundException ->
                                // 404 handler instead of the auth filter's 401, so unauthenticated
                                // callers see the same literal 404 as authenticated ones (ROADMAP SC-4).
                                "/api/v1/auth/register"
                        ).permitAll()
                        .requestMatchers("/api/v1/bootstrap").permitAll()
                        .requestMatchers("/api/v1/health").permitAll()
                        .requestMatchers("/api/v1/webhooks/whatsapp").permitAll()
                        // Machine-to-machine (Cloud Scheduler); each controller checks X-Internal-Key itself.
                        .requestMatchers("/api/v1/internal/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        // CR-01 (2026-09-02 ruling): the patient self-service case surface is
                        // caller-class-restricted here, at the filter chain, because JwtAuthFilter
                        // grants ROLE_PATIENT for both SELF_REG and PATIENT_CASE tokens (JwtService
                        // lines 72 and 93) while every healer token carries a non-PATIENT role.
                        // @rbacAuthz cannot express this: a SELF_REG token's healerId is the OWNING
                        // healer's, so @rbacAuthz.hasPermission would resolve a patient request
                        // against the wrong principal's grants. This is the control that stops the
                        // CaseController route split from being a bypass of the new
                        // CASE:UPDATE / CASE:READ gates.
                        .requestMatchers("/api/v1/cases/self-service", "/api/v1/cases/self-service/**").hasRole("PATIENT")
                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth -> oauth.successHandler(authenticationSuccessHandler))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedEntryPoint()))
                // jwtAuthFilter's position must be registered with Spring Security's internal
                // FilterComparator BEFORE it can be used as a reference point for another
                // custom filter below — registering rateLimitFilter's addFilterBefore call
                // first (referencing JwtAuthFilter.class before that class has a known
                // position) throws "The Filter class ... does not have a registered order".
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        // RateLimitFilter runs FIRST (before jwtAuthFilter) — these are pre-auth
        // public endpoints (INFRA-02, D-12); no token exists yet to authenticate.
        // Skipped entirely when app.ratelimit.enabled=false (no Redis available).
        rateLimitFilter.ifPresent(filter -> http.addFilterBefore(filter, JwtAuthFilter.class));

        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) ->
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
    }

    /**
     * CORS configuration — allowCredentials=true is CRUCIAL for cookie-based auth.
     * The browser won't send/receive cookies without this.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        var origins = new java.util.ArrayList<>(List.of(
                "http://localhost:3000",
                "http://localhost:3001",
                "http://localhost:5173"
        ));
        if (appBaseUrl != null && !appBaseUrl.isBlank()) {
            origins.add(appBaseUrl);
        }
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true); // 🔑 Crucial for HttpOnly cookies

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
