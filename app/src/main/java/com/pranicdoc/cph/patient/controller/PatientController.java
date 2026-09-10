package com.pranicdoc.cph.patient.controller;

import com.pranicdoc.cph.common.dto.PageResponse;
import com.pranicdoc.cph.common.enums.CaseStatus;
import com.pranicdoc.cph.patient.dto.*;
import com.pranicdoc.cph.patient.service.PatientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    @PreAuthorize("@rbacAuthz.hasPermission('PATIENT','CREATE')")
    @PostMapping
    public ResponseEntity<PatientResponse> create(@Valid @RequestBody PatientCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(patientService.create(request));
    }

    // D-09 trace verdict: Registration.tsx's non-isSelfRegister branch calls this from the
    // authenticated /registration healer route, and PatientService.register() throws
    // IllegalStateException("Missing healer context") when getCurrentHealerIdOrNull() is null — so
    // it is healer-authenticated and gets the same PATIENT:CREATE gate as create.
    @PreAuthorize("@rbacAuthz.hasPermission('PATIENT','CREATE')")
    @PostMapping("/register")
    public ResponseEntity<PatientRegisterResponse> register(@Valid @RequestBody PatientRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(patientService.register(request));
    }

    @PreAuthorize("@rbacAuthz.hasPermission('PATIENT','READ')")
    @GetMapping
    public ResponseEntity<List<PatientCaseResponse>> getAllPatients(
            @RequestParam(required = false) CaseStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) PatientSort sort) {
        return ResponseEntity.ok(patientService.getAllPatients(status, startDate, endDate, sort));
    }

    @PreAuthorize("@rbacAuthz.hasPermission('PATIENT','READ')")
    @GetMapping("/{id}")
    public ResponseEntity<PatientResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(patientService.getById(id));
    }

    @PreAuthorize("@rbacAuthz.hasPermission('PATIENT','READ')")
    @GetMapping("/search")
    public ResponseEntity<PageResponse<PatientResponse>> search(
            @RequestParam String query,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(patientService.search(query, pageable));
    }

    @PreAuthorize("@rbacAuthz.hasPermission('PATIENT','UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<PatientResponse> update(@PathVariable UUID id,
                                                  @Valid @RequestBody PatientUpdateRequest request) {
        return ResponseEntity.ok(patientService.update(id, request));
    }

    @PreAuthorize("@rbacAuthz.hasPermission('PATIENT','DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        patientService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    // --- Medical History ---

    @PreAuthorize("@rbacAuthz.hasPermission('PATIENT','UPDATE')")
    @PutMapping("/{id}/medical-history")
    public ResponseEntity<MedicalHistoryResponse> updateMedicalHistory(
            @PathVariable UUID id,
            @Valid @RequestBody MedicalHistoryRequest request) {
        return ResponseEntity.ok(patientService.updateMedicalHistory(id, request));
    }

    @PreAuthorize("@rbacAuthz.hasPermission('PATIENT','READ')")
    @GetMapping("/{id}/medical-history")
    public ResponseEntity<MedicalHistoryResponse> getMedicalHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(patientService.getMedicalHistory(id));
    }

    /**
     * Deliberately UNGATED (SC4 / D-09). Emits a patient self-registration link scoped
     * server-side to the calling healer and takes no healer-scoped request input. SC4 locks it
     * open so link generation is not blocked for a freshly-onboarded healer with no grants —
     * the same posture as the self-service consent endpoints. Named by plan 42-11's open-list test.
     */
    // TODO Make this auto sendable using invite service to specific number or email provided
    @GetMapping("/generate-link")
    public ResponseEntity<Map<String, String>> generateLink() {
        return ResponseEntity.ok(patientService.generateLink());
    }

    /**
     * Deliberately UNGATED (D-08 / D-19). Runs on a SELF_REG / PATIENT-role token; the dynamic
     * permission check returns false for every real caller, so a gate would 403 all patient
     * self-registration. Named by plan 42-11's open-list test.
     */
    @PostMapping("/self-register")
    public ResponseEntity<PatientResponse> selfRegister(@Valid @RequestBody PatientCreateRequest request) {
        return ResponseEntity.ok(patientService.selfRegister(request));
    }
}
