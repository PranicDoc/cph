package com.pranicdoc.cph.healing.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pranicdoc.common.dto.ChakraReadingSummarySnapshot;
import com.pranicdoc.common.dto.ColorUsedSnapshot;
import com.pranicdoc.cph.common.enums.CaseStatus;
import com.pranicdoc.cph.common.enums.ColorUsageType;
import com.pranicdoc.cph.common.enums.ScanType;
import com.pranicdoc.cph.common.exception.CaseLockedException;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.healing.dto.HealerProtocolUnderstandingRequest;
import com.pranicdoc.cph.healing.dto.HealerProtocolUnderstandingRequest.ChakraColorSelection;
import com.pranicdoc.cph.healing.dto.HealerProtocolUnderstandingRequest.ColorSelection;
import com.pranicdoc.cph.healing.model.*;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.healing.repository.HealerProtocolUnderstandingRepository;
import com.pranicdoc.cph.healing.repository.SessionRepository;
import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolChakra;
import com.pranicdoc.cph.masterdata.model.ProtocolChakraColor;
import com.pranicdoc.cph.masterdata.repository.ChakraCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.PranaColorCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolChakraColorRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolChakraRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HealerProtocolUnderstandingService {

    private final CaseRepository caseRepository;
    private final HealerProtocolUnderstandingRepository hpuRepository;
    private final PranaColorCatalogRepository pranaColorCatalogRepository;
    private final SessionRepository sessionRepository;
    private final ProtocolCatalogRepository protocolCatalogRepository;
    private final ProtocolChakraRepository protocolChakraRepository;
    private final ProtocolChakraColorRepository protocolChakraColorRepository;
    private final ChakraCatalogRepository chakraCatalogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Synchronous replacement for the old AFTER_COMMIT event-listener auto-generation.
     * Persists exactly what the healer picked in the closure form's color/property
     * selector, instead of deriving colors from ProtocolColorMapping master data.
     */
    @Transactional
    public void saveFromHealerSelection(UUID healingCaseId, HealerProtocolUnderstandingRequest request) {
        HealingCase healingCase = caseRepository.findById(healingCaseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Healing case not found for id %s"
                                .formatted(healingCaseId)));
        if (healingCase.getStatus() == CaseStatus.LOCKED) throw new CaseLockedException();

        // A name can be one color per category, so each selection resolves in its own category
        // first and falls back to the name alone (uncategorised or legacy colors).
        List<PranaColorCatalog> catalog = pranaColorCatalogRepository
                .findByHealerIdIsNullOrHealerId(healingCase.getHealerId());
        Map<String, PranaColorCatalog> catalogByName = catalog.stream()
                .collect(Collectors.toMap(PranaColorCatalog::getName, c -> c, (a, b) -> a));
        Map<String, PranaColorCatalog> energisingByName = byNameIn(catalog, ColorUsageType.ENERGISING, catalogByName);
        Map<String, PranaColorCatalog> cleansingByName = byNameIn(catalog, ColorUsageType.CLEANSING, catalogByName);

        Map<String, List<ColorUsedSnapshot>> energisingColors = new LinkedHashMap<>();
        Map<String, List<ColorUsedSnapshot>> cleansingColors = new LinkedHashMap<>();

        for (ChakraColorSelection selection : request.chakraSelections()) {
            energisingColors.put(selection.chakra(), toColorSnapshots(selection.energisingColors(), energisingByName));
            cleansingColors.put(selection.chakra(), toColorSnapshots(selection.cleansingColors(), cleansingByName));
        }

        List<ChakraReadingSummarySnapshot> chakraReadings =
                healingCase.getSessions()
                        .stream()
                        .flatMap(session -> buildChakraReadings(session).stream())
                        .toList();

        HealingDetails details = healingCase.getHealingDetails();
        String protocolName = details != null ? details.getProtocolName() : null;

        HealerProtocolUnderstanding hpu = hpuRepository.findByHealingCaseId(healingCaseId)
                .orElseGet(HealerProtocolUnderstanding::new);
        hpu.setHealingCaseId(healingCaseId);
        hpu.setHealerId(healingCase.getHealerId());
        hpu.setCompletionDate(LocalDate.now());
        hpu.setProtocolName(protocolName);
        hpu.setCleansingColorsJson(serialize(cleansingColors));
        hpu.setEnergisingColorsJson(serialize(energisingColors));
        hpu.setChakraReadingsJson(serialize(chakraReadings));

        hpuRepository.save(hpu);
    }

    /**
     * Returns the previously saved selection for a case, so the closure form
     * can pre-populate instead of always starting blank -- reopening an
     * already-filled ACTIVE case then edits the same record via the upsert
     * in {@link #saveFromHealerSelection}, rather than losing track of what
     * was chosen before.
     */
    @Transactional(readOnly = true)
    public HealerProtocolUnderstandingRequest getExistingSelection(UUID healingCaseId) {
        Optional<HealerProtocolUnderstanding> existing = hpuRepository.findByHealingCaseId(healingCaseId);
        if (existing.isEmpty()) {
            return new HealerProtocolUnderstandingRequest(List.of());
        }

        HealerProtocolUnderstanding hpu = existing.get();
        Map<String, List<ColorUsedSnapshot>> energisingColors = deserializeColors(hpu.getEnergisingColorsJson());
        Map<String, List<ColorUsedSnapshot>> cleansingColors = deserializeColors(hpu.getCleansingColorsJson());

        Set<String> chakraNames = new LinkedHashSet<>();
        chakraNames.addAll(energisingColors.keySet());
        chakraNames.addAll(cleansingColors.keySet());

        List<ChakraColorSelection> chakraSelections = chakraNames.stream()
                .map(chakra -> new ChakraColorSelection(
                        chakra,
                        toColorSelections(energisingColors.get(chakra)),
                        toColorSelections(cleansingColors.get(chakra))))
                .toList();

        return new HealerProtocolUnderstandingRequest(chakraSelections);
    }

    /**
     * Pre-fill for the closure form: for each chakra scanned in this case, the cleansing and
     * energising colors the case's protocol(s) prescribe for that chakra in master data, with
     * properties left empty for the healer to pick. A chakra the protocols do not cover, or a
     * case whose protocols have no chakra/color data, comes back with empty lists, and the
     * form falls back to manual selection.
     */
    @Transactional(readOnly = true)
    public HealerProtocolUnderstandingRequest getProtocolSuggestions(UUID healingCaseId) {
        HealingCase healingCase = caseRepository.findById(healingCaseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Healing case not found for id %s"
                                .formatted(healingCaseId)));
        Map<String, Map<ColorUsageType, Set<String>>> colorsByChakra =
                protocolColorsByChakra(healingCase, healingCase.getHealerId());

        List<ChakraColorSelection> suggestions = sessionRepository.findAllScannedChakras(healingCaseId)
                .stream()
                .sorted()
                .map(chakra -> {
                    Map<ColorUsageType, Set<String>> colors =
                            colorsByChakra.getOrDefault(normalizeChakraName(chakra), Map.of());
                    return new ChakraColorSelection(
                            chakra,
                            toUnselectedColors(colors.get(ColorUsageType.ENERGISING)),
                            toUnselectedColors(colors.get(ColorUsageType.CLEANSING)));
                })
                .toList();
        return new HealerProtocolUnderstandingRequest(suggestions);
    }

    /**
     * Normalized chakra name -> usage -> color names, merged across every protocol named on the
     * case (HealingDetails.protocolName is a comma-joined list of catalog names). Only system rows
     * and the case healer's own rows count.
     */
    private Map<String, Map<ColorUsageType, Set<String>>> protocolColorsByChakra(HealingCase healingCase, UUID healerId) {
        HealingDetails details = healingCase.getHealingDetails();
        if (details == null || details.getProtocolName() == null || details.getProtocolName().isBlank()) {
            return Map.of();
        }
        Set<String> protocolNames = Arrays.stream(details.getProtocolName().split(","))
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toSet());
        List<UUID> protocolIds = protocolCatalogRepository.findByHealerIdIsNullOrHealerId(healerId)
                .stream()
                .filter(p -> p.getName() != null && protocolNames.contains(p.getName().trim().toLowerCase(Locale.ROOT)))
                .map(ProtocolCatalog::getId)
                .toList();
        if (protocolIds.isEmpty()) return Map.of();

        // Filtered here rather than via findByProtocol_IdAndHealerIdIsNullOrHealerId, whose derived
        // "A and B or C" also matches the healer's own links on other protocols.
        List<ProtocolChakra> links = protocolChakraRepository.findByProtocol_IdIn(protocolIds)
                .stream()
                .filter(link -> link.getHealerId() == null || link.getHealerId().equals(healerId))
                .toList();
        if (links.isEmpty()) return Map.of();

        Map<UUID, String> chakraNameById = chakraCatalogRepository
                .findAllById(links.stream().map(link -> link.getChakra().getId()).distinct().toList())
                .stream()
                .collect(Collectors.toMap(ChakraCatalog::getId, ChakraCatalog::getName));
        Map<UUID, String> colorNameById = pranaColorCatalogRepository.findByHealerIdIsNullOrHealerId(healerId)
                .stream()
                .collect(Collectors.toMap(PranaColorCatalog::getId, PranaColorCatalog::getName));
        Map<UUID, String> chakraKeyByLinkId = new HashMap<>();
        for (ProtocolChakra link : links) {
            String chakraName = chakraNameById.get(link.getChakra().getId());
            if (chakraName != null) chakraKeyByLinkId.put(link.getId(), normalizeChakraName(chakraName));
        }

        List<ProtocolChakraColor> linkColors = protocolChakraColorRepository.findByProtocolChakra_IdIn(
                links.stream().map(ProtocolChakra::getId).toList());
        Map<String, Map<ColorUsageType, Set<String>>> result = new HashMap<>();
        for (ProtocolChakraColor linkColor : linkColors) {
            String chakraKey = chakraKeyByLinkId.get(linkColor.getProtocolChakra().getId());
            String colorName = colorNameById.get(linkColor.getColor().getId());
            if (chakraKey == null || colorName == null || linkColor.getUsageType() == null) continue;
            result.computeIfAbsent(chakraKey, k -> new EnumMap<>(ColorUsageType.class))
                    .computeIfAbsent(linkColor.getUsageType(), k -> new TreeSet<>())
                    .add(colorName);
        }
        return result;
    }

    /** "Solar Plexus Chakra", "solar plexus" and " Solar  Plexus " all match one another. */
    static String normalizeChakraName(String name) {
        String normalized = name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return normalized.endsWith(" chakra")
                ? normalized.substring(0, normalized.length() - " chakra".length())
                : normalized;
    }

    private static List<ColorSelection> toUnselectedColors(Set<String> colorNames) {
        if (colorNames == null || colorNames.isEmpty()) return Collections.emptyList();
        return colorNames.stream().map(name -> new ColorSelection(name, List.of())).toList();
    }

    // ── Deserializers (used by mapper) ───────────────────────────────────────

    public Map<String, List<ColorUsedSnapshot>> deserializeColors(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("Failed to deserialize colors JSON", e);
            return Collections.emptyMap();
        }
    }

    public List<ChakraReadingSummarySnapshot> deserializeChakraReadings(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize chakra readings JSON", e);
            return Collections.emptyList();
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Name -> color of that category, over the name-only map for names with no such row. */
    private static Map<String, PranaColorCatalog> byNameIn(List<PranaColorCatalog> catalog, ColorUsageType category,
                                                           Map<String, PranaColorCatalog> fallback) {
        Map<String, PranaColorCatalog> result = new HashMap<>(fallback);
        catalog.stream()
                .filter(c -> c.getCategory() == category)
                .forEach(c -> result.put(c.getName(), c));
        return result;
    }

    private List<ColorUsedSnapshot> toColorSnapshots(
            List<ColorSelection> selections,
            Map<String, PranaColorCatalog> catalogByName) {

        if (selections == null || selections.isEmpty()) return Collections.emptyList();

        return selections.stream()
                .map(selection -> {
                    PranaColorCatalog color = catalogByName.get(selection.color());
                    String code = color != null ? color.getCode() : selection.color();
                    String properties = selection.properties() == null
                            ? ""
                            : String.join(", ", selection.properties());
                    return new ColorUsedSnapshot(code, selection.color(), properties);
                })
                .toList();
    }

    private List<ColorSelection> toColorSelections(List<ColorUsedSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) return Collections.emptyList();

        return snapshots.stream()
                .map(snapshot -> new ColorSelection(
                        snapshot.colorName(),
                        snapshot.properties() == null || snapshot.properties().isBlank()
                                ? Collections.emptyList()
                                : List.of(snapshot.properties().split(", "))))
                .toList();
    }

    private List<ChakraReadingSummarySnapshot> buildChakraReadings(HealingSession session) {
        Map<UUID, ChakraReading> preMap = session.getScans().stream()
                .filter(s -> s.getScanType() == ScanType.PRE)
                .findFirst()
                .map(scan -> scan.getReadings().stream()
                        .collect(Collectors.toMap(ChakraReading::getChakraId, r -> r)))
                .orElse(Collections.emptyMap());

        Map<UUID, ChakraReading> postMap = session.getScans().stream()
                .filter(s -> s.getScanType() == ScanType.POST)
                .findFirst()
                .map(scan -> scan.getReadings().stream()
                        .collect(Collectors.toMap(ChakraReading::getChakraId, r -> r)))
                .orElse(Collections.emptyMap());

        return preMap.entrySet().stream()
                .map(e -> {
                    ChakraReading pre = e.getValue();
                    ChakraReading post = postMap.get(e.getKey());
                    return new ChakraReadingSummarySnapshot(
                            pre.getChakraName(),
                            pre.getEnergyLevel(), pre.getActivationLevel(),
                            post != null ? post.getEnergyLevel() : null,
                            post != null ? post.getActivationLevel() : null
                    );
                })
                .toList();
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.error("Failed to serialize HPU field", e);
            return "[]";
        }
    }
}
