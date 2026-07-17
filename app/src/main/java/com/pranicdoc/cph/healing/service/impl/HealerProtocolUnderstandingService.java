package com.pranicdoc.cph.healing.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pranicdoc.common.dto.ChakraReadingSummarySnapshot;
import com.pranicdoc.common.dto.ColorUsedSnapshot;
import com.pranicdoc.cph.common.enums.ColorUsageType;
import com.pranicdoc.cph.common.enums.ScanType;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.healing.event.HealingSessionCompletedEvent;
import com.pranicdoc.cph.healing.model.*;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.healing.repository.HealerProtocolUnderstandingRepository;
import com.pranicdoc.cph.masterdata.model.ProtocolColorMapping;
import com.pranicdoc.cph.masterdata.repository.ProtocolColorMappingRepository;
import com.pranicdoc.cph.masterdata.repository.PranaColorCatalogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HealerProtocolUnderstandingService {

    private final CaseRepository caseRepository;
    private final HealerProtocolUnderstandingRepository hpuRepository;
    private final ProtocolColorMappingRepository protocolColorMappingRepository;
    private final PranaColorCatalogRepository pranaColorCatalogRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSessionCompleted(HealingSessionCompletedEvent event) {
        if (hpuRepository.findByHealingCaseId(event.getHealingCaseId()).isPresent()) {
            return;
        }

        HealingCase healingCase = caseRepository.findById(event.getHealingCaseId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Healing case not found for id %s"
                                .formatted(event.getHealingCaseId())));
        HealingDetails details = healingCase.getHealingDetails();
        String protocolName = details != null ? details.getProtocolName() : null;

        List<ChakraReadingSummarySnapshot> chakraReadings =
                healingCase.getSessions()
                        .stream()
                        .flatMap(session -> buildChakraReadings(session).stream())
                        .toList();


        List<ProtocolColorMapping> mappings = protocolName != null
                ? resolveColorMappings(protocolName, healingCase.getClinicId())
                : Collections.emptyList();

        if (mappings.isEmpty()) {
            log.error("No protocol color mapping found for {}", protocolName);
            return;
        }

        Map<String, List<ColorUsedSnapshot>> cleansingColors = buildColorsFromMappings(mappings, ColorUsageType.CLEANSING);;
        Map<String, List<ColorUsedSnapshot>> energisingColors = buildColorsFromMappings(mappings, ColorUsageType.ENERGISING);
        HealerProtocolUnderstanding hpu = new HealerProtocolUnderstanding();
        hpu.setHealingCaseId(healingCase.getId());
        hpu.setHealerId(healingCase.getHealerId());
        hpu.setCompletionDate(healingCase.getClosure().getClosureDate());
        hpu.setProtocolName(protocolName);
        hpu.setCleansingColorsJson(serialize(cleansingColors));
        hpu.setEnergisingColorsJson(serialize(energisingColors));
        hpu.setChakraReadingsJson(serialize(chakraReadings));

        hpuRepository.save(hpu);
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

    private List<ProtocolColorMapping> resolveColorMappings(String protocolName, UUID clinicId) {
        List<ProtocolColorMapping> clinic = protocolColorMappingRepository
                .findByProtocolNameAndClinicId(protocolName, clinicId);
        if (!clinic.isEmpty()) return clinic;
        return protocolColorMappingRepository.findByProtocolNameAndClinicIdIsNull(protocolName);
    }

    private Map<String, List<ColorUsedSnapshot>> buildColorsFromMappings(
            List<ProtocolColorMapping> mappings,
            ColorUsageType usageType) {

        return mappings.stream()
                .filter(Objects::nonNull)
                .filter(m -> m.getUsageType() == usageType)
                .collect(Collectors.groupingBy(
                        ProtocolColorMapping::getChakraName,
                        Collectors.mapping(
                                m -> pranaColorCatalogRepository.findById(m.getColorId())
                                        .map(c -> new ColorUsedSnapshot(
                                                c.getCode(),
                                                c.getName(),
                                                String.join(", ", c.getProperties())))
                                        .orElse(null),
                                Collectors.filtering(
                                        Objects::nonNull,
                                        Collectors.toList()
                                )
                        )
                ));
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
