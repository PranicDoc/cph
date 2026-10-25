package com.pranicdoc.cph.masterdata.seed;

import com.pranicdoc.cph.common.enums.ColorUsageType;
import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
import com.pranicdoc.cph.masterdata.repository.ChakraCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.PranaColorCatalogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds system-level master data (chakras + prana colors) on startup.
 * Only runs if the catalogs are empty (idempotent).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MasterDataSeeder implements CommandLineRunner {

    private final ChakraCatalogRepository chakraRepo;
    private final PranaColorCatalogRepository colorRepo;

    @Override
    public void run(String... args) {
        seedChakras();
        seedPranaColors();
    }

    private void seedChakras() {
        if (chakraRepo.count() > 0) {
            log.info("Chakra catalog already seeded, skipping");
            return;
        }

        log.info("Seeding chakra catalog...");

        // --- 14 Major Chakras ---
        seedChakra("CROWN", "Crown Chakra", "major", "Top of the head");
        seedChakra("FOREHEAD", "Forehead Chakra", "major", "Center of forehead");
        seedChakra("AJNA", "Ajna Chakra", "major", "Between the eyebrows");
        seedChakra("THROAT", "Throat Chakra", "major", "Center of the throat");
        seedChakra("HEART_FRONT", "Front Heart Chakra", "major", "Center of chest");
        seedChakra("HEART_BACK", "Back Heart Chakra", "major", "Back of the heart");
        seedChakra("SOLAR_PLEXUS_FRONT", "Front Solar Plexus Chakra", "major", "Solar plexus area");
        seedChakra("SOLAR_PLEXUS_BACK", "Back Solar Plexus Chakra", "major", "Back of solar plexus");
        seedChakra("NAVEL", "Navel Chakra", "major", "Navel area");
        seedChakra("MENG_MEIN", "Meng Mein Chakra", "major", "Back of the navel");
        seedChakra("SEX", "Sex Chakra", "major", "Pubic area");
        seedChakra("BASIC", "Basic Chakra", "major", "Base of the spine");
        seedChakra("SPLEEN_FRONT", "Front Spleen Chakra", "major", "Left part of abdomen");
        seedChakra("SPLEEN_BACK", "Back Spleen Chakra", "major", "Back of the spleen");

        // --- 11 Minor Chakras ---
        seedChakra("JAW_LEFT", "Left Jaw Minor Chakra", "minor", "Left jaw");
        seedChakra("JAW_RIGHT", "Right Jaw Minor Chakra", "minor", "Right jaw");
        seedChakra("SHOULDER_LEFT", "Left Shoulder Minor Chakra", "minor", "Left shoulder");
        seedChakra("SHOULDER_RIGHT", "Right Shoulder Minor Chakra", "minor", "Right shoulder");
        seedChakra("ELBOW_LEFT", "Left Elbow Minor Chakra", "minor", "Left elbow");
        seedChakra("ELBOW_RIGHT", "Right Elbow Minor Chakra", "minor", "Right elbow");
        seedChakra("HAND_LEFT", "Left Hand Chakra", "minor", "Center of left palm");
        seedChakra("HAND_RIGHT", "Right Hand Chakra", "minor", "Center of right palm");
        seedChakra("HIP_LEFT", "Left Hip Minor Chakra", "minor", "Left hip");
        seedChakra("HIP_RIGHT", "Right Hip Minor Chakra", "minor", "Right hip");
        seedChakra("KNEE_LEFT", "Left Knee Minor Chakra", "minor", "Left knee");
        seedChakra("KNEE_RIGHT", "Right Knee Minor Chakra", "minor", "Right knee");
        seedChakra("SOLE_LEFT", "Left Sole Chakra", "minor", "Sole of left foot");
        seedChakra("SOLE_RIGHT", "Right Sole Chakra", "minor", "Sole of right foot");

        log.info("Seeded {} chakras", chakraRepo.count());
    }

    private void seedPranaColors() {
        if (colorRepo.count() > 0) {
            log.info("Prana color catalog already seeded, skipping");
            return;
        }

        log.info("Seeding prana color catalog...");

        seedColor("WHITE", "White Prana", "#FFFFFF", "General cleansing", "energizing");
        seedColor("RED", "Red Prana", "#FF0000", "Strengthening", "warming", "energizing");
        seedColor("ORANGE", "Orange Prana", "#FFA500", "Expelling", "eliminating", "decongesting");
        seedColor("YELLOW", "Yellow Prana", "#FFFF00", "Cohesion", "cementing", "assimilating");
        seedColor("GREEN", "Green Prana", "#00FF00", "Decongesting", "cleansing", "disinfecting");
        seedColor("BLUE", "Blue Prana", "#0000FF", "Inhibiting", "cooling", "disinfecting");
        seedColor("VIOLET", "Violet Prana", "#EE82EE", "All-purpose", "regenerating");
        seedColor("ELECTRIC_VIOLET", "Electric Violet", "#7F00FF", "Powerful regeneration", "spirituality");
        seedColor("GOLD", "Golden Prana", "#FFD700", "Higher-level healing", "flushing");
        seedColor("LIGHT_WHITISH_GREEN", "Light Whitish Green", "#E0FFE0", "Mild cleansing");
        seedColor("LIGHT_WHITISH_BLUE", "Light Whitish Blue", "#E0E0FF", "Mild inhibiting", "soothing");
        seedColor("LIGHT_WHITISH_VIOLET", "Light Whitish Violet", "#F0E0FF", "Mild regeneration");

        log.info("Seeded {} prana colors", colorRepo.count());
    }

    private void seedChakra(String code, String name, String category, String location) {
        ChakraCatalog chakra = ChakraCatalog.builder()
                .code(code)
                .name(name)
                .category(category)
                .location(location)
                .healerId(null) // System-level
                .build();
        chakraRepo.save(chakra);
    }

    private void seedColor(String code, String name, String hex, String... properties) {
        PranaColorCatalog color = PranaColorCatalog.builder()
                .code(code)
                .name(name)
                .colorHex(hex)
                .properties(List.of(properties))
                // Matches the one-time backfill of pre-category colors; SUPADM recategorises from the UI.
                .category(ColorUsageType.ENERGISING)
                .healerId(null) // System-level
                .build();
        colorRepo.save(color);
    }
}
