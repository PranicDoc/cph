package com.pranicdoc.cph.healing.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pranicdoc.cph.common.enums.ColorUsageType;
import com.pranicdoc.cph.healing.dto.HealerProtocolUnderstandingRequest;
import com.pranicdoc.cph.healing.dto.HealerProtocolUnderstandingRequest.ChakraColorSelection;
import com.pranicdoc.cph.healing.dto.HealerProtocolUnderstandingRequest.ColorSelection;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.model.HealingDetails;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link HealerProtocolUnderstandingService#getProtocolSuggestions}: the
 * closure form's pre-fill of each scanned chakra's colors from the case's master-data protocols.
 */
@ExtendWith(MockitoExtension.class)
class HealerProtocolUnderstandingSuggestionsTest {

    @Mock private CaseRepository caseRepository;
    @Mock private HealerProtocolUnderstandingRepository hpuRepository;
    @Mock private PranaColorCatalogRepository pranaColorCatalogRepository;
    @Mock private SessionRepository sessionRepository;
    @Mock private ProtocolCatalogRepository protocolCatalogRepository;
    @Mock private ProtocolChakraRepository protocolChakraRepository;
    @Mock private ProtocolChakraColorRepository protocolChakraColorRepository;
    @Mock private ChakraCatalogRepository chakraCatalogRepository;

    private HealerProtocolUnderstandingService service;

    private final UUID caseId = UUID.randomUUID();
    private final UUID healerId = UUID.randomUUID();
    private final UUID otherHealerId = UUID.randomUUID();
    private final HealingCase healingCase = HealingCase.builder().build();

    private final ChakraCatalog solarPlexus = chakra("Solar Plexus Chakra");
    private final ChakraCatalog spleen = chakra("Spleen Chakra");
    private final PranaColorCatalog green = color("Light Whitish Green");
    private final PranaColorCatalog violet = color("Light Whitish Violet");
    private final PranaColorCatalog orange = color("Light Whitish Orange");

    @BeforeEach
    void setUp() {
        service = new HealerProtocolUnderstandingService(caseRepository, hpuRepository, pranaColorCatalogRepository,
                sessionRepository, protocolCatalogRepository, protocolChakraRepository, protocolChakraColorRepository,
                chakraCatalogRepository, new ObjectMapper());
        healingCase.setId(caseId);
        healingCase.setHealerId(healerId);
        lenient().when(caseRepository.findById(caseId)).thenReturn(Optional.of(healingCase));
        lenient().when(pranaColorCatalogRepository.findByHealerIdIsNullOrHealerId(healerId))
                .thenReturn(List.of(green, violet, orange));
        lenient().when(chakraCatalogRepository.findAllById(any()))
                .thenReturn(List.of(solarPlexus, spleen));
    }

    @Test
    void fillsScannedChakrasFromEveryNamedProtocol_matchingChakraNamesLoosely() {
        withProtocolNames("Fever, Cough");
        ProtocolCatalog fever = protocol("Fever");
        ProtocolCatalog cough = protocol("cough");
        when(protocolCatalogRepository.findByHealerIdIsNullOrHealerId(healerId))
                .thenReturn(List.of(fever, cough, protocol("Asthma")));
        ProtocolChakra feverSolar = link(fever, solarPlexus, null);
        ProtocolChakra coughSolar = link(cough, solarPlexus, healerId);
        when(protocolChakraRepository.findByProtocol_IdIn(anyList())).thenReturn(List.of(feverSolar, coughSolar));
        when(protocolChakraColorRepository.findByProtocolChakra_IdIn(anyList())).thenReturn(List.of(
                linkColor(feverSolar, green, ColorUsageType.CLEANSING),
                linkColor(feverSolar, violet, ColorUsageType.ENERGISING),
                linkColor(coughSolar, green, ColorUsageType.CLEANSING),
                linkColor(coughSolar, orange, ColorUsageType.CLEANSING)));
        // Scan names need not match the catalog's exactly.
        scanned("solar plexus", "Navel");

        Map<String, ChakraColorSelection> byChakra = suggestionsByChakra();

        assertEquals(2, byChakra.size());
        ChakraColorSelection solar = byChakra.get("solar plexus");
        assertEquals(List.of("Light Whitish Violet"), names(solar.energisingColors()));
        assertEquals(List.of("Light Whitish Green", "Light Whitish Orange"), names(solar.cleansingColors()));
        assertTrue(solar.cleansingColors().stream().allMatch(c -> c.properties().isEmpty()),
                "properties are left for the healer to pick");
        assertTrue(byChakra.get("Navel").energisingColors().isEmpty());
        assertTrue(byChakra.get("Navel").cleansingColors().isEmpty());
    }

    @Test
    void ignoresAnotherHealersLinksOnTheSameProtocol() {
        withProtocolNames("Fever");
        ProtocolCatalog fever = protocol("Fever");
        when(protocolCatalogRepository.findByHealerIdIsNullOrHealerId(healerId)).thenReturn(List.of(fever));
        ProtocolChakra theirs = link(fever, spleen, otherHealerId);
        when(protocolChakraRepository.findByProtocol_IdIn(anyList())).thenReturn(List.of(theirs));
        scanned("Spleen Chakra");

        ChakraColorSelection spleenRow = suggestionsByChakra().get("Spleen Chakra");

        assertTrue(spleenRow.energisingColors().isEmpty());
        assertTrue(spleenRow.cleansingColors().isEmpty());
        verifyNoInteractions(protocolChakraColorRepository);
    }

    @Test
    void caseWithoutProtocol_returnsScannedChakrasWithEmptyColors() {
        scanned("Spleen Chakra");

        ChakraColorSelection spleenRow = suggestionsByChakra().get("Spleen Chakra");

        assertTrue(spleenRow.energisingColors().isEmpty());
        assertTrue(spleenRow.cleansingColors().isEmpty());
        verifyNoInteractions(protocolCatalogRepository, protocolChakraRepository, protocolChakraColorRepository);
    }

    @Test
    void normalizeChakraName_ignoresCaseSpacingAndChakraSuffix() {
        assertEquals("solar plexus", HealerProtocolUnderstandingService.normalizeChakraName(" Solar  Plexus Chakra "));
        assertEquals("solar plexus", HealerProtocolUnderstandingService.normalizeChakraName("solar plexus"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Map<String, ChakraColorSelection> suggestionsByChakra() {
        HealerProtocolUnderstandingRequest result = service.getProtocolSuggestions(caseId);
        return result.chakraSelections().stream()
                .collect(Collectors.toMap(ChakraColorSelection::chakra, Function.identity()));
    }

    private void withProtocolNames(String csv) {
        HealingDetails details = new HealingDetails();
        details.setProtocolName(csv);
        healingCase.setHealingDetails(details);
    }

    private void scanned(String... chakras) {
        when(sessionRepository.findAllScannedChakras(caseId)).thenReturn(new LinkedHashSet<>(List.of(chakras)));
    }

    private static List<String> names(List<ColorSelection> colors) {
        return colors.stream().map(ColorSelection::color).toList();
    }

    private static ChakraCatalog chakra(String name) {
        ChakraCatalog c = new ChakraCatalog();
        c.setId(UUID.randomUUID());
        c.setName(name);
        return c;
    }

    private static PranaColorCatalog color(String name) {
        PranaColorCatalog c = new PranaColorCatalog();
        c.setId(UUID.randomUUID());
        c.setName(name);
        return c;
    }

    private static ProtocolCatalog protocol(String name) {
        ProtocolCatalog p = new ProtocolCatalog();
        p.setId(UUID.randomUUID());
        p.setName(name);
        return p;
    }

    private static ProtocolChakra link(ProtocolCatalog protocol, ChakraCatalog chakra, UUID healerId) {
        ProtocolChakra l = new ProtocolChakra();
        l.setId(UUID.randomUUID());
        l.setProtocol(protocol);
        l.setChakra(chakra);
        l.setHealerId(healerId);
        return l;
    }

    private static ProtocolChakraColor linkColor(ProtocolChakra link, PranaColorCatalog color, ColorUsageType usage) {
        ProtocolChakraColor c = new ProtocolChakraColor();
        c.setId(UUID.randomUUID());
        c.setProtocolChakra(link);
        c.setColor(color);
        c.setUsageType(usage);
        return c;
    }
}
