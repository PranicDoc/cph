package com.pranicdoc.cph.auth.controller;

import com.pranicdoc.cph.auth.dto.HealerCreateRequest;
import com.pranicdoc.cph.auth.dto.HealerResponse;
import com.pranicdoc.cph.auth.dto.InitFeedbackSessionRequest;
import com.pranicdoc.cph.auth.dto.InitSessionRequest;
import com.pranicdoc.cph.auth.dto.LoginRequest;
import com.pranicdoc.cph.auth.service.impl.AuthService;
import com.pranicdoc.cph.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Register a new healer. Sets HttpOnly auth cookies.
     * POST /api/v1/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<HealerResponse> register(
            @Valid @RequestBody HealerCreateRequest request,
            HttpServletResponse response) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request, response));
    }

    /**
     * Login with email + password. Sets HttpOnly auth cookies.
     * POST /api/v1/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<HealerResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        return ResponseEntity.ok(authService.login(request, response));
    }

    /**
     * Refresh tokens. Reads refreshToken from cookie, sets new cookies.
     * POST /api/v1/auth/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<String>> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {
        authService.refresh(request, response);
        return ResponseEntity.ok(new ApiResponse<>(true, "Token refreshed", null));
    }

    /**
     * Logout — clears auth cookies.
     * POST /api/v1/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(HttpServletResponse response) {
        authService.logout(response);
        return ResponseEntity.ok(new ApiResponse<>(true, "Logged out", null));
    }

    /**
     * Get the currently authenticated healer profile.
     * GET /api/v1/auth/me
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(authentication.getPrincipal());
    }

    @GetMapping("/profile")
    public ResponseEntity<HealerResponse> getCurrentHealer() {
        return ResponseEntity.ok(authService.getCurrentHealer());
    }

    @PostMapping("/init-session")
    public ResponseEntity<ApiResponse<?>> initSession(@RequestBody InitSessionRequest request,
                                         HttpServletResponse response) {
        return ResponseEntity.ok(
                authService.initSessionForSelfReg(request.uuid(), request.clinicId(), response));
    }

    @PostMapping("/init-feedback-session")
    public ResponseEntity<Void> initFeedbackSession(
            @Valid @RequestBody InitFeedbackSessionRequest request,
            HttpServletResponse response) {
        authService.initSessionForFeedback(request.token(), response);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/clear-patient-session")
    public ResponseEntity<Void> clearPatientSession(HttpServletResponse response) {
        authService.clearPatientSession(response);
        return ResponseEntity.ok().build();
    }
}
