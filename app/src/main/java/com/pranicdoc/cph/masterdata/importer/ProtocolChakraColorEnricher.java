package com.pranicdoc.cph.masterdata.importer;

import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolChakraColorUsage;
import com.pranicdoc.cph.masterdata.repository.ChakraCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.PranaColorCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolChakraColorUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * ProtocolChakraColorEnricher — resolves (auto-creating on miss) the chakra
 * and prana-color rows referenced by free-text names/phrases, and upserts
 * the resulting {@link ProtocolChakraColorUsage} row (MDIMPORT-22).
 *
 * <p>Injected into {@code ProtocolImportService} rather than being its own
 * {@code MasterDataImporter} — this enriches protocol rows in-place on the
 * same {@code type=protocol} import path, it is not a separate importable
 * type.
 */
@Component
@RequiredArgsConstructor
public class ProtocolChakraColorEnricher {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^A-Z0-9]+");
    private static final int CODE_MAX_LENGTH = 30;

    private final ChakraCatalogRepository chakraRepo;
    private final PranaColorCatalogRepository pranaColorRepo;
    private final ProtocolChakraColorUsageRepository usageRepo;

    /**
     * One outcome per (protocolName, chakraName) enrichment attempt for a
     * single grouped row-set from the spreadsheet.
     */
    public record EnrichmentResult(boolean created, boolean updated, boolean skipped, String error) {
        static EnrichmentResult ofCreated() {
            return new EnrichmentResult(true, false, false, null);
        }

        static EnrichmentResult ofUpdated() {
            return new EnrichmentResult(false, true, false, null);
        }

        static EnrichmentResult ofSkipped(String error) {
            return new EnrichmentResult(false, false, true, error);
        }
    }

    /**
     * Resolves the chakra (backfilling a new {@link ChakraCatalog} row if no
     * name match exists) and every color phrase in both lists (backfilling
     * new {@link PranaColorCatalog} rows likewise), then creates or updates
     * the {@link ProtocolChakraColorUsage} row for (protocolName, chakra,
     * healerId) — replacing (not appending to) both color-id lists on an
     * update, matching {@code PranaColorImportService}'s existing
     * replace-on-re-import convention for its own list fields.
     *
     * @param rowLabel human-readable row identifier for error messages (e.g. "Row 5")
     */
    public EnrichmentResult enrich(String protocolName, String chakraName,
                                    List<String> cleansingPhrases, List<String> energizingPhrases,
                                    boolean isSuperAdmin, UUID healerId, String rowLabel) {
        if (chakraName == null || chakraName.isBlank()) {
            return EnrichmentResult.ofSkipped(rowLabel + ": missing chakra name for protocol '" + protocolName + "'");
        }

        ChakraCatalog chakra = resolveOrBackfillChakra(chakraName, isSuperAdmin, healerId);

        List<UUID> cleansingIds = new ArrayList<>();
        for (String phrase : cleansingPhrases) {
            cleansingIds.add(resolveOrBackfillColor(phrase, isSuperAdmin, healerId).getId());
        }
        List<UUID> energizingIds = new ArrayList<>();
        for (String phrase : energizingPhrases) {
            energizingIds.add(resolveOrBackfillColor(phrase, isSuperAdmin, healerId).getId());
        }

        ProtocolChakraColorUsage existing = findExisting(protocolName, chakra.getId(), healerId);
        // PD-40-04-style guard: a SYSTEM row (healerId == null) can only be
        // overwritten by SUPADM; another healer's custom row can only be
        // overwritten by that healer or SUPADM — same convention every other
        // importer in this codebase applies to its own upserted rows.
        if (existing != null && (existing.getHealerId() == null
                ? !isSuperAdmin
                : !Objects.equals(existing.getHealerId(), healerId) && !isSuperAdmin)) {
            return EnrichmentResult.ofSkipped(rowLabel + ": chakra usage for protocol '" + protocolName
                    + "' / chakra '" + chakraName + "' is owned by another party — not permitted to overwrite");
        }

        boolean wasCreate = existing == null;
        ProtocolChakraColorUsage usage = existing != null ? existing : new ProtocolChakraColorUsage();
        usage.setProtocolName(protocolName);
        usage.setChakraId(chakra.getId());
        if (wasCreate) {
            // Non-SUPADM callers can never create a SYSTEM (healerId == null) row via
            // import — mirrors every other importer's create-path convention.
            usage.setHealerId(isSuperAdmin ? null : healerId);
        }
        // Replace, not append — re-import must replace this chakra's color lists.
        usage.setCleansingColorIds(cleansingIds);
        usage.setEnergizingColorIds(energizingIds);
        usageRepo.save(usage);

        return wasCreate ? EnrichmentResult.ofCreated() : EnrichmentResult.ofUpdated();
    }

    private ProtocolChakraColorUsage findExisting(String protocolName, UUID chakraId, UUID healerId) {
        List<ProtocolChakraColorUsage> candidates = new ArrayList<>(
                usageRepo.findByProtocolNameAndHealerIdIsNull(protocolName));
        if (healerId != null) {
            candidates.addAll(usageRepo.findByProtocolNameAndHealerId(protocolName, healerId));
        }
        return candidates.stream()
                .filter(u -> Objects.equals(u.getChakraId(), chakraId))
                .findFirst()
                .orElse(null);
    }

    private ChakraCatalog resolveOrBackfillChakra(String name, boolean isSuperAdmin, UUID healerId) {
        Optional<ChakraCatalog> found = chakraRepo.findByNameIgnoreCase(name);
        if (found.isPresent()) {
            return found.get();
        }
        ChakraCatalog chakra = new ChakraCatalog();
        chakra.setCode(slugify(name));
        chakra.setName(name);
        chakra.setHealerId(isSuperAdmin ? null : healerId);
        return chakraRepo.save(chakra);
    }

    private PranaColorCatalog resolveOrBackfillColor(String phrase, boolean isSuperAdmin, UUID healerId) {
        Optional<PranaColorCatalog> found = pranaColorRepo.findByNameIgnoreCase(phrase);
        if (found.isPresent()) {
            return found.get();
        }
        PranaColorCatalog color = new PranaColorCatalog();
        color.setCode(slugify(phrase));
        color.setName(phrase);
        color.setHealerId(isSuperAdmin ? null : healerId);
        return pranaColorRepo.save(color);
    }

    /**
     * "Light Whitish Green" -> "LIGHT_WHITISH_GREEN"; "Whitish Orange-Yellow"
     * -> "WHITISH_ORANGE_YELLOW". Truncated to {@code code}'s column length
     * (30 chars, matching both {@code ChakraCatalog.code} and {@code
     * PranaColorCatalog.code}) as a defensive cap — real phrases seen in the
     * source data top out well under this, but backfilled codes are
     * generated, not admin-authored, so there is no other length guard.
     */
    private String slugify(String raw) {
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        String slug = NON_ALPHANUMERIC.matcher(upper).replaceAll("_");
        slug = slug.replaceAll("^_+|_+$", "");
        return slug.length() > CODE_MAX_LENGTH ? slug.substring(0, CODE_MAX_LENGTH) : slug;
    }
}
