package com.pranicdoc.cph.healing.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pranicdoc.cph.common.enums.ColorUsageType;
import com.pranicdoc.cph.healing.dto.HealerProtocolUnderstandingRequest;
import com.pranicdoc.cph.healing.dto.HealerProtocolUnderstandingRequest.ChakraColorSelection;
import com.pranicdoc.cph.healing.dto.HealerProtocolUnderstandingRequest.ColorSelection;
import com.pranicdoc.cph.healing.model.HealerProtocolUnderstanding;
import com.pranicdoc.cph.common.enums.ScanType;
import com.pranicdoc.cph.healing.model.ChakraReading;
import com.pranicdoc.cph.healing.model.ChakraScan;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.model.HealingSession;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.healing.repository.HealerProtocolUnderstandingRepository;
import com.pranicdoc.cph.healing.repository.SessionRepository;
import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
import com.pranicdoc.cph.masterdata.repository.ChakraCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.PranaColorCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolChakraColorRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolChakraRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A color name can exist once per category, so the closure's saved snapshot must carry the code of
 * the color in the column it was picked under, not whichever same-named row comes first.
 */
@ExtendWith(MockitoExtension.class)
class HealerProtocolUnderstandingSaveTest {

    @Mock private CaseRepository caseRepository;
    @Mock private HealerProtocolUnderstandingRepository hpuRepository;
    @Mock private PranaColorCatalogRepository pranaColorCatalogRepository;
    @Mock private SessionRepository sessionRepository;
    @Mock private ProtocolCatalogRepository protocolCatalogRepository;
    @Mock private ProtocolChakraRepository protocolChakraRepository;
    @Mock private ProtocolChakraColorRepository protocolChakraColorRepository;
    @Mock private ChakraCatalogRepository chakraCatalogRepository;

    @Test
    void eachSelectionSnapshotsTheColorOfItsOwnCategory() {
        UUID caseId = UUID.randomUUID();
        UUID healerId = UUID.randomUUID();
        HealingCase healingCase = HealingCase.builder().build();
        healingCase.setId(caseId);
        healingCase.setHealerId(healerId);
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(healingCase));
        when(hpuRepository.findByHealingCaseId(caseId)).thenReturn(Optional.empty());
        when(pranaColorCatalogRepository.findByHealerIdIsNullOrHealerId(healerId)).thenReturn(List.of(
                color("LWG_E", "Light Whitish Green", ColorUsageType.ENERGISING),
                color("LWG_C", "Light Whitish Green", ColorUsageType.CLEANSING)));
        HealerProtocolUnderstandingService service = new HealerProtocolUnderstandingService(
                caseRepository, hpuRepository, pranaColorCatalogRepository, sessionRepository, protocolCatalogRepository,
                protocolChakraRepository, protocolChakraColorRepository, chakraCatalogRepository, new ObjectMapper());

        service.saveFromHealerSelection(caseId, new HealerProtocolUnderstandingRequest(List.of(
                new ChakraColorSelection("Solar Plexus",
                        List.of(new ColorSelection("Light Whitish Green", List.of("Energizing"))),
                        List.of(new ColorSelection("Light Whitish Green", List.of("Cleansing"))),
                        null))));

        ArgumentCaptor<HealerProtocolUnderstanding> saved = ArgumentCaptor.forClass(HealerProtocolUnderstanding.class);
        verify(hpuRepository).save(saved.capture());
        assertTrue(saved.getValue().getEnergisingColorsJson().contains("LWG_E"), saved.getValue().getEnergisingColorsJson());
        assertTrue(saved.getValue().getCleansingColorsJson().contains("LWG_C"), saved.getValue().getCleansingColorsJson());
    }

    @Test
    void chakraReadingsKeepTheOrderTheyWereScanned() {
        UUID caseId = UUID.randomUUID();
        UUID healerId = UUID.randomUUID();
        List<ChakraReading> readings = new ArrayList<>();
        for (String name : List.of("Crown Chakra", "Liver", "Forehead Chakra", "Right Lungs", "Back Heart Chakra", "Basic Chakra")) {
            readings.add(ChakraReading.builder().chakraId(UUID.randomUUID()).chakraName(name).build());
        }
        HealingSession session = HealingSession.builder()
                .scans(new ArrayList<>(List.of(ChakraScan.builder().scanType(ScanType.PRE).readings(readings).build())))
                .build();
        HealingCase healingCase = HealingCase.builder().build();
        healingCase.setId(caseId);
        healingCase.setHealerId(healerId);
        healingCase.setSessions(new ArrayList<>(List.of(session)));
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(healingCase));
        when(hpuRepository.findByHealingCaseId(caseId)).thenReturn(Optional.empty());
        when(pranaColorCatalogRepository.findByHealerIdIsNullOrHealerId(healerId)).thenReturn(List.of());
        HealerProtocolUnderstandingService service = new HealerProtocolUnderstandingService(
                caseRepository, hpuRepository, pranaColorCatalogRepository, sessionRepository, protocolCatalogRepository,
                protocolChakraRepository, protocolChakraColorRepository, chakraCatalogRepository, new ObjectMapper());

        service.saveFromHealerSelection(caseId, new HealerProtocolUnderstandingRequest(List.of()));

        ArgumentCaptor<HealerProtocolUnderstanding> saved = ArgumentCaptor.forClass(HealerProtocolUnderstanding.class);
        verify(hpuRepository).save(saved.capture());
        String json = saved.getValue().getChakraReadingsJson();
        int last = -1;
        for (String name : List.of("Crown Chakra", "Liver", "Forehead Chakra", "Right Lungs", "Back Heart Chakra", "Basic Chakra")) {
            int at = json.indexOf(name);
            assertTrue(at > last, name + " out of order in " + json);
            last = at;
        }
    }

    @Test
    void chakraReasonsAreTrimmedAndOnlyNonBlankOnesStored_andComeBackOnReopen() {
        UUID caseId = UUID.randomUUID();
        UUID healerId = UUID.randomUUID();
        HealingCase healingCase = HealingCase.builder().build();
        healingCase.setId(caseId);
        healingCase.setHealerId(healerId);
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(healingCase));
        when(hpuRepository.findByHealingCaseId(caseId)).thenReturn(Optional.empty());
        when(pranaColorCatalogRepository.findByHealerIdIsNullOrHealerId(healerId)).thenReturn(List.of());
        HealerProtocolUnderstandingService service = new HealerProtocolUnderstandingService(
                caseRepository, hpuRepository, pranaColorCatalogRepository, sessionRepository, protocolCatalogRepository,
                protocolChakraRepository, protocolChakraColorRepository, chakraCatalogRepository, new ObjectMapper());

        service.saveFromHealerSelection(caseId, new HealerProtocolUnderstandingRequest(List.of(
                new ChakraColorSelection("Crown", List.of(), List.of(), "  Headache  "),
                new ChakraColorSelection("Heart", List.of(), List.of(), "   "),
                new ChakraColorSelection("Throat", List.of(), List.of(), null))));

        ArgumentCaptor<HealerProtocolUnderstanding> saved = ArgumentCaptor.forClass(HealerProtocolUnderstanding.class);
        verify(hpuRepository).save(saved.capture());
        assertEquals(Map.of("Crown", "Headache"), service.deserializeReasons(saved.getValue().getChakraReasonsJson()));

        when(hpuRepository.findByHealingCaseId(caseId)).thenReturn(Optional.of(saved.getValue()));
        List<ChakraColorSelection> reopened = service.getExistingSelection(caseId).chakraSelections();
        assertEquals(List.of("Headache", "null", "null"),
                reopened.stream().map(c -> String.valueOf(c.reason())).toList());
    }

    @Test
    void missingReasonsColumnReadsAsEmptyMap() {
        HealerProtocolUnderstandingService service = new HealerProtocolUnderstandingService(
                caseRepository, hpuRepository, pranaColorCatalogRepository, sessionRepository, protocolCatalogRepository,
                protocolChakraRepository, protocolChakraColorRepository, chakraCatalogRepository, new ObjectMapper());

        assertTrue(service.deserializeReasons(null).isEmpty());
    }

    private static PranaColorCatalog color(String code, String name, ColorUsageType category) {
        return PranaColorCatalog.builder().code(code).name(name).category(category).build();
    }
}
