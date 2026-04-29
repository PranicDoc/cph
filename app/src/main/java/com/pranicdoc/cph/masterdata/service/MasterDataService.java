package com.pranicdoc.cph.masterdata.service;

import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
import com.pranicdoc.cph.masterdata.repository.ChakraCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.PranaColorCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MasterDataService {

    private final ChakraCatalogRepository chakraRepo;
    private final PranaColorCatalogRepository colorRepo;

    // --- Chakras ---

    public List<ChakraCatalog> getAllChakras(UUID clinicId) {
        if (clinicId == null) {
            return chakraRepo.findByClinicIdIsNull();
        }
        return chakraRepo.findByClinicIdIsNullOrClinicId(clinicId);
    }

    public ChakraCatalog getChakraById(UUID id) {
        return chakraRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ChakraCatalog", id));
    }

    public ChakraCatalog getChakraByCode(String code) {
        return chakraRepo.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("ChakraCatalog", code));
    }

    // --- Prana Colors ---

    public List<PranaColorCatalog> getAllColors(UUID clinicId) {
        if (clinicId == null) {
            return colorRepo.findByClinicIdIsNull();
        }
        return colorRepo.findByClinicIdIsNullOrClinicId(clinicId);
    }

    public PranaColorCatalog getColorById(UUID id) {
        return colorRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PranaColorCatalog", id));
    }

    public PranaColorCatalog getColorByCode(String code) {
        return colorRepo.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("PranaColorCatalog", code));
    }
}
