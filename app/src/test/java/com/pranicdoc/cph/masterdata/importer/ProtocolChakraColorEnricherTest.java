package com.pranicdoc.cph.masterdata.importer;

import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolChakraColorUsage;
import com.pranicdoc.cph.masterdata.repository.ChakraCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.PranaColorCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolChakraColorUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Direct unit coverage for {@link ProtocolChakraColorEnricher} — the
 * chakra/color resolve-or-backfill and {@link ProtocolChakraColorUsage}
 * upsert logic behind MDIMPORT-22's chakra/color enrichment path.
 */
@ExtendWith(MockitoExtension.class)
class ProtocolChakraColorEnricherTest {

    @Mock private ChakraCatalogRepository chakraRepo;
    @Mock private PranaColorCatalogRepository pranaColorRepo;
    @Mock private ProtocolChakraColorUsageRepository usageRepo;

    @InjectMocks private ProtocolChakraColorEnricher enricher;

    private static final UUID CHAKRA_ID = UUID.randomUUID();
    private static final UUID CLEANSING_COLOR_ID = UUID.randomUUID();
    private static final UUID ENERGIZING_COLOR_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(usageRepo.save(any(ProtocolChakraColorUsage.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(chakraRepo.save(any(ChakraCatalog.class)))
                .thenAnswer(inv -> {
                    ChakraCatalog c = inv.getArgument(0);
                    if (c.getId() == null) c.setId(UUID.randomUUID());
                    return c;
                });
        lenient().when(pranaColorRepo.save(any(PranaColorCatalog.class)))
                .thenAnswer(inv -> {
                    PranaColorCatalog c = inv.getArgument(0);
                    if (c.getId() == null) c.setId(UUID.randomUUID());
                    return c;
                });
        lenient().when(usageRepo.findByProtocolNameAndHealerIdIsNull(any())).thenReturn(List.of());
        lenient().when(usageRepo.findByProtocolNameAndHealerId(any(), any())).thenReturn(List.of());
    }

    @Test
    void enrich_backfillsChakraAndColorsWhenNoneExist_createsNewUsageRow() {
        when(chakraRepo.findByNameIgnoreCase("Crown Chakra")).thenReturn(Optional.empty());
        when(pranaColorRepo.findByNameIgnoreCase("Light Whitish Green")).thenReturn(Optional.empty());
        when(pranaColorRepo.findByNameIgnoreCase("Light Whitish Blue")).thenReturn(Optional.empty());

        ProtocolChakraColorEnricher.EnrichmentResult result = enricher.enrich(
                "Headache", "Crown Chakra", List.of("Light Whitish Green"), List.of("Light Whitish Blue"),
                true, null, "Row 5");

        assertTrue(result.created());
        assertFalse(result.skipped());

        ArgumentCaptor<ChakraCatalog> chakraCaptor = ArgumentCaptor.forClass(ChakraCatalog.class);
        verify(chakraRepo).save(chakraCaptor.capture());
        assertEquals("Crown Chakra", chakraCaptor.getValue().getName());
        assertEquals("CROWN_CHAKRA", chakraCaptor.getValue().getCode());
        assertNull(chakraCaptor.getValue().getHealerId()); // SUPADM-created -> SYSTEM row

        ArgumentCaptor<PranaColorCatalog> colorCaptor = ArgumentCaptor.forClass(PranaColorCatalog.class);
        verify(pranaColorRepo, times(2)).save(colorCaptor.capture());
        List<String> savedNames = colorCaptor.getAllValues().stream().map(PranaColorCatalog::getName).toList();
        assertTrue(savedNames.contains("Light Whitish Green"));
        assertTrue(savedNames.contains("Light Whitish Blue"));

        verify(usageRepo).save(argThat(u ->
                u.getProtocolName().equals("Headache") && u.getCleansingColorIds().size() == 1
                        && u.getEnergizingColorIds().size() == 1));
    }

    @Test
    void enrich_reusesExistingChakraAndColorByName_noDuplicateCreated() {
        ChakraCatalog existingChakra = new ChakraCatalog();
        existingChakra.setId(CHAKRA_ID);
        existingChakra.setCode("CROWN");
        existingChakra.setName("Crown Chakra");
        when(chakraRepo.findByNameIgnoreCase("Crown Chakra")).thenReturn(Optional.of(existingChakra));

        PranaColorCatalog existingColor = new PranaColorCatalog();
        existingColor.setId(CLEANSING_COLOR_ID);
        existingColor.setCode("GREEN");
        existingColor.setName("Green Prana");
        when(pranaColorRepo.findByNameIgnoreCase("Green Prana")).thenReturn(Optional.of(existingColor));

        enricher.enrich("Headache", "Crown Chakra", List.of("Green Prana"), List.of(), true, null, "Row 1");

        verify(chakraRepo, never()).save(any());
        verify(pranaColorRepo, never()).save(any());
        verify(usageRepo).save(argThat(u -> u.getChakraId().equals(CHAKRA_ID)
                && u.getCleansingColorIds().equals(List.of(CLEANSING_COLOR_ID))));
    }

    @Test
    void enrich_reimport_replacesColorListsRatherThanAppending() {
        ChakraCatalog existingChakra = new ChakraCatalog();
        existingChakra.setId(CHAKRA_ID);
        existingChakra.setName("Crown Chakra");
        when(chakraRepo.findByNameIgnoreCase("Crown Chakra")).thenReturn(Optional.of(existingChakra));

        PranaColorCatalog newColor = new PranaColorCatalog();
        newColor.setId(ENERGIZING_COLOR_ID);
        newColor.setName("Light Whitish Red");
        when(pranaColorRepo.findByNameIgnoreCase("Light Whitish Red")).thenReturn(Optional.of(newColor));

        ProtocolChakraColorUsage existingUsage = new ProtocolChakraColorUsage();
        existingUsage.setProtocolName("Headache");
        existingUsage.setChakraId(CHAKRA_ID);
        existingUsage.setHealerId(null);
        existingUsage.setCleansingColorIds(List.of(CLEANSING_COLOR_ID)); // old data, different color
        when(usageRepo.findByProtocolNameAndHealerIdIsNull("Headache")).thenReturn(List.of(existingUsage));

        ProtocolChakraColorEnricher.EnrichmentResult result = enricher.enrich(
                "Headache", "Crown Chakra", List.of(), List.of("Light Whitish Red"), true, null, "Row 1");

        assertTrue(result.updated());
        verify(usageRepo).save(argThat(u ->
                u.getCleansingColorIds().isEmpty() // replaced, old color gone
                        && u.getEnergizingColorIds().equals(List.of(ENERGIZING_COLOR_ID))));
    }

    @Test
    void enrich_nonSuperAdmin_cannotOverwriteSystemUsageRow() {
        ChakraCatalog existingChakra = new ChakraCatalog();
        existingChakra.setId(CHAKRA_ID);
        existingChakra.setName("Crown Chakra");
        when(chakraRepo.findByNameIgnoreCase("Crown Chakra")).thenReturn(Optional.of(existingChakra));

        ProtocolChakraColorUsage existingUsage = new ProtocolChakraColorUsage();
        existingUsage.setProtocolName("Headache");
        existingUsage.setChakraId(CHAKRA_ID);
        existingUsage.setHealerId(null); // SYSTEM row
        when(usageRepo.findByProtocolNameAndHealerIdIsNull("Headache")).thenReturn(List.of(existingUsage));

        ProtocolChakraColorEnricher.EnrichmentResult result = enricher.enrich(
                "Headache", "Crown Chakra", List.of(), List.of(), false, UUID.randomUUID(), "Row 1");

        assertTrue(result.skipped());
        assertTrue(result.error().contains("owned by another party"));
        verify(usageRepo, never()).save(any());
    }

    @Test
    void enrich_nonSuperAdmin_cannotOverwriteAnotherHealersUsageRow() {
        UUID otherHealerId = UUID.randomUUID();
        ChakraCatalog existingChakra = new ChakraCatalog();
        existingChakra.setId(CHAKRA_ID);
        existingChakra.setName("Crown Chakra");
        when(chakraRepo.findByNameIgnoreCase("Crown Chakra")).thenReturn(Optional.of(existingChakra));

        ProtocolChakraColorUsage existingUsage = new ProtocolChakraColorUsage();
        existingUsage.setProtocolName("Headache");
        existingUsage.setChakraId(CHAKRA_ID);
        existingUsage.setHealerId(otherHealerId); // owned by a DIFFERENT healer
        when(usageRepo.findByProtocolNameAndHealerId(eq("Headache"), any())).thenReturn(List.of(existingUsage));

        ProtocolChakraColorEnricher.EnrichmentResult result = enricher.enrich(
                "Headache", "Crown Chakra", List.of(), List.of(), false, UUID.randomUUID(), "Row 1");

        assertTrue(result.skipped());
        assertTrue(result.error().contains("owned by another party"));
        verify(usageRepo, never()).save(any());
    }

    @Test
    void enrich_superAdmin_canOverwriteAnotherHealersUsageRow() {
        ChakraCatalog existingChakra = new ChakraCatalog();
        existingChakra.setId(CHAKRA_ID);
        existingChakra.setName("Crown Chakra");
        when(chakraRepo.findByNameIgnoreCase("Crown Chakra")).thenReturn(Optional.of(existingChakra));

        ProtocolChakraColorUsage existingUsage = new ProtocolChakraColorUsage();
        existingUsage.setProtocolName("Headache");
        existingUsage.setChakraId(CHAKRA_ID);
        existingUsage.setHealerId(UUID.randomUUID());
        when(usageRepo.findByProtocolNameAndHealerIdIsNull("Headache")).thenReturn(List.of(existingUsage));

        ProtocolChakraColorEnricher.EnrichmentResult result = enricher.enrich(
                "Headache", "Crown Chakra", List.of(), List.of(), true, null, "Row 1");

        assertTrue(result.updated());
        verify(usageRepo).save(existingUsage);
    }

    @Test
    void enrich_ownHealerRow_canUpdateOwnRow() {
        UUID callerHealerId = UUID.randomUUID();
        ChakraCatalog existingChakra = new ChakraCatalog();
        existingChakra.setId(CHAKRA_ID);
        existingChakra.setName("Crown Chakra");
        when(chakraRepo.findByNameIgnoreCase("Crown Chakra")).thenReturn(Optional.of(existingChakra));

        ProtocolChakraColorUsage existingUsage = new ProtocolChakraColorUsage();
        existingUsage.setProtocolName("Headache");
        existingUsage.setChakraId(CHAKRA_ID);
        existingUsage.setHealerId(callerHealerId);
        when(usageRepo.findByProtocolNameAndHealerId("Headache", callerHealerId)).thenReturn(List.of(existingUsage));

        ProtocolChakraColorEnricher.EnrichmentResult result = enricher.enrich(
                "Headache", "Crown Chakra", List.of(), List.of(), false, callerHealerId, "Row 1");

        assertTrue(result.updated());
        verify(usageRepo).save(existingUsage);
    }

    @Test
    void enrich_missingChakraName_skipsWithError() {
        ProtocolChakraColorEnricher.EnrichmentResult result = enricher.enrich(
                "Headache", null, List.of("Green"), List.of(), true, null, "Row 3");

        assertTrue(result.skipped());
        assertTrue(result.error().contains("missing chakra name"));
        verifyNoInteractions(usageRepo);
    }

    @Test
    void enrich_slugifiesPhraseWithPunctuationIntoUpperSnakeCase() {
        when(pranaColorRepo.findByNameIgnoreCase("Whitish Orange-Yellow")).thenReturn(Optional.empty());
        when(chakraRepo.findByNameIgnoreCase("Solar Plexus Chakra")).thenReturn(Optional.empty());

        enricher.enrich("Fever", "Solar Plexus Chakra", List.of("Whitish Orange-Yellow"), List.of(), true, null, "Row 1");

        verify(pranaColorRepo).save(argThat(c -> c.getCode().equals("WHITISH_ORANGE_YELLOW")));
        verify(chakraRepo).save(argThat(c -> c.getCode().equals("SOLAR_PLEXUS_CHAKRA")));
    }
}
