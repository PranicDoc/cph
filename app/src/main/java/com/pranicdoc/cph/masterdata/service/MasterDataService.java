package com.pranicdoc.cph.masterdata.service;

import com.pranicdoc.cph.common.enums.ProtocolType;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.masterdata.dto.ChakraRequest;
import com.pranicdoc.cph.masterdata.dto.PranaColorRequest;
import com.pranicdoc.cph.masterdata.dto.ProtocolChakraColorView;
import com.pranicdoc.cph.masterdata.dto.ProtocolColorMappingRequest;
import com.pranicdoc.cph.masterdata.dto.ProtocolRequest;
import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolColorMapping;
import com.pranicdoc.cph.masterdata.repository.ChakraCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.PranaColorCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolChakraColorRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolChakraRepository;
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
    private final ProtocolCatalogRepository protocolRepo;
    private final ProtocolChakraRepository protocolChakraRepo;
    private final ProtocolChakraColorRepository protocolChakraColorRepo;
    private final SecurityContextUtil securityContextUtil;

    // ── Chakras ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ChakraCatalog> getAllChakras(UUID healerId) {
        if (healerId == null) return chakraRepo.findByHealerIdIsNull();
        return chakraRepo.findByHealerIdIsNullOrHealerId(healerId);
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
        chakra.setHealerId(securityContextUtil.getCurrentHealerId());
        return chakraRepo.save(chakra);
    }

    @Transactional
    public ChakraCatalog updateChakra(UUID id, ChakraRequest req, boolean isSuperAdmin) {
        ChakraCatalog chakra = getChakraById(id);
        if (chakra.getHealerId() == null && !isSuperAdmin)
            throw new AccessDeniedException("System chakras can only be edited by SUPADM");
        chakra.setName(req.name());
        chakra.setCategory(req.category());
        chakra.setLocation(req.location());
        return chakraRepo.save(chakra);
    }

    @Transactional
    public void deleteChakra(UUID id, boolean isSuperAdmin) {
        ChakraCatalog chakra = getChakraById(id);
        if (chakra.getHealerId() == null && !isSuperAdmin)
            throw new AccessDeniedException("System chakras can only be deleted by SUPADM");
        chakra.softDelete();
        chakraRepo.save(chakra);
    }

    // ── Prana Colors ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PranaColorCatalog> getAllColors(UUID healerId) {
        if (healerId == null) return colorRepo.findByHealerIdIsNull();
        return colorRepo.findByHealerIdIsNullOrHealerId(healerId);
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
        color.setProperties(req.properties() != null ? req.properties() : List.of());
        color.setHealerId(securityContextUtil.getCurrentHealerId());
        return colorRepo.save(color);
    }

    @Transactional
    public PranaColorCatalog updateColor(UUID id, PranaColorRequest req, boolean isSuperAdmin) {
        PranaColorCatalog color = getColorById(id);
        if (color.getHealerId() == null && !isSuperAdmin)
            throw new AccessDeniedException("System colors can only be edited by SUPADM");
        color.setName(req.name());
        color.setColorHex(req.colorHex());
        color.setProperties(req.properties() != null ? req.properties() : List.of());
        return colorRepo.save(color);
    }

    @Transactional
    public void deleteColor(UUID id, boolean isSuperAdmin) {
        PranaColorCatalog color = getColorById(id);
        if (color.getHealerId() == null && !isSuperAdmin)
            throw new AccessDeniedException("System colors can only be deleted by SUPADM");
        color.softDelete();
        colorRepo.save(color);
    }

    // ── Protocol Color Mappings ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProtocolColorMapping> getAllMappings(UUID healerId) {
        if (healerId == null) return mappingRepo.findByHealerIdIsNull();
        return mappingRepo.findByHealerIdIsNullOrHealerId(healerId);
    }

    @Transactional
    public List<ProtocolColorMapping> createMappings(ProtocolColorMappingRequest req) {
        UUID healerId = securityContextUtil.getCurrentHealerId();
        List<ProtocolColorMapping> entities = req.colorIds().stream().map(colorId -> {
            ProtocolColorMapping m = new ProtocolColorMapping();
            m.setProtocolName(req.protocolName());
            m.setColorId(colorId);
            m.setUsageType(req.usageType());
            m.setHealerId(healerId);
            m.setChakraName(req.chakraName());
            return m;
        }).toList();
        return mappingRepo.saveAll(entities);
    }

    @Transactional
    public void deleteMapping(UUID id, boolean isSuperAdmin) {
        ProtocolColorMapping mapping = mappingRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProtocolColorMapping", id));
        if (mapping.getHealerId() == null && !isSuperAdmin)
            throw new AccessDeniedException("System mappings can only be deleted by SUPADM");
        mapping.softDelete();
        mappingRepo.save(mapping);
    }

    // ── Protocols ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProtocolCatalog> getAllProtocols(UUID healerId, ProtocolType type) {
        if (type != null) {
            return healerId == null
                    ? protocolRepo.findByProtocolTypeAndHealerIdIsNull(type)
                    : protocolRepo.findByProtocolTypeAndHealerIdIsNullOrHealerId(type, healerId);
        }
        return healerId == null
                ? protocolRepo.findByHealerIdIsNull()
                : protocolRepo.findByHealerIdIsNullOrHealerId(healerId);
    }

    @Transactional(readOnly = true)
    public ProtocolCatalog getProtocolById(UUID id) {
        return protocolRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProtocolCatalog", id));
    }

    @Transactional
    public ProtocolCatalog createProtocol(ProtocolRequest req) {
        ProtocolCatalog protocol = new ProtocolCatalog();
        protocol.setName(req.name());
        protocol.setSourceBook(req.sourceBook());
        protocol.setPageNumber(req.pageNumber());
        protocol.setCategory(req.category());
        protocol.setDescription(req.description());
        protocol.setChapter(req.chapter());
        protocol.setProtocolType(req.protocolType());
        protocol.setHealerId(securityContextUtil.getCurrentHealerId());
        return protocolRepo.save(protocol);
    }

    @Transactional
    public ProtocolCatalog updateProtocol(UUID id, ProtocolRequest req, boolean isSuperAdmin) {
        ProtocolCatalog protocol = getProtocolById(id);
        if (protocol.getHealerId() == null && !isSuperAdmin)
            throw new AccessDeniedException("System protocols can only be edited by SUPADM");
        protocol.setName(req.name());
        protocol.setSourceBook(req.sourceBook());
        protocol.setPageNumber(req.pageNumber());
        protocol.setCategory(req.category());
        protocol.setDescription(req.description());
        protocol.setChapter(req.chapter());
        protocol.setProtocolType(req.protocolType());
        return protocolRepo.save(protocol);
    }

    @Transactional
    public void deleteProtocol(UUID id, boolean isSuperAdmin) {
        ProtocolCatalog protocol = getProtocolById(id);
        if (protocol.getHealerId() == null && !isSuperAdmin)
            throw new AccessDeniedException("System protocols can only be deleted by SUPADM");
        protocol.softDelete();
        protocolRepo.save(protocol);
    }

    // ── Protocol <-> Chakra <-> Color (filtered master-data read flow) ────────

    /**
     * Step 2 of the read flow: distinct chakras associated with any of the
     * given protocols. Returns empty for an empty/null input rather than
     * running a query that would otherwise match nothing (an empty "in"
     * clause is invalid JPQL).
     */
    @Transactional(readOnly = true)
    public List<ChakraCatalog> getChakrasForProtocols(List<UUID> protocolIds) {
        if (protocolIds == null || protocolIds.isEmpty()) return List.of();
        return protocolChakraRepo.findDistinctChakrasByProtocolIds(protocolIds);
    }

    /**
     * Step 3 of the read flow: colors (cleansing + energizing, distinguished
     * by usage type) for the given protocol + chakra selection.
     */
    @Transactional(readOnly = true)
    public List<ProtocolChakraColorView> getColorsForProtocolsAndChakras(List<UUID> protocolIds, List<UUID> chakraIds) {
        if (protocolIds == null || protocolIds.isEmpty() || chakraIds == null || chakraIds.isEmpty()) return List.of();
        return protocolChakraColorRepo.findColorsForProtocolsAndChakras(protocolIds, chakraIds);
    }
}
