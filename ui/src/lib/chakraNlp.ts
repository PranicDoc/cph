import type {
  ChakraCatalogEntry,
  ChakraDocEntry,
  EnergyLevel,
  ActivationLevel,
} from "../components/ChakraScan/types/spatial-chakra";

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

function segmentTranscript(transcript: string): string[] {
  return transcript
    .replace(/\.\s+/g, "|||")
    .replace(/,\s*(then|next|also|and)\s+/gi, "|||")
    .split("|||")
    .map((s) => s.trim())
    .filter(Boolean);
}

function findChakra(
  segment: string,
  catalog: ChakraCatalogEntry[]
): ChakraCatalogEntry | null {
  const lower = segment.toLowerCase();
  // Catalog is pre-sorted by name length descending so longer names match first
  for (const chakra of catalog) {
    const words = chakra.name.toLowerCase().split(/\s+/);
    if (words.every((w) => lower.includes(w))) return chakra;
  }
  return null;
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

  for (const segment of segmentTranscript(transcript)) {
    const chakra = findChakra(segment, sortedCatalog);
    if (!chakra) continue;

    const energy = matchEnergy(segment);
    const activation = matchActivation(segment);

    if (!energy && !activation) continue;

    let entry = entryMap.get(chakra.id);
    if (!entry) {
      entry = {
        id: chakra.id,
        name: chakra.name,
      } as ChakraDocEntry;
    }

    if (energy) entry.energyLevel = energy;
    if (activation) entry.activationLevel = activation;

    entryMap.set(chakra.id, entry);
  }

  return Array.from(entryMap.values());
}
