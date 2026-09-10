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
 *   role      = healer role (HEALER, SUPADM)
 *   type      = "ACCESS" or "REFRESH"
 *
 * Token claims (SELF_REG/PATIENT_CASE, post-26-11 D-26-04):
 *   SELF_REG:     sub = the real owning healerId (UUID) — no tenant-shaped claim.
 *   PATIENT_CASE: sub = patientId; claim("healerId") = the case's real owning
 *                 healer (UUID) — no tenant-shaped claim.
 * Both token types now carry a genuine owning-healer identity instead of a
 * random subject / tenant-shaped proxy, so the Hibernate @Filter (healer_id)
 * scopes patient sessions correctly — no per-role filter exemption needed.
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
        return buildToken(healerId, null, role, "ACCESS", accessTokenExpirationMs);
    }

    /**
     * Generate a self registration token (feedback session — no registration UUID).
     * The subject is the real owning healer's id (D-26-04) — not a random UUID.
     */
    public String generateSelfRegToken(String healerId) {
        return buildToken(null, UUID.fromString(healerId), "PATIENT", "SELF_REG", accessTokenExpirationMs);
    }

    /**
     * Generate a self registration token with the registration link UUID embedded.
     * Used during patient self-registration so the backend can look up and consume the link.
     * The subject is the real owning healer's id (D-26-04) — not a random UUID.
     */
    public String generateSelfRegToken(String healerId, String regUuid) {
        Date now = new Date();
        return Jwts.builder()
                .subject(healerId)
                .claim("type", "SELF_REG")
                .claim("role", "PATIENT")
                .claim("reg_uuid", regUuid)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpirationMs))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Generate a feedback-session token bound to a single {@code FeedbackLink.caseId}
     * (42-15 / CR-02). Same SELF_REG shape as {@link #generateSelfRegToken(String)} —
     * subject is the owning healerId, role PATIENT — but additionally carries a
     * server-derived {@code caseId} claim so {@code CaseService} can perform a
     * record-level ownership check on the patient feedback read. The caseId always
     * comes from the persisted {@code FeedbackLink} row, never from a request
     * parameter. Kept as type SELF_REG (not promoted to PATIENT_CASE) because this
     * token still has no patientId claim; a PATIENT_CASE-shaped filter branch would
     * assume one exists.
     */
    public String generateFeedbackSessionToken(String healerId, UUID caseId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(healerId)
                .claim("type", "SELF_REG")
                .claim("role", "PATIENT")
                .claim("caseId", caseId.toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpirationMs))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Generate a case-scoped token issued after case creation.
     * Replaces the broad SELF_REG token — bound to a single patientId + caseId.
     * Uses refresh-token expiry (7 days) so it survives browser close.
     * Carries the case's real owning healerId claim (D-26-04) — no tenant-shaped proxy.
     */
    public String generatePatientCaseToken(UUID patientId, UUID caseId, UUID healerId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(patientId.toString())
                .claim("caseId", caseId.toString())
                .claim("healerId", healerId.toString())
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
        return buildToken(healerId, null, role, "REFRESH", refreshTokenExpirationMs);
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

    private String buildToken(UUID healerId, UUID selfRegHealerId,
                               String role, String type, long expirationMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        if("SELF_REG".equals(type)) {
            // D-26-04: subject is the real owning healerId — no tenant-shaped claim.
            return Jwts.builder()
                    .subject(selfRegHealerId.toString())
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
