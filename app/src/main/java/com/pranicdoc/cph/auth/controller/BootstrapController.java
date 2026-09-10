package com.pranicdoc.cph.auth.controller;

import com.pranicdoc.cph.auth.dto.BootstrapAssignRoleRequest;
import com.pranicdoc.cph.auth.dto.BootstrapRequest;
import com.pranicdoc.cph.auth.dto.BootstrapResponse;
import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.enums.Role;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.security.CookieUtil;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.rbac.dto.RoleAssignmentRequest;
import com.pranicdoc.cph.rbac.dto.RoleAssignmentResponse;
import com.pranicdoc.cph.rbac.service.RoleAssignmentService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bootstrap endpoints — two deliberately asymmetric deploy-time paths in one
 * controller.
 *
 * <p><b>{@code POST /api/v1/bootstrap}</b> creates the very first admin healer
 * (solo-healer world, CUTOVER-05: no clinic to create or join — the healer IS
 * the tenant). It is intentionally reachable without authentication, because
 * at the moment it runs there is no account in the database to authenticate
 * as. It self-locks permanently the instant any healer exists, and sets
 * HttpOnly auth cookies so the new operator can start using the API
 * immediately.
 *
 * <p><b>{@code POST /api/v1/bootstrap/assign-role}</b> attaches an existing
 * custom role to an existing account, resolved by email (ASSIGN-05, D-05). It
 * carries the opposite security posture: restricted to the highest tier by
 * its own per-method authorization annotation. It issues no tokens, does no
 * cookie handling, and never self-locks — it is the seeding path used after a
 * cut-over leaves freshly converted accounts with no grants and no UI yet to
 * give them any.
 *
 * <p>No security-configuration change was needed to protect the second
 * endpoint, and that is a verified decision rather than an omission
 * (PD-37-28). The framework's permit rule for this controller matches only
 * the exact base path {@code /api/v1/bootstrap}; the {@code /assign-role}
 * sub-path falls through to the authenticated default at the end of the
 * filter chain. Adding a second rule for the sub-path would create a
 * divergent gate for the same controller.
 */
@RestController
@RequestMapping("/api/v1/bootstrap")
@RequiredArgsConstructor
public class BootstrapController {

    private final HealerRepository healerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CookieUtil cookieUtil;
    private final RoleAssignmentService roleAssignmentService;

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

        String accessToken = jwtService.generateAccessToken(healer.getId(), healer.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(healer.getId(), healer.getRole().name());
        cookieUtil.setAuthCookies(response, accessToken, refreshToken);

        return ResponseEntity.status(HttpStatus.CREATED).body(new BootstrapResponse(
                healer.getId(), healer.getFullName(), healer.getEmail(), healer.getRole(),
                null, null, 0 // tokens in cookies, not response body
        ));
    }

    /**
     * Grants one existing custom role to one existing account, resolved by
     * email (ASSIGN-05). Only the caller is tier-restricted, by this method's
     * own authorization annotation; there is deliberately no restriction on
     * the target account's own tier. The roadmap's Phase 37 entry says the
     * target must already be the highest tier — that wording is a known
     * drafting error (D-04). It contradicts the locked design under which the
     * highest tier is never itself a role target and never holds an
     * assignment row at all, which would make such a target impossible to
     * express under this schema. A future reader comparing this code to the
     * roadmap should not "correct" it back toward that wording.
     *
     * <p>The only work this method does itself is the email lookup. Role
     * existence, the already-assigned-pair rejection, and the write all live
     * in the assignment service and surface through the shared exception
     * handler as 404 / 400 unchanged.
     */
    @PostMapping("/assign-role")
    @PreAuthorize("hasRole('SUPADM')")
    @Transactional
    public ResponseEntity<RoleAssignmentResponse> assignRole(
            @Valid @RequestBody BootstrapAssignRoleRequest request) {
        Healer target = healerRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No account exists with email " + request.email()));
        RoleAssignmentResponse result = roleAssignmentService.assign(
                new RoleAssignmentRequest(target.getId(), request.roleId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
