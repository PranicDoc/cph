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
 * Token claims (ACCESS/REFRESH, post-26-09 CONTRACT step):
 *   sub       = healerId (UUID)
 *   role      = healer role (HEALER, SENIOR_HEALER, ADMIN, etc.)
 *   type      = "ACCESS" or "REFRESH"
 *
 * (SELF_REG/PATIENT_CASE token shapes are unchanged here — 26-11's D-26-04 scope.)
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
     * Generate an access token with healer claims (clinic-free, post-26-09).
     */
    public String generateAccessToken(UUID healerId, String role) {
        return buildToken(healerId, null, null, role, "ACCESS", accessTokenExpirationMs);
    }

    /**
     * Generate a self registration token (feedback session — no registration UUID).
     */
    public String generateSelfRegToken(String uuid) {
        return buildToken(null, UUID.fromString(uuid), null, "PATIENT", "SELF_REG", accessTokenExpirationMs);
    }

    /**
     * Generate a self registration token with the registration link UUID embedded.
     * Used during patient self-registration so the backend can look up and consume the link.
     */
    public String generateSelfRegToken(String clinicId, String regUuid) {
        Date now = new Date();
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("clinicId", clinicId)
                .claim("type", "SELF_REG")
                .claim("role", "PATIENT")
                .claim("reg_uuid", regUuid)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpirationMs))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Generate a case-scoped token issued after case creation.
     * Replaces the broad SELF_REG token — bound to a single patientId + caseId.
     * Uses refresh-token expiry (7 days) so it survives browser close.
     */
    public String generatePatientCaseToken(UUID patientId, UUID caseId, UUID clinicId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(patientId.toString())
                .claim("caseId", caseId.toString())
                .claim("clinicId", clinicId.toString())
                .claim("type", "PATIENT_CASE")
                .claim("role", "PATIENT")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenExpirationMs))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Generate a refresh token with minimal claims (clinic-free, post-26-09).
     */
    public String generateRefreshToken(UUID healerId, String role) {
        return buildToken(healerId, null, null, role, "REFRESH", refreshTokenExpirationMs);
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
     * Extract role from token claims.
     */
    public String extractRole(String token) {
        return parseToken(token).get("role", String.class);
    }

    /**
     * Extract token type ("ACCESS", "REFRESH", "SELF_REG", or "PATIENT_CASE").
     */
    public String extractTokenType(String token) {
        return parseToken(token).get("type", String.class);
    }

    /** Extract the registration link UUID embedded in a SELF_REG token. Null for other token types. */
    public String extractRegUuid(String token) {
        return parseToken(token).get("reg_uuid", String.class);
    }

    /** Extract the caseId claim from a PATIENT_CASE token. */
    public UUID extractCaseId(String token) {
        return UUID.fromString(parseToken(token).get("caseId", String.class));
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
                    .claim("role", role)
                    .issuedAt(now)
                    .expiration(expiry)
                    .signWith(signingKey)
                    .compact();
        }

        return Jwts.builder()
                .subject(healerId.toString())
                .claim("role", role)
                .claim("type", type)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }
}
