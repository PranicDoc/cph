package com.pranicdoc.cph.masterdata.controller;

import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.masterdata.dto.ChakraRequest;
import com.pranicdoc.cph.masterdata.dto.PranaColorRequest;
import com.pranicdoc.cph.masterdata.dto.ProtocolColorMappingRequest;
import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolColorMapping;
import com.pranicdoc.cph.masterdata.service.MasterDataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/masterdata")
@RequiredArgsConstructor
public class MasterDataController {

    private final MasterDataService masterDataService;
    private final SecurityContextUtil securityContextUtil;

    @GetMapping("/chakras")
    public ResponseEntity<ApiResponse<List<ChakraCatalog>>> getChakras(
            @RequestParam(required = false) UUID clinicId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Success", masterDataService.getAllChakras(clinicId)));
    }

    @GetMapping("/chakras/{id}")
    public ResponseEntity<ApiResponse<ChakraCatalog>> getChakraById(@PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Success", masterDataService.getChakraById(id)));
    }

    @GetMapping("/chakras/code/{code}")
    public ResponseEntity<ApiResponse<ChakraCatalog>> getChakraByCode(@PathVariable String code) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Success", masterDataService.getChakraByCode(code)));
    }

    @PostMapping("/chakras")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPADM')")
    public ResponseEntity<ApiResponse<ChakraCatalog>> createChakra(@Valid @RequestBody ChakraRequest req) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Chakra created", masterDataService.createChakra(req)));
    }

    @PutMapping("/chakras/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPADM')")
    public ResponseEntity<ApiResponse<ChakraCatalog>> updateChakra(
            @PathVariable UUID id, @Valid @RequestBody ChakraRequest req) {
        boolean isSuperAdmin = "SUPADM".equals(securityContextUtil.getCurrentRole());
        return ResponseEntity.ok(new ApiResponse<>(true, "Chakra updated", masterDataService.updateChakra(id, req, isSuperAdmin)));
    }

    @DeleteMapping("/chakras/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPADM')")
    public ResponseEntity<ApiResponse<Void>> deleteChakra(@PathVariable UUID id) {
        boolean isSuperAdmin = "SUPADM".equals(securityContextUtil.getCurrentRole());
        masterDataService.deleteChakra(id, isSuperAdmin);
        return ResponseEntity.ok(new ApiResponse<>(true, "Chakra deleted", null));
    }

    @GetMapping("/colors")
    public ResponseEntity<ApiResponse<List<PranaColorCatalog>>> getColors(
            @RequestParam(required = false) UUID clinicId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Success", masterDataService.getAllColors(clinicId)));
    }

    @GetMapping("/colors/{id}")
    public ResponseEntity<ApiResponse<PranaColorCatalog>> getColorById(@PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Success", masterDataService.getColorById(id)));
    }

    @GetMapping("/colors/code/{code}")
    public ResponseEntity<ApiResponse<PranaColorCatalog>> getColorByCode(@PathVariable String code) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Success", masterDataService.getColorByCode(code)));
    }

    @PostMapping("/colors")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPADM')")
    public ResponseEntity<ApiResponse<PranaColorCatalog>> createColor(@Valid @RequestBody PranaColorRequest req) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Color created", masterDataService.createColor(req)));
    }

    @PutMapping("/colors/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPADM')")
    public ResponseEntity<ApiResponse<PranaColorCatalog>> updateColor(
            @PathVariable UUID id, @Valid @RequestBody PranaColorRequest req) {
        boolean isSuperAdmin = "SUPADM".equals(securityContextUtil.getCurrentRole());
        return ResponseEntity.ok(new ApiResponse<>(true, "Color updated", masterDataService.updateColor(id, req, isSuperAdmin)));
    }

    @DeleteMapping("/colors/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPADM')")
    public ResponseEntity<ApiResponse<Void>> deleteColor(@PathVariable UUID id) {
        boolean isSuperAdmin = "SUPADM".equals(securityContextUtil.getCurrentRole());
        masterDataService.deleteColor(id, isSuperAdmin);
        return ResponseEntity.ok(new ApiResponse<>(true, "Color deleted", null));
    }

    @GetMapping("/protocol-mappings")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPADM')")
    public ResponseEntity<ApiResponse<List<ProtocolColorMapping>>> getMappings(
            @RequestParam(required = false) UUID clinicId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Success", masterDataService.getAllMappings(clinicId)));
    }

    @PostMapping("/protocol-mappings")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPADM')")
    public ResponseEntity<ApiResponse<List<ProtocolColorMapping>>> createMappings(
            @Valid @RequestBody ProtocolColorMappingRequest req) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Mappings created", masterDataService.createMappings(req)));
    }

    @DeleteMapping("/protocol-mappings/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPADM')")
    public ResponseEntity<ApiResponse<Void>> deleteMapping(@PathVariable UUID id) {
        boolean isSuperAdmin = "SUPADM".equals(securityContextUtil.getCurrentRole());
        masterDataService.deleteMapping(id, isSuperAdmin);
        return ResponseEntity.ok(new ApiResponse<>(true, "Mapping deleted", null));
    }
}
