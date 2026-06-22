package com.pranicdoc.cph.masterdata.service;

import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.masterdata.dto.ChakraRequest;
import com.pranicdoc.cph.masterdata.dto.PranaColorRequest;
import com.pranicdoc.cph.masterdata.dto.ProtocolColorMappingRequest;
import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolColorMapping;
import com.pranicdoc.cph.masterdata.repository.ChakraCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.PranaColorCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolColorMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MasterDataService {

    private final ChakraCatalogRepository chakraRepo;
    private final PranaColorCatalogRepository colorRepo;
    private final ProtocolColorMappingRepository mappingRepo;

    // ── Chakras ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ChakraCatalog> getAllChakras(UUID clinicId) {
        if (clinicId == null) return chakraRepo.findByClinicIdIsNull();
        return chakraRepo.findByClinicIdIsNullOrClinicId(clinicId);
    }

    @Transactional(readOnly = true)
    public ChakraCatalog getChakraById(UUID id) {
        return chakraRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ChakraCatalog", id));
    }

    @Transactional(readOnly = true)
    public ChakraCatalog getChakraByCode(String code) {
        return chakraRepo.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("ChakraCatalog", code));
    }

    @Transactional
    public ChakraCatalog createChakra(ChakraRequest req) {
        ChakraCatalog chakra = new ChakraCatalog();
        chakra.setName(req.name());
        chakra.setCode(req.code().toUpperCase());
        chakra.setCategory(req.category());
        chakra.setLocation(req.location());
        chakra.setClinicId(req.clinicId());
        return chakraRepo.save(chakra);
    }

    @Transactional
    public ChakraCatalog updateChakra(UUID id, ChakraRequest req, boolean isSuperAdmin) {
        ChakraCatalog chakra = getChakraById(id);
        if (chakra.getClinicId() == null && !isSuperAdmin)
            throw new AccessDeniedException("System chakras can only be edited by SUPADM");
        chakra.setName(req.name());
        chakra.setCategory(req.category());
        chakra.setLocation(req.location());
        return chakraRepo.save(chakra);
    }

    @Transactional
    public void deleteChakra(UUID id, boolean isSuperAdmin) {
        ChakraCatalog chakra = getChakraById(id);
        if (chakra.getClinicId() == null && !isSuperAdmin)
            throw new AccessDeniedException("System chakras can only be deleted by SUPADM");
        chakra.softDelete();
        chakraRepo.save(chakra);
    }

    // ── Prana Colors ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PranaColorCatalog> getAllColors(UUID clinicId) {
        if (clinicId == null) return colorRepo.findByClinicIdIsNull();
        return colorRepo.findByClinicIdIsNullOrClinicId(clinicId);
    }

    @Transactional(readOnly = true)
    public PranaColorCatalog getColorById(UUID id) {
        return colorRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PranaColorCatalog", id));
    }

    @Transactional(readOnly = true)
    public PranaColorCatalog getColorByCode(String code) {
        return colorRepo.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("PranaColorCatalog", code));
    }

    @Transactional
    public PranaColorCatalog createColor(PranaColorRequest req) {
        PranaColorCatalog color = new PranaColorCatalog();
        color.setName(req.name());
        color.setCode(req.code().toUpperCase());
        color.setColorHex(req.colorHex());
        color.setProperties(req.properties());
        color.setClinicId(req.clinicId());
        return colorRepo.save(color);
    }

    @Transactional
    public PranaColorCatalog updateColor(UUID id, PranaColorRequest req, boolean isSuperAdmin) {
        PranaColorCatalog color = getColorById(id);
        if (color.getClinicId() == null && !isSuperAdmin)
            throw new AccessDeniedException("System colors can only be edited by SUPADM");
        color.setName(req.name());
        color.setColorHex(req.colorHex());
        color.setProperties(req.properties());
        return colorRepo.save(color);
    }

    @Transactional
    public void deleteColor(UUID id, boolean isSuperAdmin) {
        PranaColorCatalog color = getColorById(id);
        if (color.getClinicId() == null && !isSuperAdmin)
            throw new AccessDeniedException("System colors can only be deleted by SUPADM");
        color.softDelete();
        colorRepo.save(color);
    }

    // ── Protocol Color Mappings ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProtocolColorMapping> getAllMappings(UUID clinicId) {
        if (clinicId == null) return mappingRepo.findByClinicIdIsNull();
        return mappingRepo.findByClinicIdIsNullOrClinicId(clinicId);
    }

    @Transactional
    public List<ProtocolColorMapping> createMappings(ProtocolColorMappingRequest req) {
        List<ProtocolColorMapping> entities = req.colorIds().stream().map(colorId -> {
            ProtocolColorMapping m = new ProtocolColorMapping();
            m.setProtocolName(req.protocolName());
            m.setColorId(colorId);
            m.setUsageType(req.usageType());
            m.setClinicId(req.clinicId());
            return m;
        }).toList();
        return mappingRepo.saveAll(entities);
    }

    @Transactional
    public void deleteMapping(UUID id, boolean isSuperAdmin) {
        ProtocolColorMapping mapping = mappingRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProtocolColorMapping", id));
        if (mapping.getClinicId() == null && !isSuperAdmin)
            throw new AccessDeniedException("System mappings can only be deleted by SUPADM");
        mapping.softDelete();
        mappingRepo.save(mapping);
    }
}
