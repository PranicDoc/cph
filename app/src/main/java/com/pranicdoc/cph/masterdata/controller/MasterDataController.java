package com.pranicdoc.cph.masterdata.controller;

import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
import com.pranicdoc.cph.masterdata.service.MasterDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/masterdata")
@RequiredArgsConstructor
public class MasterDataController {

    private final MasterDataService masterDataService;

    // --- Chakras ---

    @GetMapping("/chakras")
    public ResponseEntity<ApiResponse<List<ChakraCatalog>>> getChakras(
            @RequestParam(required = false) UUID clinicId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Success",masterDataService.getAllChakras(clinicId)));
    }

    @GetMapping("/chakras/{id}")
    public ResponseEntity<ChakraCatalog> getChakraById(@PathVariable UUID id) {
        return ResponseEntity.ok(masterDataService.getChakraById(id));
    }

    @GetMapping("/chakras/code/{code}")
    public ResponseEntity<ChakraCatalog> getChakraByCode(@PathVariable String code) {
        return ResponseEntity.ok(masterDataService.getChakraByCode(code));
    }

    // --- Prana Colors ---

    @GetMapping("/colors")
    public ResponseEntity<List<PranaColorCatalog>> getColors(
            @RequestParam(required = false) UUID clinicId) {
        return ResponseEntity.ok(masterDataService.getAllColors(clinicId));
    }

    @GetMapping("/colors/{id}")
    public ResponseEntity<PranaColorCatalog> getColorById(@PathVariable UUID id) {
        return ResponseEntity.ok(masterDataService.getColorById(id));
    }

    @GetMapping("/colors/code/{code}")
    public ResponseEntity<PranaColorCatalog> getColorByCode(@PathVariable String code) {
        return ResponseEntity.ok(masterDataService.getColorByCode(code));
    }
}
