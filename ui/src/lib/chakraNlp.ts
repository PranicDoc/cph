import type {
  ChakraCatalogEntry,
  ChakraDocEntry,
  EnergyLevel,
  ActivationLevel,
} from "../components/ChakraScan/types/spatial-chakra";
import { levenshtein, soundex } from "./soundex";

const ENERGY_KEYWORDS: Array<{ pattern: RegExp; level: EnergyLevel }> = [
  {
    pattern:
      /\b(depleted|depleated|deplete|low energy|low prana|weak|drained)\b/i,
    level: "D",
  },
  {
    pattern:
      /\b(congested|congestion|blocked|block|excess|excessive|overloaded)\b/i,
    level: "C",
  },
  { pattern: /\b(normal|good|fine|ok|okay|balanced|healthy)\b/i, level: "N" },
];

const ACTIVATION_KEYWORDS: Array<{
  pattern: RegExp;
  level: ActivationLevel;
}> = [
  {
    pattern:
      /\b(underactivated|under activated|under active|underactive|sluggish|slow)\b/i,
    level: "U",
  },
  {
    pattern:
      /\b(overactivated|over activated|over active|overactive|hyperactive|hyper)\b/i,
    level: "O",
  },
  { pattern: /\bnormal\b/i, level: "N" },
];

function matchEnergy(segment: string): EnergyLevel | null {
  for (const { pattern, level } of ENERGY_KEYWORDS) {
    if (pattern.test(segment)) return level;
  }
  return null;
}

function matchActivation(segment: string): ActivationLevel | null {
  for (const { pattern, level } of ACTIVATION_KEYWORDS) {
    if (pattern.test(segment)) return level;
  }
  return null;
}

interface ChakraSlice {
  chakra: ChakraCatalogEntry;
  text: string;
}

interface NameMatch {
  start: number;
  end: number;
  chakra: ChakraCatalogEntry;
}

/**
 * Finds every position where a catalog chakra name is actually said,
 * longest-name-first so "Crown Chakra" wins over any shorter overlapping
 * false match. Shared by the plain parser and the review-aware parser below.
 */
function findChakraNameMatches(
  transcript: string,
  catalog: ChakraCatalogEntry[]
): NameMatch[] {
  const matches: NameMatch[] = [];
  for (const chakra of catalog) {
    const escaped = chakra.name
      .split(/\s+/)
      .map((w) => w.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"))
      .join("\\s+");
    const regex = new RegExp(`\\b${escaped}\\b`, "gi");
    let m: RegExpExecArray | null;
    while ((m = regex.exec(transcript))) {
      matches.push({ start: m.index, end: m.index + m[0].length, chakra });
    }
  }

  // Longer/earlier matches win over shorter ones starting at/overlapping
  // the same position (catalog is pre-sorted longest-name-first, so ties
  // at the same start index keep the longer chakra name).
  matches.sort((a, b) => a.start - b.start || b.end - b.start - (a.end - a.start));

  const accepted: NameMatch[] = [];
  for (const match of matches) {
    const overlaps = accepted.some(
      (a) => match.start < a.end && match.end > a.start
    );
    if (!overlaps) accepted.push(match);
  }
  accepted.sort((a, b) => a.start - b.start);
  return accepted;
}

/**
 * Splits the transcript at every chakra-name mention rather than on
 * punctuation/filler words — speech-to-text output (esp. from in-browser
 * Whisper) is often one continuous run with no punctuation at all, so
 * punctuation-based segmentation silently merges different chakras'
 * energy/activation into whichever the loop last locked onto.
 */
function splitOnChakraNames(
  transcript: string,
  catalog: ChakraCatalogEntry[]
): ChakraSlice[] {
  const accepted = findChakraNameMatches(transcript, catalog);
  return accepted.map((match, i) => {
    const sliceEnd = i + 1 < accepted.length ? accepted[i + 1].start : transcript.length;
    return { chakra: match.chakra, text: transcript.slice(match.start, sliceEnd) };
  });
}

interface KeywordOccurrence<L> {
  start: number;
  end: number;
  level: L;
}

/** Same keyword lists as matchEnergy/matchActivation, but every occurrence
 * with its position — needed to interleave with chakra-name positions on one
 * timeline (matchEnergy/matchActivation only answer "is it present anywhere"). */
function findKeywordOccurrences<L>(
  transcript: string,
  keywords: Array<{ pattern: RegExp; level: L }>
): KeywordOccurrence<L>[] {
  const occurrences: KeywordOccurrence<L>[] = [];
  for (const { pattern, level } of keywords) {
    const flags = pattern.flags.includes("g") ? pattern.flags : pattern.flags + "g";
    const global = new RegExp(pattern.source, flags);
    let m: RegExpExecArray | null;
    while ((m = global.exec(transcript))) {
      occurrences.push({ start: m.index, end: m.index + m[0].length, level });
    }
  }
  return occurrences;
}

function extractLeftoverWords(rawText: string): string[] {
  const words: string[] = rawText.match(/[a-zA-Z]+/g) ?? [];
  return words.filter((word) => {
    const lower = word.toLowerCase();
    if (lower === "chakra" || lower === "unk" || lower === "unclear") return false;
    if (matchEnergy(word) || matchActivation(word)) return false;
    return true;
  });
}

function catalogPrimaryWord(chakra: ChakraCatalogEntry): string {
  const stripped = chakra.name
    .replace(/\bchakra\b/gi, "")
    .replace(/[^a-zA-Z\s]/g, "")
    .trim()
    .split(/\s+/)[0];
  return (stripped || chakra.name).toLowerCase();
}

/**
 * Ranks catalog chakras by how likely they are to be what a mis-transcribed
 * word actually meant — edit distance is the primary signal since real ASR
 * substitutions often don't preserve the first letter (e.g. "crown" heard as
 * "ground"), which is exactly the case Soundex alone misses; Soundex only
 * acts as a small tiebreaker on top; not a hard filter.
 */
export function rankCandidates(
  rawText: string,
  catalog: ChakraCatalogEntry[],
  limit = 3
): ChakraCatalogEntry[] {
  const leftoverWords = extractLeftoverWords(rawText);

  if (leftoverWords.length === 0) {
    return catalog.slice(0, limit);
  }

  const scored = catalog.map((chakra) => {
    const primary = catalogPrimaryWord(chakra);
    const primarySoundex = soundex(primary);
    let best = Infinity;
    for (const word of leftoverWords) {
      const distance = levenshtein(word.toLowerCase(), primary);
      const soundexBonus = soundex(word) === primarySoundex ? 0.5 : 0;
      const score = distance - soundexBonus;
      if (score < best) best = score;
    }
    return { chakra, score: best };
  });

  scored.sort((a, b) => a.score - b.score);
  return scored.slice(0, limit).map((s) => s.chakra);
}

export interface UnresolvedSegment {
  rawText: string;
  detectedEnergy: EnergyLevel | null;
  detectedActivation: ActivationLevel | null;
  candidates: ChakraCatalogEntry[];
  /** Set when the chakra name itself WAS recognized and only its energy
   * and/or activation level is missing — the review UI should prompt for
   * the missing level(s) directly instead of asking the user to pick a
   * different chakra (candidates is [] in this case). Null means the name
   * itself didn't match anything and candidates holds ranked guesses. */
  knownChakra: ChakraCatalogEntry | null;
}

export interface ChakraParseResult {
  entries: ChakraDocEntry[];
  unresolved: UnresolvedSegment[];
}

/**
 * Same job as parseChakraTranscript, but instead of silently dropping a
 * phrase whose chakra name didn't match anything in the catalog (e.g. ASR
 * misheard "Crown" as "Ground"), it surfaces that phrase with ranked
 * guesses so a human can confirm it — see TranscriptionReviewModal.
 *
 * Anchors (chakra-name mentions and energy/activation keyword mentions) are
 * merged onto one position-ordered timeline. A keyword found while no
 * chakra name is "open" starts an unresolved run; a second keyword on the
 * same axis (energy or activation) without an intervening chakra name means
 * a new, distinct unnamed phrase has started, so the first one is flushed.
 */
export function parseChakraTranscriptWithReview(
  transcript: string,
  availableChakras: ChakraCatalogEntry[],
  currentEntries: ChakraDocEntry[]
): ChakraParseResult {
  if (!transcript.trim()) return { entries: currentEntries, unresolved: [] };

  const sortedCatalog = [...availableChakras].sort(
    (a, b) => b.name.length - a.name.length
  );
  const entryMap = new Map(currentEntries.map((e) => [e.id, { ...e }]));
  const unresolved: UnresolvedSegment[] = [];

  type Anchor =
    | { type: "name"; start: number; end: number; chakra: ChakraCatalogEntry }
    | { type: "energy"; start: number; end: number; level: EnergyLevel }
    | { type: "activation"; start: number; end: number; level: ActivationLevel };

  const anchors: Anchor[] = [
    ...findChakraNameMatches(transcript, sortedCatalog).map(
      (m): Anchor => ({ type: "name", ...m })
    ),
    ...findKeywordOccurrences(transcript, ENERGY_KEYWORDS).map(
      (m): Anchor => ({ type: "energy", ...m })
    ),
    ...findKeywordOccurrences(transcript, ACTIVATION_KEYWORDS).map(
      (m): Anchor => ({ type: "activation", ...m })
    ),
  ].sort((a, b) => a.start - b.start);

  let currentChakra: ChakraCatalogEntry | null = null;
  let currentChakraStart = 0;
  let currentEnergy: EnergyLevel | null = null;
  let currentActivation: ActivationLevel | null = null;

  let unresolvedStart: number | null = null;
  let unresolvedEnergy: EnergyLevel | null = null;
  let unresolvedActivation: ActivationLevel | null = null;

  let lastAnchorEnd = 0;

  // A chakra name being said at all is a commitment to record both of its
  // levels (ChakraDocEntry requires both) — so whenever the name resolved
  // but the merged entry still lacks energy and/or activation, that's
  // surfaced for review instead of left silently incomplete.
  const flushResolved = (endIdx: number) => {
    if (currentChakra) {
      let entry = entryMap.get(currentChakra.id);
      if (!entry) {
        entry = { id: currentChakra.id, name: currentChakra.name } as ChakraDocEntry;
      }
      if (currentEnergy) entry.energyLevel = currentEnergy;
      if (currentActivation) entry.activationLevel = currentActivation;
      entryMap.set(currentChakra.id, entry);

      if (!entry.energyLevel || !entry.activationLevel) {
        unresolved.push({
          rawText: transcript.slice(currentChakraStart, endIdx).trim(),
          detectedEnergy: entry.energyLevel ?? null,
          detectedActivation: entry.activationLevel ?? null,
          candidates: [],
          knownChakra: currentChakra,
        });
      }
    }
    currentEnergy = null;
    currentActivation = null;
  };

  const flushUnresolved = (endIdx: number) => {
    if (unresolvedStart !== null && (unresolvedEnergy || unresolvedActivation)) {
      const rawText = transcript.slice(unresolvedStart, endIdx).trim();
      unresolved.push({
        rawText,
        detectedEnergy: unresolvedEnergy,
        detectedActivation: unresolvedActivation,
        candidates: rankCandidates(rawText, sortedCatalog),
        knownChakra: null,
      });
    }
    unresolvedStart = null;
    unresolvedEnergy = null;
    unresolvedActivation = null;
  };

  for (const anchor of anchors) {
    if (anchor.type === "name") {
      flushUnresolved(anchor.start);
      flushResolved(anchor.start);
      currentChakra = anchor.chakra;
      currentChakraStart = anchor.start;
    } else if (anchor.type === "energy") {
      if (currentChakra && unresolvedStart === null) {
        currentEnergy = anchor.level;
      } else {
        if (unresolvedStart !== null && unresolvedEnergy !== null) {
          flushUnresolved(anchor.start);
        }
        if (unresolvedStart === null) unresolvedStart = lastAnchorEnd;
        unresolvedEnergy = anchor.level;
        currentChakra = null;
      }
    } else {
      if (currentChakra && unresolvedStart === null) {
        currentActivation = anchor.level;
      } else {
        if (unresolvedStart !== null && unresolvedActivation !== null) {
          flushUnresolved(anchor.start);
        }
        if (unresolvedStart === null) unresolvedStart = lastAnchorEnd;
        unresolvedActivation = anchor.level;
        currentChakra = null;
      }
    }
    lastAnchorEnd = anchor.end;
  }

  flushResolved(transcript.length);
  flushUnresolved(transcript.length);

  return { entries: Array.from(entryMap.values()), unresolved };
}

export function parseChakraTranscript(
  transcript: string,
  availableChakras: ChakraCatalogEntry[],
  currentEntries: ChakraDocEntry[]
): ChakraDocEntry[] {
  if (!transcript.trim()) return currentEntries;

  const sortedCatalog = [...availableChakras].sort(
    (a, b) => b.name.length - a.name.length
  );

  const entryMap = new Map(currentEntries.map((e) => [e.id, { ...e }]));

  for (const { chakra, text } of splitOnChakraNames(transcript, sortedCatalog)) {
    const energy = matchEnergy(text);
    const activation = matchActivation(text);

    // If this slice contains only the chakra name with no descriptor,
    // there's nothing new to record for it yet.
    if (!energy && !activation) {
      continue;
    }

    let entry = entryMap.get(chakra.id);

    if (!entry) {
      entry = {
        id: chakra.id,
        name: chakra.name,
      } as ChakraDocEntry;
    }

    if (energy) {
      entry.energyLevel = energy;
    }

    if (activation) {
      entry.activationLevel = activation;
    }

    entryMap.set(chakra.id, entry);
  }

  return Array.from(entryMap.values());
}
