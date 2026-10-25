package com.pranicdoc.cph.masterdata.seed;

import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import com.pranicdoc.cph.masterdata.repository.ChakraCatalogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds the system-level chakra catalog on startup, only when it is empty (idempotent).
 * Prana colors are no longer seeded: production color data is final and maintained from
 * Master Data -> Prana Colors (and the protocol import).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MasterDataSeeder implements CommandLineRunner {

    private final ChakraCatalogRepository chakraRepo;

    @Override
    public void run(String... args) {
        seedChakras();
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
}
