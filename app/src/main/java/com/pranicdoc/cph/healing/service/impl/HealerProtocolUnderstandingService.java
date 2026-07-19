package com.pranicdoc.cph.healing.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pranicdoc.common.dto.ChakraReadingSummarySnapshot;
import com.pranicdoc.common.dto.ColorUsedSnapshot;
import com.pranicdoc.cph.common.enums.ScanType;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.healing.dto.HealerProtocolUnderstandingRequest;
import com.pranicdoc.cph.healing.dto.HealerProtocolUnderstandingRequest.ChakraColorSelection;
import com.pranicdoc.cph.healing.dto.HealerProtocolUnderstandingRequest.ColorSelection;
import com.pranicdoc.cph.healing.model.*;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.healing.repository.HealerProtocolUnderstandingRepository;
import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
import com.pranicdoc.cph.masterdata.repository.PranaColorCatalogRepository;
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

        Map<String, PranaColorCatalog> catalogByName = pranaColorCatalogRepository
                .findByHealerIdIsNullOrHealerId(healingCase.getHealerId())
                .stream()
                .collect(Collectors.toMap(
                        PranaColorCatalog::getName,
                        c -> c,
                        (a, b) -> a));

        Map<String, List<ColorUsedSnapshot>> energisingColors = new LinkedHashMap<>();
        Map<String, List<ColorUsedSnapshot>> cleansingColors = new LinkedHashMap<>();

        for (ChakraColorSelection selection : request.chakraSelections()) {
            energisingColors.put(selection.chakra(), toColorSnapshots(selection.energisingColors(), catalogByName));
            cleansingColors.put(selection.chakra(), toColorSnapshots(selection.cleansingColors(), catalogByName));
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
