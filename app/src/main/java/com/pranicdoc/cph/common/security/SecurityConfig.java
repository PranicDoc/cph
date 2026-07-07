package com.pranicdoc.cph.common.security;

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
                                // No /register mapping exists anymore (D-10) — permitAll here only
                                // lets the request reach the dispatcher's NoHandlerFoundException ->
                                // 404 handler instead of the auth filter's 401, so unauthenticated
                                // callers see the same literal 404 as authenticated ones (ROADMAP SC-4).
                                "/api/v1/auth/register"
                        ).permitAll()
                        .requestMatchers("/api/v1/bootstrap").permitAll()
                        .requestMatchers("/api/v1/health").permitAll()
                        .requestMatchers("/api/v1/webhooks/whatsapp").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth -> oauth.successHandler(authenticationSuccessHandler))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedEntryPoint()))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

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
