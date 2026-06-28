package com.pranicdoc.cph.auth.controller;

import com.pranicdoc.cph.auth.dto.BootstrapRequest;
import com.pranicdoc.cph.auth.dto.BootstrapResponse;
import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.clinic.model.Clinic;
import com.pranicdoc.cph.clinic.repository.ClinicRepository;
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
 * One-time bootstrap — creates the FIRST clinic + admin healer.
 * Self-locks once any clinic exists in the database.
 * Sets HttpOnly auth cookies so admin can start using the API immediately.
 *
 * POST /api/v1/bootstrap
 */
@RestController
@RequestMapping("/api/v1/bootstrap")
@RequiredArgsConstructor
public class BootstrapController {

    private final ClinicRepository clinicRepository;
    private final HealerRepository healerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CookieUtil cookieUtil;

    @PostMapping
    @Transactional
    public ResponseEntity<BootstrapResponse> bootstrap(
            @Valid @RequestBody BootstrapRequest request,
            HttpServletResponse response) {

        // Self-lock: only works when no clinics exist
        if (clinicRepository.count() > 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        }

        // 1. Create clinic
        Clinic clinic = Clinic.builder()
                .code(request.clinicCode())
                .name(request.clinicName() != null ? request.clinicName() : request.clinicCode())
                .phone(request.phone())
                .city(request.city())
                .active(true)
                .build();
        clinic = clinicRepository.save(clinic);

        // 2. Create admin healer
        Healer healer = Healer.builder()
                .fullName(request.fullName())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.SUPADM)
                .active(true)
                .build();
        healer.setClinicId(clinic.getId());
        healer = healerRepository.save(healer);

        // 3. Set auth cookies
        String accessToken = jwtService.generateAccessToken(
                healer.getId(), clinic.getId(), clinic.getCode(), healer.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(
                healer.getId(), clinic.getId(), clinic.getCode(), healer.getRole().name());
        cookieUtil.setAuthCookies(response, accessToken, refreshToken);

        return ResponseEntity.status(HttpStatus.CREATED).body(new BootstrapResponse(
                clinic.getId(), clinic.getCode(),
                healer.getId(), healer.getFullName(), healer.getEmail(), healer.getRole(),
                null, null, 0 // tokens in cookies, not response body
        ));
    }
}
