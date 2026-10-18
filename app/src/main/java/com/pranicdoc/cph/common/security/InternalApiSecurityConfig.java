package com.pranicdoc.cph.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Secondary filter chain for /api/v1/internal/** (machine-to-machine, no user or JWT). Ordered before
 * {@link SecurityConfig}'s catch-all chain, so these paths never reach the JWT / OAuth2 setup:
 * {@link InternalApiKeyFilter} is the only way in.
 */
@Configuration
public class InternalApiSecurityConfig {

    static final String INTERNAL_PATHS = "/api/v1/internal/**";

    @Bean
    @Order(1)
    public SecurityFilterChain internalApiFilterChain(HttpSecurity http,
                                                      @Value("${app.internal.api-key:}") String apiKey) throws Exception {
        http
                .securityMatcher(INTERNAL_PATHS)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("INTERNAL"))
                .addFilterBefore(new InternalApiKeyFilter(apiKey), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
