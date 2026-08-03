package com.pranicdoc.cph.consent.controller;

import com.pranicdoc.cph.consent.dto.ConsentAcceptRequest;
import com.pranicdoc.cph.consent.dto.ConsentResponse;
import com.pranicdoc.cph.consent.dto.PolicyVersionResponse;
import com.pranicdoc.cph.consent.model.ConsentType;
import com.pranicdoc.cph.consent.service.ConsentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * REST surface for the consent capability (CONSENT-03/04/05/06/07). No
 * {@code @PreAuthorize} role restriction is applied here — every endpoint is
 * self-service, scoped to the authenticated healer inside {@link ConsentService}
 * via {@code SecurityContextUtil} (same posture as {@code PatientController}).
 * Authentication itself is already enforced globally by SecurityConfig's
 * {@code .anyRequest().authenticated()} gate; these paths are never added to
 * a permitAll list (T-30-08). The one deliberate exception is {@code publish}
 * below, which carries {@code @PreAuthorize} as the sole admin-only write path
 * (D-02) — a future reader should not "fix" this inconsistency.
 */
@RestController
@RequestMapping("/api/v1/consents")
@RequiredArgsConstructor
public class ConsentController {

    private final ConsentService consentService;

    @PostMapping
    public ResponseEntity<ConsentResponse> accept(@Valid @RequestBody ConsentAcceptRequest request,
                                                    HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(consentService.accept(request, httpRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ConsentResponse> withdraw(@PathVariable UUID id) {
        return ResponseEntity.ok(consentService.withdraw(id));
    }

    @GetMapping("/history")
    public ResponseEntity<List<ConsentResponse>> history() {
        return ResponseEntity.ok(consentService.history());
    }

    @GetMapping("/policy-versions/active")
    public ResponseEntity<List<PolicyVersionResponse>> activePolicies() {
        return ResponseEntity.ok(consentService.activePolicyVersions());
    }

    @PostMapping(value = "/policy-versions", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPADM')")
    public ResponseEntity<PolicyVersionResponse> publish(
            @RequestParam("consentType") ConsentType consentType,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "mandatory", required = false) Boolean mandatory) {
        return ResponseEntity.status(HttpStatus.CREATED).body(consentService.publish(consentType, file, content, mandatory));
    }

    @GetMapping("/outstanding")
    public ResponseEntity<List<PolicyVersionResponse>> outstanding() {
        return ResponseEntity.ok(consentService.outstandingConsents());
    }
}
