package com.pranicdoc.cph.masterdata;

import com.pranicdoc.cph.common.enums.ColorUsageType;
import com.pranicdoc.cph.common.enums.ProtocolType;
import com.pranicdoc.cph.masterdata.dto.ProtocolChakraColorView;
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
import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct JPA-level coverage for the {@link ProtocolChakra} / {@link
 * ProtocolChakraColor} many-to-many + usage-typed relationship, following
 * {@code TenantFilterIntegrationTest}'s pattern (real {@link EntityManager}
 * under a full {@code @SpringBootTest} context, {@code @Transactional}
 * per-test rollback) since this repo has no {@code @DataJpaTest} precedent.
 *
 * <p>Covers: multiple chakras per protocol, multiple protocols sharing a
 * chakra, duplicate-link prevention on both junction tables, filtering
 * chakras by protocol ids (step 2 of the read flow), filtering colors by
 * protocol+chakra ids while distinguishing CLEANSING/ENERGISING (step 3),
 * and the same color appearing under both usage types for one combination.
 *
 * <p>Uses {@code @DataJpaTest} rather than {@code @SpringBootTest}: this
 * repo's full {@code @SpringBootTest} context fails to boot in this
 * environment (an unconditional {@code RedisConnectionFactory} dependency
 * outside Spring Boot's autoconfiguration-exclusion reach — reproduced by
 * the pre-existing, unmodified {@code TenantFilterIntegrationTest}, so it
 * predates and is unrelated to this change). {@code @DataJpaTest} only
 * autoconfigures the JPA/DataSource slice, which is all this test needs,
 * and rolls each test back automatically (no explicit {@code @Transactional}
 * needed).
 */
@DataJpaTest
@ActiveProfiles("test")
class ProtocolChakraColorRelationshipIntegrationTest {

    @Autowired private EntityManager entityManager;
    @Autowired private ProtocolCatalogRepository protocolRepo;
    @Autowired private ChakraCatalogRepository chakraRepo;
    @Autowired private PranaColorCatalogRepository colorRepo;
    @Autowired private ProtocolChakraRepository protocolChakraRepo;
    @Autowired private ProtocolChakraColorRepository protocolChakraColorRepo;

    // JpaConfig's @EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl") is picked up by the
    // @DataJpaTest slice, and AuditorAwareImpl needs these two beans — mocked here rather than
    // provided for real, matching MasterDataControllerAuthorizationTest's precedent for the same pair.
    @MockBean private HealerCacheService healerCacheService;
    @MockBean private SecurityContextUtil securityContextUtil;

    private ProtocolCatalog newProtocol(String name, int page) {
        ProtocolCatalog p = new ProtocolCatalog();
        p.setName(name);
        p.setSourceBook("Test Book");
        p.setPageNumber(page);
        p.setProtocolType(ProtocolType.PROTOCOL);
        return protocolRepo.save(p);
    }

    private ChakraCatalog newChakra(String code, String name) {
        ChakraCatalog c = new ChakraCatalog();
        c.setCode(code);
        c.setName(name);
        return chakraRepo.save(c);
    }

    private PranaColorCatalog newColor(String code, String name) {
        PranaColorCatalog c = new PranaColorCatalog();
        c.setCode(code);
        c.setName(name);
        return colorRepo.save(c);
    }

    private ProtocolChakra link(ProtocolCatalog protocol, ChakraCatalog chakra) {
        return protocolChakraRepo.save(ProtocolChakra.builder().protocol(protocol).chakra(chakra).build());
    }

    private ProtocolChakraColor linkColor(ProtocolChakra pc, PranaColorCatalog color, ColorUsageType type) {
        return protocolChakraColorRepo.save(
                ProtocolChakraColor.builder().protocolChakra(pc).color(color).usageType(type).build());
    }

    // ── Protocol <-> Chakra ─────────────────────────────────────────────────

    @Test
    @Transactional
    void oneProtocolCanHaveMultipleChakras() {
        ProtocolCatalog fever = newProtocol("Fever", 1);
        ChakraCatalog solarPlexus = newChakra("SOLAR_PLEXUS_T", "Solar Plexus Chakra");
        ChakraCatalog spleen = newChakra("SPLEEN_T", "Spleen Chakra");

        link(fever, solarPlexus);
        link(fever, spleen);
        entityManager.flush();
        entityManager.clear();

        List<ChakraCatalog> chakras = protocolChakraRepo.findDistinctChakrasByProtocolIdsForHealer(List.of(fever.getId()), null);
        assertEquals(2, chakras.size());
        assertTrue(chakras.stream().anyMatch(c -> c.getId().equals(solarPlexus.getId())));
        assertTrue(chakras.stream().anyMatch(c -> c.getId().equals(spleen.getId())));
    }

    @Test
    @Transactional
    void multipleProtocolsCanShareTheSameChakra() {
        ProtocolCatalog fever = newProtocol("Fever", 2);
        ProtocolCatalog headache = newProtocol("Headache", 3);
        ChakraCatalog crown = newChakra("CROWN_T", "Crown Chakra");

        link(fever, crown);
        link(headache, crown);
        entityManager.flush();
        entityManager.clear();

        List<ChakraCatalog> forFever = protocolChakraRepo.findDistinctChakrasByProtocolIdsForHealer(List.of(fever.getId()), null);
        List<ChakraCatalog> forBoth = protocolChakraRepo.findDistinctChakrasByProtocolIdsForHealer(
                List.of(fever.getId(), headache.getId()), null);

        assertEquals(1, forFever.size());
        assertEquals(crown.getId(), forFever.get(0).getId());
        // Selecting both protocols must not duplicate the shared chakra in the result.
        assertEquals(1, forBoth.size());
        assertEquals(crown.getId(), forBoth.get(0).getId());
    }

    @Test
    @Transactional
    void duplicateProtocolChakraLinkForSameHealerIsRejected() {
        ProtocolCatalog fever = newProtocol("Fever", 4);
        ChakraCatalog crown = newChakra("CROWN_DUP_T", "Crown Chakra Dup");
        UUID healerId = UUID.randomUUID();
        protocolChakraRepo.save(ProtocolChakra.builder().protocol(fever).chakra(crown).healerId(healerId).build());
        entityManager.flush();

        ProtocolChakra duplicate = ProtocolChakra.builder().protocol(fever).chakra(crown).healerId(healerId).build();
        assertThrows(DataIntegrityViolationException.class,
                () -> protocolChakraRepo.saveAndFlush(duplicate));
    }

    /**
     * V38: the unique constraint became (protocol_id, chakra_id, healer_id) so each healer can
     * independently add their own link for a (protocol, chakra) pair already covered by a system
     * row. Postgres/H2 both treat NULL as distinct for uniqueness purposes, so two system
     * (healer_id null) rows for the same pair are NOT rejected by this constraint — exactly the
     * same accepted trade-off protocol_color_mapping's healer-keyed UNIQUE already makes (V19).
     * De-duplicating the system row is the import path's find-or-create responsibility, not this
     * constraint's — this test documents that this is expected, not a regression.
     */
    @Test
    @Transactional
    void twoSystemRowsForSameProtocolChakraPairAreNotRejectedByTheConstraint() {
        ProtocolCatalog fever = newProtocol("Fever", 4);
        ChakraCatalog crown = newChakra("CROWN_SYS_DUP_T", "Crown Chakra Sys Dup");
        link(fever, crown);
        entityManager.flush();

        ProtocolChakra secondSystemRow = ProtocolChakra.builder().protocol(fever).chakra(crown).build();
        assertDoesNotThrow(() -> protocolChakraRepo.saveAndFlush(secondSystemRow));
    }

    @Test
    @Transactional
    void healerCanAddTheirOwnLinkAlongsideAnExistingSystemLink() {
        ProtocolCatalog fever = newProtocol("Fever", 4);
        ChakraCatalog crown = newChakra("CROWN_OWN_T", "Crown Chakra Own");
        link(fever, crown); // system row, healerId null
        entityManager.flush();

        ProtocolChakra ownRow = ProtocolChakra.builder()
                .protocol(fever).chakra(crown).healerId(UUID.randomUUID()).build();
        assertDoesNotThrow(() -> protocolChakraRepo.saveAndFlush(ownRow));
    }

    @Test
    @Transactional
    void filteringChakrasByProtocolIds_returnsOnlyLinkedChakras() {
        ProtocolCatalog fever = newProtocol("Fever", 5);
        ProtocolCatalog unrelated = newProtocol("Unrelated Protocol", 6);
        ChakraCatalog crown = newChakra("CROWN_FILT_T", "Crown Chakra Filt");
        ChakraCatalog unrelatedChakra = newChakra("UNRELATED_T", "Unrelated Chakra");

        link(fever, crown);
        link(unrelated, unrelatedChakra);
        entityManager.flush();
        entityManager.clear();

        List<ChakraCatalog> chakras = protocolChakraRepo.findDistinctChakrasByProtocolIdsForHealer(List.of(fever.getId()), null);
        assertEquals(1, chakras.size());
        assertEquals(crown.getId(), chakras.get(0).getId());
    }

    // ── Protocol + Chakra <-> Color ─────────────────────────────────────────

    @Test
    @Transactional
    void oneChakraCanHaveMultipleCleansingColors() {
        ProtocolCatalog fever = newProtocol("Fever", 7);
        ChakraCatalog solarPlexus = newChakra("SOLAR_PLEXUS_CL_T", "Solar Plexus Chakra Cl");
        ProtocolChakra pc = link(fever, solarPlexus);
        PranaColorCatalog green = newColor("LWG_T1", "Light Whitish Green T1");
        PranaColorCatalog blue = newColor("LWB_T1", "Light Whitish Blue T1");

        linkColor(pc, green, ColorUsageType.CLEANSING);
        linkColor(pc, blue, ColorUsageType.CLEANSING);
        entityManager.flush();
        entityManager.clear();

        List<ProtocolChakraColorView> views = protocolChakraColorRepo.findColorsForProtocolsAndChakrasForHealer(
                List.of(fever.getId()), List.of(solarPlexus.getId()), null);
        assertEquals(2, views.size());
        assertTrue(views.stream().allMatch(v -> v.usageType() == ColorUsageType.CLEANSING));
    }

    @Test
    @Transactional
    void oneChakraCanHaveMultipleEnergizingColors() {
        ProtocolCatalog fever = newProtocol("Fever", 8);
        ChakraCatalog solarPlexus = newChakra("SOLAR_PLEXUS_EN_T", "Solar Plexus Chakra En");
        ProtocolChakra pc = link(fever, solarPlexus);
        PranaColorCatalog green = newColor("LWG_T2", "Light Whitish Green T2");
        PranaColorCatalog blueColor = newColor("LWB_T2", "Light Whitish Blue T2");
        PranaColorCatalog orange = newColor("LWO_T2", "Light Whitish Orange T2");

        linkColor(pc, green, ColorUsageType.ENERGISING);
        linkColor(pc, blueColor, ColorUsageType.ENERGISING);
        linkColor(pc, orange, ColorUsageType.ENERGISING);
        entityManager.flush();
        entityManager.clear();

        List<ProtocolChakraColorView> views = protocolChakraColorRepo.findColorsForProtocolsAndChakrasForHealer(
                List.of(fever.getId()), List.of(solarPlexus.getId()), null);
        assertEquals(3, views.size());
        assertTrue(views.stream().allMatch(v -> v.usageType() == ColorUsageType.ENERGISING));
    }

    @Test
    @Transactional
    void sameColorCanAppearInBothCleansingAndEnergizing() {
        ProtocolCatalog fever = newProtocol("Fever", 9);
        ChakraCatalog heartBack = newChakra("HEART_BACK_T", "Back Heart Chakra T");
        ProtocolChakra pc = link(fever, heartBack);
        PranaColorCatalog green = newColor("LWG_T3", "Light Whitish Green T3");

        linkColor(pc, green, ColorUsageType.CLEANSING);
        linkColor(pc, green, ColorUsageType.ENERGISING);
        entityManager.flush();
        entityManager.clear();

        List<ProtocolChakraColorView> views = protocolChakraColorRepo.findColorsForProtocolsAndChakrasForHealer(
                List.of(fever.getId()), List.of(heartBack.getId()), null);
        assertEquals(2, views.size());
        assertTrue(views.stream().anyMatch(v -> v.usageType() == ColorUsageType.CLEANSING && v.colorId().equals(green.getId())));
        assertTrue(views.stream().anyMatch(v -> v.usageType() == ColorUsageType.ENERGISING && v.colorId().equals(green.getId())));
    }

    @Test
    @Transactional
    void differentColorsForDifferentProtocolChakraCombinations() {
        ProtocolCatalog fever = newProtocol("Fever", 10);
        ChakraCatalog solarPlexus = newChakra("SOLAR_PLEXUS_DIFF_T", "Solar Plexus Chakra Diff");
        ChakraCatalog spleen = newChakra("SPLEEN_DIFF_T", "Spleen Chakra Diff");
        ProtocolChakra spLink = link(fever, solarPlexus);
        ProtocolChakra spleenLink = link(fever, spleen);
        PranaColorCatalog green = newColor("LWG_T4", "Light Whitish Green T4");
        PranaColorCatalog violet = newColor("LWV_T4", "Light Whitish Violet T4");

        linkColor(spLink, green, ColorUsageType.CLEANSING);
        linkColor(spleenLink, violet, ColorUsageType.CLEANSING);
        entityManager.flush();
        entityManager.clear();

        List<ProtocolChakraColorView> solarViews = protocolChakraColorRepo.findColorsForProtocolsAndChakrasForHealer(
                List.of(fever.getId()), List.of(solarPlexus.getId()), null);
        List<ProtocolChakraColorView> spleenViews = protocolChakraColorRepo.findColorsForProtocolsAndChakrasForHealer(
                List.of(fever.getId()), List.of(spleen.getId()), null);

        assertEquals(1, solarViews.size());
        assertEquals(green.getId(), solarViews.get(0).colorId());
        assertEquals(1, spleenViews.size());
        assertEquals(violet.getId(), spleenViews.get(0).colorId());
    }

    @Test
    @Transactional
    void filteringColorsByProtocolAndChakra_excludesOtherChakrasOnSameProtocol() {
        ProtocolCatalog fever = newProtocol("Fever", 11);
        ChakraCatalog solarPlexus = newChakra("SOLAR_PLEXUS_FILT_T", "Solar Plexus Chakra Filt");
        ChakraCatalog spleen = newChakra("SPLEEN_FILT_T", "Spleen Chakra Filt");
        ProtocolChakra spLink = link(fever, solarPlexus);
        ProtocolChakra spleenLink = link(fever, spleen);
        PranaColorCatalog green = newColor("LWG_T5", "Light Whitish Green T5");
        PranaColorCatalog violet = newColor("LWV_T5", "Light Whitish Violet T5");

        linkColor(spLink, green, ColorUsageType.CLEANSING);
        linkColor(spleenLink, violet, ColorUsageType.CLEANSING);
        entityManager.flush();
        entityManager.clear();

        List<ProtocolChakraColorView> views = protocolChakraColorRepo.findColorsForProtocolsAndChakrasForHealer(
                List.of(fever.getId()), List.of(solarPlexus.getId()), null);
        assertEquals(1, views.size());
        assertEquals(green.getId(), views.get(0).colorId());
    }

    @Test
    @Transactional
    void duplicateProtocolChakraColorLinkIsRejected() {
        ProtocolCatalog fever = newProtocol("Fever", 12);
        ChakraCatalog crown = newChakra("CROWN_COLOR_DUP_T", "Crown Chakra Color Dup");
        ProtocolChakra pc = link(fever, crown);
        PranaColorCatalog green = newColor("LWG_DUP_T", "Light Whitish Green Dup");
        linkColor(pc, green, ColorUsageType.CLEANSING);
        entityManager.flush();

        ProtocolChakraColor duplicate = ProtocolChakraColor.builder()
                .protocolChakra(pc).color(green).usageType(ColorUsageType.CLEANSING).build();
        assertThrows(DataIntegrityViolationException.class,
                () -> protocolChakraColorRepo.saveAndFlush(duplicate));
    }

    @Test
    @Transactional
    void unknownProtocolIdReturnsNoChakras() {
        List<ChakraCatalog> chakras = protocolChakraRepo.findDistinctChakrasByProtocolIdsForHealer(List.of(UUID.randomUUID()), null);
        assertTrue(chakras.isEmpty());
    }
}
