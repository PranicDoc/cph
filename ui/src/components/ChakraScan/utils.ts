import { BODY_REGIONS } from "./data/body-regions";
import { getChakraById } from "./data/chakra-catalog";
import { PROTOCOL_LEVEL_LABELS } from "./data/protocol-catalog";
import type {
  ChakraDocEntry,
  ProtocolCatalogEntry,
} from "./types/spatial-chakra";

export type ChakraEntriesMap = Record<string, ChakraDocEntry>;

export function createDefaultEntry(chakraId: string): ChakraDocEntry {
  const chakra = getChakraById(chakraId);

  return {
    id: chakraId,
    name: chakra?.name ?? chakraId,
    color: chakra?.colorHex ?? "#888",
    energyLevel: "N",
    activationLevel: "N",
    treatmentColors: [],
  };
}

export function createEntriesMap(entries: ChakraDocEntry[]): ChakraEntriesMap {
  return entries.reduce<ChakraEntriesMap>((acc, entry) => {
    acc[entry.id] = entry;
    return acc;
  }, {});
}

export function isDocumented(entry: ChakraDocEntry | undefined): boolean {
  if (!entry) {
    return false;
  }

  return (
    entry.energyLevel !== "N" ||
    entry.activationLevel !== "N" ||
    !!entry.protocol ||
    entry.treatmentColors.length > 0
  );
}

export function getRegionStats(regionId: string, entries: ChakraEntriesMap) {
  const region = BODY_REGIONS.find((r) => r.id === regionId);

  if (!region) {
    return { documented: 0, total: 0 };
  }

  return {
    documented: region.chakraIds.filter((id) => isDocumented(entries[id]))
      .length,
    total: region.chakraIds.length,
  };
}

export function getOverallStats(entries: ChakraEntriesMap) {
  return BODY_REGIONS.reduce(
    (stats, region) => {
      const documented = region.chakraIds.filter((id) =>
        isDocumented(entries[id])
      ).length;

      return {
        documented: stats.documented + documented,
        total: stats.total + region.chakraIds.length,
      };
    },
    { documented: 0, total: 0 }
  );
}

export function getProtocolSearchLabel(protocol: ProtocolCatalogEntry) {
  const level = PROTOCOL_LEVEL_LABELS[protocol.level] || protocol.level;

  return `${protocol.name} - ${level} - ${protocol.category}`;
}
