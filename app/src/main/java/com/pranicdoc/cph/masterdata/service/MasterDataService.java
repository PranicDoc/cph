package com.pranicdoc.cph.masterdata.service;

import com.pranicdoc.cph.common.enums.ProtocolType;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.masterdata.dto.ChakraRequest;
import com.pranicdoc.cph.masterdata.dto.PranaColorRequest;
import com.pranicdoc.cph.masterdata.dto.ProtocolColorMappingRequest;
import com.pranicdoc.cph.masterdata.dto.ProtocolRequest;
import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolColorMapping;
import com.pranicdoc.cph.masterdata.repository.ChakraCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.PranaColorCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolColorMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MasterDataService {

    private final ChakraCatalogRepository chakraRepo;
    private final PranaColorCatalogRepository colorRepo;
    private final ProtocolColorMappingRepository mappingRepo;
    private final ProtocolCatalogRepository protocolRepo;
    private final SecurityContextUtil securityContextUtil;

    /**
     * Denies the caller unless they own {@code entityHealerId} (i.e. it matches their own
     * healerId) or the caller is a SUPADM. SYSTEM rows ({@code entityHealerId == null}) are
     * SUPADM-only. Single enforcement point for the cross-healer ownership guard across all
     * mutating master-data operations (MDIMPORT-19).
     */
    private void assertOwnership(UUID entityHealerId, boolean isSuperAdmin, String entityName, String action) {
        if (entityHealerId == null) {
            if (!isSuperAdmin) {
                throw new AccessDeniedException(entityName + " is a system row — only SUPADM may " + action + " it");
            }
            return;
        }
        if (!Objects.equals(entityHealerId, securityContextUtil.getCurrentHealerId()) && !isSuperAdmin) {
            throw new AccessDeniedException(entityName + " is owned by another party — not permitted to " + action);
        }
    }

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
        assertOwnership(chakra.getHealerId(), isSuperAdmin, "Chakra", "edit");
        chakra.setName(req.name());
        chakra.setCategory(req.category());
        chakra.setLocation(req.location());
        return chakraRepo.save(chakra);
    }

    @Transactional
    public void deleteChakra(UUID id, boolean isSuperAdmin) {
        ChakraCatalog chakra = getChakraById(id);
        assertOwnership(chakra.getHealerId(), isSuperAdmin, "Chakra", "delete");
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
        color.setApplications(req.applications() != null ? req.applications() : List.of());
        color.setDescription(req.description());
        color.setHealerId(securityContextUtil.getCurrentHealerId());
        return colorRepo.save(color);
    }

    @Transactional
    public PranaColorCatalog updateColor(UUID id, PranaColorRequest req, boolean isSuperAdmin) {
        PranaColorCatalog color = getColorById(id);
        assertOwnership(color.getHealerId(), isSuperAdmin, "Color", "edit");
        color.setName(req.name());
        color.setColorHex(req.colorHex());
        color.setProperties(req.properties() != null ? req.properties() : List.of());
        color.setApplications(req.applications() != null ? req.applications() : List.of());
        color.setDescription(req.description());
        return colorRepo.save(color);
    }

    @Transactional
    public void deleteColor(UUID id, boolean isSuperAdmin) {
        PranaColorCatalog color = getColorById(id);
        assertOwnership(color.getHealerId(), isSuperAdmin, "Color", "delete");
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
        assertOwnership(mapping.getHealerId(), isSuperAdmin, "Mapping", "delete");
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
        assertOwnership(protocol.getHealerId(), isSuperAdmin, "Protocol", "edit");
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
        assertOwnership(protocol.getHealerId(), isSuperAdmin, "Protocol", "delete");
        protocol.softDelete();
        protocolRepo.save(protocol);
    }
}
