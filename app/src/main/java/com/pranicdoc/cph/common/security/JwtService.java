package com.pranicdoc.cph.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

/**
 * JWT token generation and validation service using jjwt.
 *
 * Token claims:
 *   sub       = healerId (UUID)
 *   clinicId  = clinic UUID
 *   clinicCode = clinic display code (e.g. "CHNY01")
 *   role      = healer role (HEALER, SENIOR_HEALER, ADMIN, etc.)
 *   type      = "ACCESS" or "REFRESH"
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String base64Secret,
            @Value("${app.jwt.access-token-expiration-ms:3600000}") long accessTokenExpirationMs,
            @Value("${app.jwt.refresh-token-expiration-ms:604800000}") long refreshTokenExpirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    /**
     * Generate an access token with healer/clinic claims.
     */
    public String generateAccessToken(UUID healerId, UUID clinicId, String clinicCode, String role) {
        return buildToken(healerId, clinicId, clinicCode, role, "ACCESS", accessTokenExpirationMs);
    }

    /**
     * Generate a self registration token.
     */
    public String generateSelfRegToken(String uuid) {
        return buildToken(null, UUID.fromString(uuid), null, null, "SELF_REG", accessTokenExpirationMs);
    }

    /**
     * Generate a refresh token with minimal claims.
     */
    public String generateRefreshToken(UUID healerId, UUID clinicId, String clinicCode, String role) {
        return buildToken(healerId, clinicId, clinicCode, role, "REFRESH", refreshTokenExpirationMs);
    }

    /**
     * Parse and validate a token. Returns claims if valid, throws JwtException otherwise.
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Validate token without throwing — returns true if valid.
     */
    public boolean isTokenValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Extract healerId (subject) from token.
     */
    public UUID extractHealerId(String token) {
        return UUID.fromString(parseToken(token).getSubject());
    }

    /**
     * Extract clinicId from token claims.
     */
    public UUID extractClinicId(String token) {
        return UUID.fromString(parseToken(token).get("clinicId", String.class));
    }

    /**
     * Extract clinic code from token claims.
     */
    public String extractClinicCode(String token) {
        return parseToken(token).get("clinicCode", String.class);
    }

    /**
     * Extract role from token claims.
     */
    public String extractRole(String token) {
        return parseToken(token).get("role", String.class);
    }

    /**
     * Extract token type ("ACCESS" or "REFRESH").
     */
    public String extractTokenType(String token) {
        return parseToken(token).get("type", String.class);
    }

    /**
     * Get access token expiration in seconds (for API response).
     */
    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationMs / 1000;
    }

    // ---- internal ----

    private String buildToken(UUID healerId, UUID clinicId, String clinicCode,
                               String role, String type, long expirationMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        if("SELF_REG".equals(type)) {
            return Jwts.builder()
                    .subject(UUID.randomUUID().toString())
                    .claim("clinicId", clinicId.toString())
                    .claim("type", type)
                    .issuedAt(now)
                    .expiration(expiry)
                    .signWith(signingKey)
                    .compact();
        }

        return Jwts.builder()
                .subject(healerId.toString())
                .claim("clinicId", clinicId.toString())
                .claim("clinicCode", clinicCode)
                .claim("role", role)
                .claim("type", type)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }
}
