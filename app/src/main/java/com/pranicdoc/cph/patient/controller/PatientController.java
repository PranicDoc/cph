package com.pranicdoc.cph.patient.controller;

import com.pranicdoc.cph.common.dto.PageResponse;
import com.pranicdoc.cph.patient.dto.*;
import com.pranicdoc.cph.patient.service.PatientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    @PostMapping
    public ResponseEntity<PatientResponse> create(@Valid @RequestBody PatientCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(patientService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<PatientCaseResponse>> getAllPatients() {
        return ResponseEntity.ok(patientService.getAllPatients());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PatientResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(patientService.getById(id));
    }

    @GetMapping("/search")
    public ResponseEntity<PageResponse<PatientResponse>> search(
            @RequestParam String query,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(patientService.search(query, pageable));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PatientResponse> update(@PathVariable UUID id,
                                                  @Valid @RequestBody PatientUpdateRequest request) {
        return ResponseEntity.ok(patientService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        patientService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    // --- Medical History ---

    @PutMapping("/{id}/medical-history")
    public ResponseEntity<MedicalHistoryResponse> updateMedicalHistory(
            @PathVariable UUID id,
            @Valid @RequestBody MedicalHistoryRequest request) {
        return ResponseEntity.ok(patientService.updateMedicalHistory(id, request));
    }

    @GetMapping("/{id}/medical-history")
    public ResponseEntity<MedicalHistoryResponse> getMedicalHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(patientService.getMedicalHistory(id));
    }

    @GetMapping("/generate-link")
    public ResponseEntity<Map<String, String>> generateLink() {
        return ResponseEntity.ok(patientService.generateLink());
    }

    @PostMapping("/self-register")
    public ResponseEntity<?> selfRegister(@Valid @RequestBody PatientCreateRequest request) {
        return ResponseEntity.ok(patientService.selfRegister(request));
    }
}
