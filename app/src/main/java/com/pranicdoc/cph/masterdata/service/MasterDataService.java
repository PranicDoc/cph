package com.pranicdoc.cph.masterdata.service;

import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.enums.ColorUsageType;
import com.pranicdoc.cph.common.enums.ProtocolType;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.masterdata.dto.ChakraRequest;
import com.pranicdoc.cph.masterdata.dto.PranaColorRequest;
import com.pranicdoc.cph.masterdata.dto.ProtocolChakraColorAddRequest;
import com.pranicdoc.cph.masterdata.dto.ProtocolChakraColorEntry;
import com.pranicdoc.cph.masterdata.dto.ProtocolChakraColorGroup;
import com.pranicdoc.cph.masterdata.dto.ProtocolChakraColorUpdateRequest;
import com.pranicdoc.cph.masterdata.dto.ProtocolChakraColorView;
import com.pranicdoc.cph.masterdata.dto.ProtocolColorMappingRequest;
import com.pranicdoc.cph.masterdata.dto.ProtocolRequest;
import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolChakra;
import com.pranicdoc.cph.masterdata.model.ProtocolChakraColor;
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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

    @Auditable(action = "CREATE_CHAKRA")
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

    @Auditable(action = "UPDATE_CHAKRA")
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

    @Auditable(action = "DELETE_CHAKRA")
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

    @Auditable(action = "CREATE_COLOR")
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

    @Auditable(action = "UPDATE_COLOR")
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

    @Auditable(action = "DELETE_COLOR")
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

    @Auditable(action = "CREATE_PROTOCOL_MAPPING")
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

    @Auditable(action = "DELETE_PROTOCOL_MAPPING")
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

    @Auditable(action = "CREATE_PROTOCOL")
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

    @Auditable(action = "UPDATE_PROTOCOL")
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

    @Auditable(action = "DELETE_PROTOCOL")
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
     * given protocols, visible to the current healer (system rows union
     * their own). Returns empty for an empty/null input rather than running
     * a query that would otherwise match nothing (an empty "in" clause is
     * invalid JPQL).
     */
    @Transactional(readOnly = true)
    public List<ChakraCatalog> getChakrasForProtocols(List<UUID> protocolIds) {
        if (protocolIds == null || protocolIds.isEmpty()) return List.of();
        return protocolChakraRepo.findDistinctChakrasByProtocolIdsForHealer(
                protocolIds, securityContextUtil.getCurrentHealerId());
    }

    /**
     * Step 3 of the read flow: colors (cleansing + energizing, distinguished
     * by usage type) for the given protocol + chakra selection, visible to
     * the current healer (system rows union their own).
     */
    @Transactional(readOnly = true)
    public List<ProtocolChakraColorView> getColorsForProtocolsAndChakras(List<UUID> protocolIds, List<UUID> chakraIds) {
        if (protocolIds == null || protocolIds.isEmpty() || chakraIds == null || chakraIds.isEmpty()) return List.of();
        return protocolChakraColorRepo.findColorsForProtocolsAndChakrasForHealer(
                protocolIds, chakraIds, securityContextUtil.getCurrentHealerId());
    }

    // ── Protocol <-> Chakra <-> Color (single-protocol manage view) ──────────
    // Distinct from the read flow above: this is the "select one protocol,
    // add/edit/remove its chakras and colors" admin surface. A healer's own
    // additions here are visible and editable only by them (or SUPADM);
    // system (bulk-imported) links stay read-only for everyone else.

    @Transactional(readOnly = true)
    public List<ProtocolChakraColorGroup> getChakraColorGroupsForProtocol(UUID protocolId) {
        UUID healerId = securityContextUtil.getCurrentHealerId();
        List<ProtocolChakra> links = protocolChakraRepo.findByProtocol_IdAndHealerIdIsNullOrHealerId(protocolId, healerId);
        if (links.isEmpty()) return List.of();

        List<UUID> linkIds = links.stream().map(ProtocolChakra::getId).toList();
        Map<UUID, List<ProtocolChakraColor>> colorsByLink = protocolChakraColorRepo.findByProtocolChakra_IdIn(linkIds)
                .stream()
                .collect(Collectors.groupingBy(pcc -> pcc.getProtocolChakra().getId()));

        return links.stream()
                .map(link -> toGroup(link, colorsByLink.getOrDefault(link.getId(), List.of())))
                .toList();
    }

    /**
     * Add one or more chakras (each with cleansing/energising colors) to a
     * protocol. Always find-or-creates the CALLER's own {@code ProtocolChakra}
     * row (healerId = current healer) — never touches a system row or
     * another healer's row, so no ownership check is needed here (unlike
     * update/delete below, which target an existing row by id).
     */
    @Auditable(action = "ADD_PROTOCOL_CHAKRA_COLORS")
    @Transactional
    public List<ProtocolChakraColorGroup> addProtocolChakraColors(UUID protocolId, ProtocolChakraColorAddRequest req) {
        ProtocolCatalog protocol = getProtocolById(protocolId);
        UUID healerId = securityContextUtil.getCurrentHealerId();

        for (ProtocolChakraColorEntry entry : req.entries()) {
            ChakraCatalog chakra = getChakraById(entry.chakraId());
            ProtocolChakra link = protocolChakraRepo
                    .findByProtocol_IdAndChakra_IdAndHealerId(protocolId, entry.chakraId(), healerId)
                    .orElseGet(() -> protocolChakraRepo.save(
                            ProtocolChakra.builder().protocol(protocol).chakra(chakra).healerId(healerId).build()));

            Set<ColorLinkKey> existing = protocolChakraColorRepo.findByProtocolChakra_IdIn(List.of(link.getId()))
                    .stream()
                    .map(pcc -> new ColorLinkKey(pcc.getColor().getId(), pcc.getUsageType()))
                    .collect(Collectors.toSet());

            addNewColors(link, entry.cleansingColorIds(), ColorUsageType.CLEANSING, existing);
            addNewColors(link, entry.energisingColorIds(), ColorUsageType.ENERGISING, existing);
        }

        return getChakraColorGroupsForProtocol(protocolId);
    }

    /** Full replace of one existing link's color set (diffed, not delete-all-then-recreate — see below). */
    @Auditable(action = "UPDATE_PROTOCOL_CHAKRA_COLOR")
    @Transactional
    public List<ProtocolChakraColorGroup> updateProtocolChakraColor(
            UUID protocolId, UUID protocolChakraId, ProtocolChakraColorUpdateRequest req, boolean isSuperAdmin) {
        ProtocolChakra link = getOwnedProtocolChakra(protocolId, protocolChakraId, isSuperAdmin);

        List<ProtocolChakraColor> existing = protocolChakraColorRepo.findByProtocolChakra_IdIn(List.of(protocolChakraId));
        replaceColorsForUsage(link, existing, req.cleansingColorIds(), ColorUsageType.CLEANSING);
        replaceColorsForUsage(link, existing, req.energisingColorIds(), ColorUsageType.ENERGISING);

        return getChakraColorGroupsForProtocol(protocolId);
    }

    /** Removes a chakra (and its colors) from a protocol. */
    @Auditable(action = "DELETE_PROTOCOL_CHAKRA_COLOR")
    @Transactional
    public void deleteProtocolChakraColor(UUID protocolId, UUID protocolChakraId, boolean isSuperAdmin) {
        ProtocolChakra link = getOwnedProtocolChakra(protocolId, protocolChakraId, isSuperAdmin);
        List<ProtocolChakraColor> colors = protocolChakraColorRepo.findByProtocolChakra_IdIn(List.of(protocolChakraId));
        colors.forEach(c -> { c.softDelete(); protocolChakraColorRepo.save(c); });
        link.softDelete();
        protocolChakraRepo.save(link);
    }

    /**
     * Ownership check for update/delete: system rows (healerId null) require
     * the SUPADM override; a non-system row may only be touched by the
     * healer who owns it, never by another healer even with the same grant.
     */
    private ProtocolChakra getOwnedProtocolChakra(UUID protocolId, UUID protocolChakraId, boolean isSuperAdmin) {
        ProtocolChakra link = protocolChakraRepo.findById(protocolChakraId)
                .orElseThrow(() -> new ResourceNotFoundException("ProtocolChakra", protocolChakraId));
        if (!link.getProtocol().getId().equals(protocolId)) {
            throw new ResourceNotFoundException("ProtocolChakra", protocolChakraId);
        }
        UUID ownerHealerId = link.getHealerId();
        if (ownerHealerId == null) {
            if (!isSuperAdmin) throw new AccessDeniedException("System protocol-chakra links can only be edited by SUPADM");
            return link;
        }
        if (!ownerHealerId.equals(securityContextUtil.getCurrentHealerId())) {
            throw new AccessDeniedException("You can only edit your own protocol-chakra links");
        }
        return link;
    }

    private void addNewColors(ProtocolChakra link, List<UUID> colorIds, ColorUsageType usageType, Set<ColorLinkKey> existing) {
        if (colorIds == null) return;
        for (UUID colorId : colorIds) {
            if (existing.contains(new ColorLinkKey(colorId, usageType))) continue;
            PranaColorCatalog color = getColorById(colorId);
            protocolChakraColorRepo.save(ProtocolChakraColor.builder()
                    .protocolChakra(link).color(color).usageType(usageType).build());
        }
    }

    /** Diffs desired vs current colors for one usage type — soft-deletes removed, inserts added, leaves the rest untouched. */
    private void replaceColorsForUsage(ProtocolChakra link, List<ProtocolChakraColor> existing, List<UUID> desiredColorIds, ColorUsageType usageType) {
        Set<UUID> desired = desiredColorIds == null ? Set.of() : new HashSet<>(desiredColorIds);
        Map<UUID, ProtocolChakraColor> currentByColor = existing.stream()
                .filter(c -> c.getUsageType() == usageType)
                .collect(Collectors.toMap(c -> c.getColor().getId(), c -> c));

        currentByColor.forEach((colorId, row) -> {
            if (!desired.contains(colorId)) {
                row.softDelete();
                protocolChakraColorRepo.save(row);
            }
        });
        for (UUID colorId : desired) {
            if (!currentByColor.containsKey(colorId)) {
                PranaColorCatalog color = getColorById(colorId);
                protocolChakraColorRepo.save(ProtocolChakraColor.builder()
                        .protocolChakra(link).color(color).usageType(usageType).build());
            }
        }
    }

    private ProtocolChakraColorGroup toGroup(ProtocolChakra link, List<ProtocolChakraColor> colors) {
        List<UUID> cleansing = colors.stream()
                .filter(c -> c.getUsageType() == ColorUsageType.CLEANSING)
                .map(c -> c.getColor().getId())
                .toList();
        List<UUID> energising = colors.stream()
                .filter(c -> c.getUsageType() == ColorUsageType.ENERGISING)
                .map(c -> c.getColor().getId())
                .toList();
        return new ProtocolChakraColorGroup(
                link.getId(), link.getChakra().getId(), link.getChakra().getName(), link.getHealerId(), cleansing, energising);
    }

    private record ColorLinkKey(UUID colorId, ColorUsageType usageType) {}
}
