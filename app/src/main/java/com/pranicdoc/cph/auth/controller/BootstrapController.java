package com.pranicdoc.cph.auth.controller;

import com.pranicdoc.cph.auth.dto.BootstrapRequest;
import com.pranicdoc.cph.auth.dto.BootstrapResponse;
import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.enums.Role;
import com.pranicdoc.cph.common.security.CookieUtil;
import com.pranicdoc.cph.common.security.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * One-time bootstrap — creates the FIRST admin healer (solo-healer world, CUTOVER-05:
 * no clinic to create or join — the healer IS the tenant).
 * Self-locks once any healer exists in the database.
 * Sets HttpOnly auth cookies so admin can start using the API immediately.
 *
 * POST /api/v1/bootstrap
 */
@RestController
@RequestMapping("/api/v1/bootstrap")
@RequiredArgsConstructor
public class BootstrapController {

    private final HealerRepository healerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CookieUtil cookieUtil;

    @PostMapping
    @Transactional
    public ResponseEntity<BootstrapResponse> bootstrap(
            @Valid @RequestBody BootstrapRequest request,
            HttpServletResponse response) {

        // Self-lock: only works when no healer exists yet
        if (healerRepository.count() > 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        }

        // Create admin healer — no clinic association
        Healer healer = Healer.builder()
                .fullName(request.fullName())
                .email(request.email())
                .phone(request.phone())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.SUPADM)
                .active(true)
                .build();
        healer = healerRepository.save(healer);

        // TODO(26-09): drop clinicId/clinicCode args — bridge, generateAccessToken/
        // generateRefreshToken still carry these params until 26-09's JWT-claim-contract cutover.
        String accessToken = jwtService.generateAccessToken(
                healer.getId(), healer.getClinicId(), null, healer.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(
                healer.getId(), healer.getClinicId(), null, healer.getRole().name());
        cookieUtil.setAuthCookies(response, accessToken, refreshToken);

        return ResponseEntity.status(HttpStatus.CREATED).body(new BootstrapResponse(
                healer.getId(), healer.getFullName(), healer.getEmail(), healer.getRole(),
                null, null, 0 // tokens in cookies, not response body
        ));
    }
}
