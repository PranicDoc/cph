package com.pranicdoc.cph.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Verifies a Google-issued OIDC ID token server-side (signature, issuer, audience, expiry)
 * before it is trusted to link a Google account to a Healer.
 *
 * Reuses the already-configured GOOGLE_CLIENT_ID (spring.security.oauth2.client.registration.google.client-id)
 * as the expected audience — no new dependency, NimbusJwtDecoder is already on the classpath
 * via spring-boot-starter-oauth2-client.
 */
@Component
public class GoogleTokenVerifier {

    private static final String GOOGLE_JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ALLOWED_ISSUERS = Set.of(
            "https://accounts.google.com", "accounts.google.com");

    private final NimbusJwtDecoder decoder;

    public GoogleTokenVerifier(
            @Value("${spring.security.oauth2.client.registration.google.client-id}") String googleClientId) {
        this.decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWK_SET_URI).build();

        OAuth2TokenValidator<Jwt> timestampValidator = new JwtTimestampValidator();

        OAuth2TokenValidator<Jwt> issuerValidator = jwt -> {
            String issuer = jwt.getClaimAsString("iss");
            if (issuer != null && ALLOWED_ISSUERS.contains(issuer)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_issuer", "The iss claim is not a recognized Google issuer", null));
        };

        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
            if (jwt.getAudience() != null && jwt.getAudience().contains(googleClientId)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_audience", "The aud claim does not match the expected client", null));
        };

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                List.of(timestampValidator, issuerValidator, audienceValidator)));
    }

    /**
     * Verify a Google ID token and return its subject (sub) and email claim.
     * Throws IllegalArgumentException (mapped to HTTP 400 by GlobalExceptionHandler) on any
     * verification failure — bad signature, wrong issuer/audience, expired token, or missing email.
     */
    public VerifiedGoogleUser verify(String idToken) {
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException ex) {
            throw new IllegalArgumentException("Google sign-in could not be verified. Please try again.");
        }

        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Google sign-in could not be verified. Please try again.");
        }

        return new VerifiedGoogleUser(jwt.getSubject(), email);
    }

    public record VerifiedGoogleUser(String sub, String email) {}
}
