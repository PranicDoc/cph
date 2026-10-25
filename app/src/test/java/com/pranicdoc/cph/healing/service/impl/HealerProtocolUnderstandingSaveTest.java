package com.pranicdoc.cph.healing.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pranicdoc.cph.common.enums.ColorUsageType;
import com.pranicdoc.cph.healing.dto.HealerProtocolUnderstandingRequest;
import com.pranicdoc.cph.healing.dto.HealerProtocolUnderstandingRequest.ChakraColorSelection;
import com.pranicdoc.cph.healing.dto.HealerProtocolUnderstandingRequest.ColorSelection;
import com.pranicdoc.cph.healing.model.HealerProtocolUnderstanding;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.healing.repository.HealerProtocolUnderstandingRepository;
import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
import com.pranicdoc.cph.masterdata.repository.PranaColorCatalogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
                caseRepository, hpuRepository, pranaColorCatalogRepository, new ObjectMapper());

        service.saveFromHealerSelection(caseId, new HealerProtocolUnderstandingRequest(List.of(
                new ChakraColorSelection("Solar Plexus",
                        List.of(new ColorSelection("Light Whitish Green", List.of("Energizing"))),
                        List.of(new ColorSelection("Light Whitish Green", List.of("Cleansing")))))));

        ArgumentCaptor<HealerProtocolUnderstanding> saved = ArgumentCaptor.forClass(HealerProtocolUnderstanding.class);
        verify(hpuRepository).save(saved.capture());
        assertTrue(saved.getValue().getEnergisingColorsJson().contains("LWG_E"), saved.getValue().getEnergisingColorsJson());
        assertTrue(saved.getValue().getCleansingColorsJson().contains("LWG_C"), saved.getValue().getCleansingColorsJson());
    }

    private static PranaColorCatalog color(String code, String name, ColorUsageType category) {
        return PranaColorCatalog.builder().code(code).name(name).category(category).build();
    }
}
