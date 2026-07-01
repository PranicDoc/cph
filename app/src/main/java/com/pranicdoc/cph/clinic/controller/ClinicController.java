package com.pranicdoc.cph.clinic.controller;

import com.pranicdoc.cph.clinic.dto.ClinicCreateRequest;
import com.pranicdoc.cph.clinic.dto.ClinicResponse;
import com.pranicdoc.cph.clinic.service.ClinicService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinics")
@RequiredArgsConstructor
public class ClinicController {

    private final ClinicService clinicService;

    @GetMapping
    @PreAuthorize("hasRole('SUPADM')")
    public ResponseEntity<List<ClinicResponse>> getAll() {
        return ResponseEntity.ok(clinicService.getAll());
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPADM')")
    public ResponseEntity<ClinicResponse> create(@Valid @RequestBody ClinicCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clinicService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClinicResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(clinicService.getById(id));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<ClinicResponse> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(clinicService.getByCode(code));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPADM')")
    public ResponseEntity<ClinicResponse> update(@PathVariable UUID id,
                                                 @Valid @RequestBody ClinicCreateRequest request) {
        return ResponseEntity.ok(clinicService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPADM')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        clinicService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
