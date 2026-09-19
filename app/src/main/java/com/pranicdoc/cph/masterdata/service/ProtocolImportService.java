package com.pranicdoc.cph.masterdata.service;

import com.pranicdoc.cph.common.enums.ColorUsageType;
import com.pranicdoc.cph.common.enums.ProtocolType;
import com.pranicdoc.cph.masterdata.dto.ProtocolImportResult;
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
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * ProtocolImportService — bulk xlsx/csv upsert of {@link ProtocolCatalog} rows.
 *
 * <p><b>Chakra/color enrichment:</b> a file may optionally also carry
 * {@code Chakras Used} / {@code Cleansing Color} / {@code Energizing Color}
 * columns. Detection is column-presence based, not a separate upload type —
 * a plain protocol file (no such columns) takes the original per-row path,
 * byte-for-byte unchanged; a file carrying those columns takes the grouped
 * path in {@link #importWithChakraColorEnrichment}, which additionally links
 * each protocol to the chakras it addresses ({@link ProtocolChakra}) and the
 * cleansing/energizing colors used for each protocol+chakra combination
 * ({@link ProtocolChakraColor}).
 *
 * <p>A protocol may reference several chakras, each with its own color set.
 * Because {@code Protocol/Technique Name} only needs to appear once per
 * protocol, subsequent chakra rows for the same protocol leave the name
 * column blank; such a row is treated as a continuation of the most recently
 * named group rather than a new protocol (and, unlike the base per-row path,
 * is not an error).
 *
 * <p><b>The enrichment path never creates or modifies a protocol row.</b> It is
 * relationship-only: for each group, the protocol is looked up by (name, resolved
 * book) alone — no page — since a protocol can legitimately span multiple pages
 * under the same name and book, and every matching row is enriched. The resolved
 * book is the row's own Book value when present, else {@link #DEFAULT_SOURCE_BOOK}.
 * No match skips the whole group (including its chakra/color rows) with an error
 * rather than creating one. Protocols are only ever created or updated by the base
 * per-row path (a file with no chakra/color columns).
 *
 * <p><b>Data-quality guard:</b> a chakra name or color phrase that looks like
 * free-text prose rather than a catalog entry (contains a colon or newline,
 * is implausibly long, or is a known placeholder like "Not Specified") is
 * rejected with a per-row error instead of being silently backfilled as a
 * garbage catalog row — see {@link #isValidPhrase}.
 */
@Service
@RequiredArgsConstructor
public class ProtocolImportService {

    private static final String SHEET_NAME = "Protocols";
    private static final String COL_NAME = "Protocol/Technique Name";
    private static final String COL_BOOK = "Book";
    private static final String COL_PAGE = "Page";
    private static final String COL_CATEGORY = "Category";
    private static final String COL_DESCRIPTION = "Description";
    private static final String COL_CHAPTER = "Chapter";
    private static final String COL_TYPE = "Type";
    private static final String COL_CHAKRAS_USED = "Chakras Used";
    private static final String COL_CLEANSING_COLOR = "Cleansing Color";
    private static final String COL_ENERGIZING_COLOR = "Energizing Color";

    /**
     * ASSUMPTION, stated here for visibility: the chakra/color enrichment format carries no
     * Book column. Used only to resolve which existing protocol a row's chakra/color data
     * matches — the enrichment path never creates or writes a {@link ProtocolCatalog} row, so
     * this never determines a stored value, only a lookup key. Overridden per-row whenever the
     * file DOES supply a Book value.
     */
    static final String DEFAULT_SOURCE_BOOK = "Advanced Pranic Healing";

    /** Known "no value" placeholders seen in real data — never backfilled as a catalog row. */
    private static final Set<String> PLACEHOLDER_PHRASES =
            Set.of("not specified", "n/a", "na", "none", "not applicable");
    private static final int MAX_PHRASE_LENGTH = 80;

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^A-Z0-9]+");
    private static final int CODE_MAX_LENGTH = 30;

    private final ProtocolCatalogRepository protocolRepo;
    private final ChakraCatalogRepository chakraRepo;
    private final PranaColorCatalogRepository pranaColorRepo;
    private final ProtocolChakraRepository protocolChakraRepo;
    private final ProtocolChakraColorRepository protocolChakraColorRepo;

    @Transactional
    public ProtocolImportResult importFile(MultipartFile file) {
        try {
            return importFile(file.getOriginalFilename(), file.getInputStream());
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read uploaded file: " + e.getMessage());
        }
    }

    /**
     * Same processing as {@link #importFile(MultipartFile)}, but off a filename + stream
     * instead of a {@link MultipartFile} — the async import path ({@code
     * ProtocolImportAsyncRunner}) reads the upload into memory synchronously (a
     * MultipartFile's backing resource doesn't survive past the request), then calls this
     * overload from a background thread via a fresh {@code ByteArrayInputStream}.
     */
    @Transactional
    public ProtocolImportResult importFile(String filename, InputStream inputStream) {
        String extension = extractExtension(filename);

        List<Map<String, String>> rows = switch (extension) {
            case "xlsx" -> readXlsxRows(inputStream);
            case "csv" -> readCsvRows(inputStream);
            default -> throw new IllegalArgumentException(
                    "Unsupported file type '" + extension + "' — only .xlsx and .csv are accepted");
        };

        boolean hasChakraColorColumns = !rows.isEmpty() && (rows.get(0).containsKey(COL_CHAKRAS_USED)
                || rows.get(0).containsKey(COL_CLEANSING_COLOR) || rows.get(0).containsKey(COL_ENERGIZING_COLOR));
        if (hasChakraColorColumns) {
            return importWithChakraColorEnrichment(rows);
        }
        return importBaseRows(rows);
    }

    // ── Base per-row path (original curated-catalog format — unchanged) ────────────────────

    private ProtocolImportResult importBaseRows(List<Map<String, String>> rows) {
        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        int rowNumber = 1; // header is row 0 (not counted); data rows start at 1
        for (Map<String, String> row : rows) {
            rowNumber++;
            String name = trimToNull(row.get(COL_NAME));
            String sourceBook = trimToNull(row.get(COL_BOOK));
            String pageRaw = trimToNull(row.get(COL_PAGE));
            String typeRaw = trimToNull(row.get(COL_TYPE));
            String category = trimToNull(row.get(COL_CATEGORY));
            String description = trimToNull(row.get(COL_DESCRIPTION));
            String chapter = trimToNull(row.get(COL_CHAPTER));

            if (name == null) {
                errors.add("Row " + rowNumber + ": missing " + COL_NAME);
                skipped++;
                continue;
            }
            if (sourceBook == null) {
                errors.add("Row " + rowNumber + ": missing " + COL_BOOK);
                skipped++;
                continue;
            }
            if (pageRaw == null) {
                errors.add("Row " + rowNumber + ": missing " + COL_PAGE);
                skipped++;
                continue;
            }
            Integer pageNumber = parsePageNumber(pageRaw);
            if (pageNumber == null) {
                errors.add("Row " + rowNumber + ": " + COL_PAGE + " '" + pageRaw + "' is not numeric");
                skipped++;
                continue;
            }
            ProtocolType protocolType = parseProtocolType(typeRaw);
            if (protocolType == null) {
                errors.add("Row " + rowNumber + ": invalid " + COL_TYPE + " '" + typeRaw
                        + "' — must be PROTOCOL or SUPPLEMENTARY");
                skipped++;
                continue;
            }

            ProtocolCatalog protocol = protocolRepo
                    .findByNameAndSourceBookAndPageNumber(name, sourceBook, pageNumber)
                    .orElse(null);
            if (protocol == null) {
                protocol = new ProtocolCatalog();
                protocol.setName(name);
                protocol.setSourceBook(sourceBook);
                protocol.setPageNumber(pageNumber);
                protocol.setHealerId(null);
                created++;
            } else {
                updated++;
            }
            protocol.setCategory(category);
            protocol.setDescription(description);
            protocol.setChapter(chapter);
            protocol.setProtocolType(protocolType);
            protocolRepo.save(protocol);
        }

        return new ProtocolImportResult(rows.size(), created, updated, skipped, errors);
    }

    // ── Chakra/color enrichment path ───────────────────────────────────────

    /** One data row, kept with its 1-based row number for error messages. */
    private record RowEntry(int rowNumber, Map<String, String> row) {
    }

    /** A protocol's lead row plus every continuation row grouped under it. */
    private static final class Group {
        final int leadRowNumber;
        final List<RowEntry> rows = new ArrayList<>();
        List<ProtocolCatalog> protocols; // resolved by lookup only; null if none found (group skipped)

        Group(int leadRowNumber) {
            this.leadRowNumber = leadRowNumber;
        }
    }

    /** Outcome of looking up (never creating) the protocol(s) for one group. */
    private record GroupResolution(List<ProtocolCatalog> protocols, String error) {
        static GroupResolution of(List<ProtocolCatalog> protocols) {
            return new GroupResolution(protocols, null);
        }

        static GroupResolution ofError(String error) {
            return new GroupResolution(null, error);
        }
    }

    /** Composite key for the in-flight (protocolId, chakraId) / (protocolChakraId, colorId, usageType) dedup maps. */
    private record LinkKey(UUID a, UUID b, ColorUsageType usageType) {
        static LinkKey of(UUID protocolId, UUID chakraId) {
            return new LinkKey(protocolId, chakraId, null);
        }

        static LinkKey of(UUID protocolChakraId, UUID colorId, ColorUsageType usageType) {
            return new LinkKey(protocolChakraId, colorId, usageType);
        }
    }

    private ProtocolImportResult importWithChakraColorEnrichment(List<Map<String, String>> rows) {
        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        // ── Step 1: group continuation rows (blank name) under their most recent named row ──
        List<Group> groups = new ArrayList<>();
        Group current = null;
        int rowNumber = 1;
        for (Map<String, String> row : rows) {
            rowNumber++;
            String name = trimToNull(row.get(COL_NAME));
            if (name != null) {
                current = new Group(rowNumber);
                groups.add(current);
            }
            if (current == null) {
                // No protocol group started yet — nothing to attach this row's data to.
                errors.add("Row " + rowNumber + ": missing " + COL_NAME + " and no preceding protocol row to attach to");
                skipped++;
                continue;
            }
            current.rows.add(new RowEntry(rowNumber, row));
        }

        // ── Step 2: look up (never create) the protocol for each group from its lead row ──
        for (Group group : groups) {
            Map<String, String> leadRow = group.rows.get(0).row();
            GroupResolution resolution = resolveProtocolsForGroup(leadRow, group.leadRowNumber);
            if (resolution.error() != null) {
                errors.add(resolution.error());
                skipped++;
                // group.protocol stays null — its chakra/color rows are skipped below.
                continue;
            }
            group.protocols = resolution.protocols();
        }

        // ── Step 3: preload existing protocol_chakra links for every resolved protocol,
        //     in one query, so the per-row loop below never issues a per-combination SELECT. ──
        List<UUID> protocolIds = groups.stream()
                .filter(g -> g.protocols != null)
                .flatMap(g -> g.protocols.stream())
                .map(ProtocolCatalog::getId)
                .distinct()
                .toList();
        Map<LinkKey, ProtocolChakra> existingLinks = new HashMap<>();
        if (!protocolIds.isEmpty()) {
            for (ProtocolChakra link : protocolChakraRepo.findByProtocol_IdIn(protocolIds)) {
                existingLinks.put(LinkKey.of(link.getProtocol().getId(), link.getChakra().getId()), link);
            }
        }
        Set<LinkKey> existingColorLinks = new HashSet<>();
        if (!existingLinks.isEmpty()) {
            List<UUID> existingLinkIds = existingLinks.values().stream().map(ProtocolChakra::getId).toList();
            for (ProtocolChakraColor pcc : protocolChakraColorRepo.findByProtocolChakra_IdIn(existingLinkIds)) {
                existingColorLinks.add(LinkKey.of(pcc.getProtocolChakra().getId(), pcc.getColor().getId(), pcc.getUsageType()));
            }
        }

        // ── Step 4: preload the full system chakra/color catalogs by name, once, so
        //     resolve-or-backfill never issues a per-row SELECT either. ──
        Map<String, ChakraCatalog> chakraByName = new HashMap<>();
        Set<String> chakraCodes = new HashSet<>();
        for (ChakraCatalog c : chakraRepo.findByHealerIdIsNull()) {
            chakraByName.put(c.getName().toLowerCase(Locale.ROOT), c);
            chakraCodes.add(c.getCode());
        }
        Map<String, PranaColorCatalog> colorByName = new HashMap<>();
        Set<String> colorCodes = new HashSet<>();
        for (PranaColorCatalog c : pranaColorRepo.findByHealerIdIsNull()) {
            colorByName.put(c.getName().toLowerCase(Locale.ROOT), c);
            colorCodes.add(c.getCode());
        }

        // ── Step 5: walk every row of every valid group, resolving/backfilling chakra + colors
        //     and creating the ProtocolChakra / ProtocolChakraColor rows for the matched protocol.
        //     The protocol row itself is never touched here — see Step 2. ──
        for (Group group : groups) {
            if (group.protocols == null) continue; // group-level error already recorded above

            for (RowEntry entry : group.rows) {
                String chakraName = trimToNull(entry.row().get(COL_CHAKRAS_USED));
                String cleansingRaw = entry.row().get(COL_CLEANSING_COLOR);
                String energizingRaw = entry.row().get(COL_ENERGIZING_COLOR);

                if (chakraName == null && trimToNull(cleansingRaw) == null && trimToNull(energizingRaw) == null) {
                    continue; // no chakra/color data on this row — nothing to enrich
                }
                if (chakraName == null) {
                    errors.add("Row " + entry.rowNumber() + ": color data present but missing " + COL_CHAKRAS_USED);
                    skipped++;
                    continue;
                }
                if (!isValidPhrase(chakraName)) {
                    errors.add("Row " + entry.rowNumber() + ": '" + truncateForMessage(chakraName)
                            + "' does not look like a chakra name — skipped");
                    skipped++;
                    continue;
                }

                PhraseExtractionResult cleansingResult = extractColorPhrases(cleansingRaw, "cleansing", entry.rowNumber());
                errors.addAll(cleansingResult.rejectionErrors());
                skipped += cleansingResult.rejectionErrors().size();
                List<String> cleansingPhrases = cleansingResult.validPhrases();

                PhraseExtractionResult energizingResult = extractColorPhrases(energizingRaw, "energizing", entry.rowNumber());
                errors.addAll(energizingResult.rejectionErrors());
                skipped += energizingResult.rejectionErrors().size();
                List<String> energizingPhrases = energizingResult.validPhrases();

                ChakraCatalog chakra = resolveOrBackfillChakra(chakraName, chakraByName, chakraCodes);

                for (ProtocolCatalog protocol : group.protocols) {
                    LinkKey linkKey = LinkKey.of(protocol.getId(), chakra.getId());
                    ProtocolChakra link = existingLinks.get(linkKey);
                    if (link == null) {
                        link = ProtocolChakra.builder().protocol(protocol).chakra(chakra).build();
                        link = protocolChakraRepo.save(link);
                        existingLinks.put(linkKey, link);
                        created++;
                    }

                    for (String phrase : cleansingPhrases) {
                        if (linkColor(link, phrase, ColorUsageType.CLEANSING, colorByName, colorCodes, existingColorLinks)) {
                            created++;
                        }
                    }
                    for (String phrase : energizingPhrases) {
                        if (linkColor(link, phrase, ColorUsageType.ENERGISING, colorByName, colorCodes, existingColorLinks)) {
                            created++;
                        }
                    }
                }
            }
        }

        return new ProtocolImportResult(rows.size(), created, updated, skipped, errors);
    }

    /**
     * Looks up — never creates, never writes — the protocol(s) a group's chakra/color data
     * should attach to, by (name, resolved book) alone — page is not part of this match, since
     * the same protocol can legitimately span multiple pages under one name and book, and every
     * such row should be enriched. A row whose protocol doesn't match anything existing is
     * skipped with an error rather than creating one; this path is relationship-only.
     */
    private GroupResolution resolveProtocolsForGroup(Map<String, String> leadRow, int rowNumber) {
        String name = trimToNull(leadRow.get(COL_NAME));
        if (name == null) {
            return GroupResolution.ofError("Row " + rowNumber + ": missing " + COL_NAME);
        }
        String sourceBookRaw = trimToNull(leadRow.get(COL_BOOK));
        String sourceBook = sourceBookRaw != null ? sourceBookRaw : DEFAULT_SOURCE_BOOK;

        List<ProtocolCatalog> matches = protocolRepo.findByNameAndSourceBook(name, sourceBook);
        if (!matches.isEmpty()) {
            return GroupResolution.of(matches);
        }
        return GroupResolution.ofError("Row " + rowNumber + ": protocol '" + name
                + "' not found (book '" + sourceBook
                + "') — chakra/color import never creates or modifies protocols");
    }

    /**
     * Resolves (or backfills) one color phrase and links it to {@code link}
     * under {@code usageType} if not already linked. Returns {@code true}
     * when a new {@link ProtocolChakraColor} row was created.
     */
    private boolean linkColor(ProtocolChakra link, String phrase, ColorUsageType usageType,
                               Map<String, PranaColorCatalog> colorByName, Set<String> colorCodes,
                               Set<LinkKey> existingColorLinks) {
        PranaColorCatalog color = resolveOrBackfillColor(phrase, colorByName, colorCodes);
        LinkKey key = LinkKey.of(link.getId(), color.getId(), usageType);
        if (existingColorLinks.contains(key)) {
            return false;
        }
        ProtocolChakraColor pcc = ProtocolChakraColor.builder()
                .protocolChakra(link)
                .color(color)
                .usageType(usageType)
                .build();
        protocolChakraColorRepo.save(pcc);
        existingColorLinks.add(key);
        return true;
    }

    private ChakraCatalog resolveOrBackfillChakra(String name, Map<String, ChakraCatalog> byName, Set<String> codes) {
        ChakraCatalog existing = byName.get(name.toLowerCase(Locale.ROOT));
        if (existing != null) {
            return existing;
        }
        ChakraCatalog chakra = new ChakraCatalog();
        chakra.setCode(uniqueCode(name, codes));
        chakra.setName(name);
        chakra.setHealerId(null);
        chakra = chakraRepo.save(chakra);
        byName.put(name.toLowerCase(Locale.ROOT), chakra);
        codes.add(chakra.getCode());
        return chakra;
    }

    private PranaColorCatalog resolveOrBackfillColor(String name, Map<String, PranaColorCatalog> byName, Set<String> codes) {
        PranaColorCatalog existing = byName.get(name.toLowerCase(Locale.ROOT));
        if (existing != null) {
            return existing;
        }
        PranaColorCatalog color = new PranaColorCatalog();
        color.setCode(uniqueCode(name, codes));
        color.setName(name);
        color.setHealerId(null);
        color = pranaColorRepo.save(color);
        byName.put(name.toLowerCase(Locale.ROOT), color);
        codes.add(color.getCode());
        return color;
    }

    /**
     * "Light Whitish Green" -> "LIGHT_WHITISH_GREEN", truncated to the
     * catalog {@code code} column length (30 chars) and de-duplicated
     * against {@code takenCodes} (in-memory only — both catalogs are fully
     * preloaded into that set before this runs, so no extra query is
     * needed) by appending a numeric suffix on collision.
     */
    private String uniqueCode(String name, Set<String> takenCodes) {
        String upper = name.trim().toUpperCase(Locale.ROOT);
        String slug = NON_ALPHANUMERIC.matcher(upper).replaceAll("_").replaceAll("^_+|_+$", "");
        if (slug.length() > CODE_MAX_LENGTH) {
            slug = slug.substring(0, CODE_MAX_LENGTH);
        }
        if (!takenCodes.contains(slug)) {
            return slug;
        }
        int suffix = 2;
        String candidate;
        int baseLength = Math.min(slug.length(), CODE_MAX_LENGTH - 2);
        do {
            candidate = slug.substring(0, baseLength) + "_" + suffix;
            suffix++;
        } while (takenCodes.contains(candidate));
        return candidate;
    }

    /** Splits a cell on commas (the source data's multi-color-per-cell delimiter), trims, drops blanks. */
    private List<String> splitColorPhrases(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> phrases = new ArrayList<>();
        for (String piece : raw.split(",")) {
            String trimmed = piece.trim();
            if (!trimmed.isEmpty()) {
                phrases.add(trimmed);
            }
        }
        return phrases;
    }

    /** Valid phrases plus the rejection error message for each one that wasn't, from one cell. */
    private record PhraseExtractionResult(List<String> validPhrases, List<String> rejectionErrors) {
    }

    /**
     * Extracts color phrases from one cell, guarding against a per-chakra color breakdown
     * written as prose inside a single cell (colon- and/or newline-separated groups, each
     * internally comma-separated). Splitting such a cell on comma FIRST and validating each
     * fragment independently would only catch the one fragment that happens to carry the colon
     * and silently backfill the rest as bogus colors — so the whole raw cell is checked for
     * that structure BEFORE any splitting happens; a cell that fails is rejected in one piece
     * with a single error, never split. A cell that passes is split normally, with each
     * resulting phrase still checked individually (blank/length/placeholder).
     */
    private PhraseExtractionResult extractColorPhrases(String raw, String label, int rowNumber) {
        if (trimToNull(raw) == null) {
            return new PhraseExtractionResult(List.of(), List.of());
        }
        if (looksLikeStructuredProse(raw)) {
            return new PhraseExtractionResult(List.of(), List.of("Row " + rowNumber + ": " + label
                    + " color cell '" + truncateForMessage(raw.trim()) + "' looks like free text, not a color list — skipped"));
        }
        List<String> valid = new ArrayList<>();
        List<String> rejectionErrors = new ArrayList<>();
        for (String phrase : splitColorPhrases(raw)) {
            if (isValidPhrase(phrase)) {
                valid.add(phrase);
            } else {
                rejectionErrors.add("Row " + rowNumber + ": " + label + " color '" + truncateForMessage(phrase)
                        + "' does not look like a color name — skipped");
            }
        }
        return new PhraseExtractionResult(valid, rejectionErrors);
    }

    /** True when a raw (not-yet-split) cell carries a colon or newline — a per-item breakdown, not a simple list. */
    private boolean looksLikeStructuredProse(String raw) {
        return raw.indexOf(':') >= 0 || raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0;
    }

    /**
     * Rejects a candidate chakra/color phrase that looks like free-text prose rather than a
     * catalog entry name — see {@link #looksLikeStructuredProse} for the whole-cell counterpart
     * applied to color cells before splitting. Also rejects placeholder tokens like
     * "Not Specified" where no real value applies. Backfilling either as a literal chakra/color
     * catalog row would corrupt master data, so both are rejected with a per-row error instead.
     */
    private boolean isValidPhrase(String phrase) {
        if (phrase == null) return false;
        String trimmed = phrase.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_PHRASE_LENGTH) return false;
        if (looksLikeStructuredProse(trimmed)) return false;
        return !PLACEHOLDER_PHRASES.contains(trimmed.toLowerCase(Locale.ROOT));
    }

    private String truncateForMessage(String value) {
        return value.length() > 40 ? value.substring(0, 40) + "…" : value;
    }

    // ── File reading ─────────────────────────────────────────────────────

    private List<Map<String, String>> readXlsxRows(InputStream inputStream) {
        List<Map<String, String>> rows = new ArrayList<>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) {
                throw new IllegalArgumentException("Sheet '" + SHEET_NAME + "' not found in the uploaded file");
            }
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new IllegalArgumentException("Sheet '" + SHEET_NAME + "' has no header row");
            }
            Map<Integer, String> headerByIndex = new LinkedHashMap<>();
            for (Cell cell : headerRow) {
                headerByIndex.put(cell.getColumnIndex(), cellToString(cell));
            }
            for (int r = headerRow.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Map<String, String> rowMap = new LinkedHashMap<>();
                for (Map.Entry<Integer, String> entry : headerByIndex.entrySet()) {
                    Cell cell = row.getCell(entry.getKey());
                    rowMap.put(entry.getValue(), cell == null ? null : cellToString(cell));
                }
                rows.add(rowMap);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read xlsx file: " + e.getMessage());
        }
        return rows;
    }

    private List<Map<String, String>> readCsvRows(InputStream inputStream) {
        List<Map<String, String>> rows = new ArrayList<>();
        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {
            for (CSVRecord record : parser) {
                Map<String, String> rowMap = new LinkedHashMap<>();
                record.toMap().forEach(rowMap::put);
                rows.add(rowMap);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read csv file: " + e.getMessage());
        }
        return rows;
    }

    private String cellToString(Cell cell) {
        if (cell.getCellType() == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (value == Math.floor(value)) {
                return String.valueOf((long) value);
            }
            return String.valueOf(value);
        }
        return cell.toString().trim();
    }

    private Integer parsePageNumber(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ProtocolType parseProtocolType(String raw) {
        if (raw == null) return null;
        try {
            return ProtocolType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
