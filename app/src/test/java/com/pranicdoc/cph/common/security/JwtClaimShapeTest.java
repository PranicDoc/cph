package com.pranicdoc.cph.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests describing the POST-CUTOVER JWT claim shape (Phase 26). Both
 * methods are {@code @Disabled} — the current claim shape (clinicId-bearing
 * ACCESS/REFRESH tokens, clinicId-proxied PATIENT_CASE tokens, random-UUID
 * SELF_REG subjects) is unchanged until their enabling plans land. Keeping
 * these tests compiling-but-skipped now means 26-09/26-11 enable a
 * ready-made regression net instead of writing the assertions from scratch.
 *
 * <ul>
 *   <li>{@link #noClinicIdClaim_stillAuthenticates()} — 26-VALIDATION.md Wave 0
 *       requirement 2 / task 26-W1-02. Guards Pitfall 4: {@code JwtAuthFilter}
 *       currently does an UNCONDITIONAL
 *       {@code UUID.fromString(claims.get("clinicId", String.class))} before
 *       branching on token type, so a token minted without a {@code clinicId}
 *       claim throws (swallowed by the filter's catch-all) and silently fails
 *       to authenticate today. Enabled by 26-09, once that unconditional parse
 *       is removed/reworked in the same commit that stops issuing the claim.</li>
 *   <li>{@link #patientToken_carriesRealHealerId()} — 26-VALIDATION.md Wave 0
 *       requirement 3 / task 26-W4-01 (D-26-04). Guards against
 *       {@code PATIENT_CASE}/{@code SELF_REG} tokens carrying a null or
 *       {@code UUID.randomUUID()} healer identity instead of the real owning
 *       healer's id. Enabled by 26-11.</li>
 * </ul>
 */
class JwtClaimShapeTest {

    // Matches app.jwt.secret in application-test.yaml — reused here so a
    // manually-built token (bypassing JwtService.buildToken's fixed claim
    // set) is signed with the same key a locally-constructed JwtService
    // instance verifies against.
    private static final String TEST_SECRET =
            "fk6JC1uv+OE7IbpytRxWetyFg2WO38y2gM5uOeKsXuvfn6aMeKBlP7Dh0tJL2D0cYtUzIPAak6jtin2i/c+RMQ==";

    @Test
    void noClinicIdClaim_stillAuthenticates() throws Exception {
        SecretKey signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));

        UUID healerId = UUID.randomUUID();
        Date now = new Date();
        // Deliberately built WITHOUT a "clinicId" claim — the post-26-09
        // token shape for a healer ACCESS session.
        String tokenWithoutClinicIdClaim = Jwts.builder()
                .subject(healerId.toString())
                .claim("role", "HEALER")
                .claim("type", "ACCESS")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3_600_000L))
                .signWith(signingKey)
                .compact();

        JwtService jwtService = new JwtService(TEST_SECRET, 3_600_000L, 604_800_000L);
        JwtAuthFilter filter = new JwtAuthFilter(jwtService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("accessToken", tokenWithoutClinicIdClaim));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication,
                "Expected SecurityContext to be populated even though the token carries no clinicId claim (Pitfall 4 guard)");
        assertTrue(authentication.isAuthenticated());
    }

    @Disabled("enabled by 26-11: D-26-04 patient-token real healerId")
    @Test
    void patientToken_carriesRealHealerId() {
        JwtService jwtService = new JwtService(TEST_SECRET, 3_600_000L, 604_800_000L);

        UUID realHealerId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();

        // Post-D-26-04 contract: a PATIENT_CASE token must carry the case's
        // real owning healerId (not merely proxy a clinicId, and never
        // null/random). generatePatientCaseToken's 3rd positional UUID arg
        // is the target of that rename per 26-PATTERNS.md Cluster B.
        String patientCaseToken = jwtService.generatePatientCaseToken(patientId, caseId, realHealerId);
        Claims patientCaseClaims = jwtService.parseToken(patientCaseToken);
        String patientTokenHealerId = patientCaseClaims.get("healerId", String.class);

        assertNotNull(patientTokenHealerId,
                "PATIENT_CASE token must carry a healerId claim post-D-26-04");
        assertEquals(realHealerId.toString(), patientTokenHealerId,
                "PATIENT_CASE token healerId must equal the case's real owning healer, not null/random");

        // Post-D-26-04 contract: a SELF_REG token's subject must be the real
        // owning healerId instead of today's UUID.randomUUID() bug
        // (JwtService.buildToken's SELF_REG branch, 26-PATTERNS.md Cluster B).
        String selfRegToken = jwtService.generateSelfRegToken(realHealerId.toString());
        Claims selfRegClaims = jwtService.parseToken(selfRegToken);
        String selfRegSubject = selfRegClaims.getSubject();

        assertEquals(realHealerId.toString(), selfRegSubject,
                "SELF_REG token subject must equal the real owning healerId, not a random UUID");
    }
}
