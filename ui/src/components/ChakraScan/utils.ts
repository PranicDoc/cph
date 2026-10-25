import type { ChakraOption, ColorOption } from "../../interfaces";
import { BODY_REGIONS } from "./data/body-regions";
import { CHAKRA_CATALOG, getChakraById } from "./data/chakra-catalog";
import { PROTOCOL_LEVEL_LABELS } from "./data/protocol-catalog";
import type {
  BodyRegion,
  ChakraCatalogEntry,
  ChakraCategory,
  ChakraDocEntry,
  PranaColorEntry,
  ProtocolCatalogEntry,
} from "./types/spatial-chakra";

export type ChakraEntriesMap = Record<string, ChakraDocEntry>;

const OTHER_REGION_ID = "other";
const NEUTRAL_COLOR = "#94A3B8";

const BACKEND_CODE_TO_VISUAL_ID: Record<string, string> = {
  HEART_FRONT: "HEART_F",
  HEART_BACK: "HEART_B",
  SOLAR_PLEXUS_FRONT: "SOLAR_F",
  SOLAR_PLEXUS_BACK: "SOLAR_B",
  SPLEEN_FRONT: "SPLEEN_F",
  SPLEEN_BACK: "SPLEEN_B",
  JAW_LEFT: "JAW_L",
  JAW_RIGHT: "JAW_R",
  SHOULDER_LEFT: "SHOULDER_L",
  SHOULDER_RIGHT: "SHOULDER_R",
  ELBOW_LEFT: "ELBOW_L",
  ELBOW_RIGHT: "ELBOW_R",
  HAND_LEFT: "HAND_L",
  HAND_RIGHT: "HAND_R",
  HIP_LEFT: "HIP_L",
  HIP_RIGHT: "HIP_R",
  KNEE_LEFT: "KNEE_L",
  KNEE_RIGHT: "KNEE_R",
  SOLE_LEFT: "FOOT_L",
  SOLE_RIGHT: "FOOT_R",
};

const OTHER_REGION: BodyRegion = {
  id: OTHER_REGION_ID,
  name: "Other",
  icon: "-",
  chakraIds: [],
  cameraPosition: [0, 0.9, 3.2],
  cameraLookAt: [0, 0.9, 0],
  regionDotPosition: [0, 0.9, 0.2],
};

function normalizeCategory(
  category: string | undefined,
  fallback: ChakraCategory = "minor"
): ChakraCategory {
  if (category === "major" || category === "minor" || category === "organ_mini") {
    return category;
  }

  return fallback;
}

function getVisualIdForBackendCode(code: string | undefined) {
  if (!code) {
    return undefined;
  }

  return BACKEND_CODE_TO_VISUAL_ID[code] ?? code;
}

export function getVisualChakraForCode(
  code: string | undefined
): ChakraCatalogEntry | undefined {
  const visualId = getVisualIdForBackendCode(code);

  return visualId ? getChakraById(visualId) : undefined;
}

export function buildBackendChakraCatalog(
  chakras: ChakraOption[]
): ChakraCatalogEntry[] {
  return chakras
    .map((chakra, index) => {
      const visual = getVisualChakraForCode(chakra.code);

      return {
        id: chakra.id,
        code: chakra.code,
        name: chakra.name || visual?.name || chakra.code || chakra.id,
        category: normalizeCategory(chakra.category, visual?.category),
        location: chakra.location ?? visual?.location ?? "",
        side: visual?.side ?? "center",
        colorHex: visual?.colorHex ?? NEUTRAL_COLOR,
        colorLabel: visual?.colorLabel ?? "Neutral",
        bodyRegionId: visual?.bodyRegionId ?? OTHER_REGION_ID,
        position3D: visual?.position3D ?? [0, 0.9, 0.15],
        displayOrder: visual?.displayOrder ?? 1000 + index,
      };
    })
    .sort((a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name));
}

export function buildPranaColorCatalog(colors: ColorOption[]): PranaColorEntry[] {
  return colors.map((color) => {
    const colorHex = color.colorHex || NEUTRAL_COLOR;

    const propertiesText = color.properties?.join(", ") ?? "";

    return {
      id: color.id,
      code: color.code,
      name: color.name,
      colorHex,
      gradientFrom: colorHex,
      gradientTo: colorHex,
      primaryUses: propertiesText || color.name,
      properties: propertiesText,
      categories: color.categories,
    };
  });
}

export function buildRegionsForChakras(
  chakras: ChakraCatalogEntry[]
): BodyRegion[] {
  const chakraIdsByRegion = chakras.reduce<Record<string, string[]>>(
    (acc, chakra) => {
      const regionId = chakra.bodyRegionId || OTHER_REGION_ID;
      acc[regionId] = [...(acc[regionId] ?? []), chakra.id];
      return acc;
    },
    {}
  );

  return [...BODY_REGIONS, OTHER_REGION]
    .map((region) => ({
      ...region,
      chakraIds: chakraIdsByRegion[region.id] ?? [],
    }))
    .filter((region) => region.chakraIds.length > 0);
}

export function createDefaultEntry(chakra: ChakraCatalogEntry): ChakraDocEntry {
  return {
    id: chakra.id,
    code: chakra.code,
    name: chakra.name,
    color: chakra.colorHex,
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

export function getRegionStats(
  regionId: string,
  regions: BodyRegion[],
  entries: ChakraEntriesMap
) {
  const region = regions.find((r) => r.id === regionId);

  if (!region) {
    return { documented: 0, total: 0 };
  }

  return {
    documented: region.chakraIds.filter((id) => isDocumented(entries[id]))
      .length,
    total: region.chakraIds.length,
  };
}

export function getOverallStats(
  regions: BodyRegion[],
  entries: ChakraEntriesMap
) {
  return regions.reduce(
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

export { CHAKRA_CATALOG };
