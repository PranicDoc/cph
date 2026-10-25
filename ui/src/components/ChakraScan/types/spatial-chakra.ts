import type { ColorCategory } from "@/lib/colorCategory";

// ============================================================
// Spatial Chakra Scan — Type Definitions
// ============================================================

// --- Energy & Activation Enums ---

export type EnergyLevel = "D" | "N" | "C";
export type ActivationLevel = "U" | "N" | "O";
export type ColorUsageType = "cleansing" | "energising";
export type ScanType = "pre" | "post";
export type BodyModelType = "xbot" | "anatomical";
export type ChakraCategory = "major" | "minor" | "organ_mini";
export type BodySide = "front" | "back" | "center";

export const ENERGY_LEVEL_LABELS: Record<EnergyLevel, string> = {
  D: "Depleted",
  N: "Normal",
  C: "Congested",
};

export const ACTIVATION_LEVEL_LABELS: Record<ActivationLevel, string> = {
  U: "Underactivated",
  N: "Normal",
  O: "Overactivated",
};

// --- Chakra Catalog ---

export interface ChakraCatalogEntry {
  id: string;
  code?: string;
  name: string;
  category: ChakraCategory;
  location: string;
  side: BodySide;
  colorHex: string;
  colorLabel: string;
  bodyRegionId: string;
  position3D: [number, number, number];
  displayOrder: number;
}

// --- Body Region ---

export interface BodyRegion {
  id: string;
  name: string;
  icon: string;
  chakraIds: string[];
  cameraPosition: [number, number, number];
  cameraLookAt: [number, number, number];
  regionDotPosition: [number, number, number];
}

// --- Protocol ---

export interface ProtocolCatalogEntry {
  id: string;
  name: string;
  category: string;
  level: "basic" | "advanced" | "psychotherapy" | "crystal";
}

// --- Prana Color ---

export interface PranaColorEntry {
  id: string;
  name: string;
  code: string;
  colorHex: string;
  gradientFrom: string;
  gradientTo: string;
  primaryUses: string;
  properties: string;
  categories?: ColorCategory[];
}

// --- Treatment Color (selected on a chakra) ---

export interface TreatmentColor {
  id: string;
  name: string;
  colorHex: string;
  usageType: ColorUsageType;
}

// --- Protocol Selection ---

export interface ProtocolSelection {
  id: string;
  name: string;
  category: string;
  level: string;
}

// --- Chakra Documentation Entry (per-chakra form data) ---

export interface ChakraDocEntry {
  id: string;
  name: string;
  code?: string;
  color: string;
  energyLevel: EnergyLevel;
  activationLevel: ActivationLevel;
  protocol?: ProtocolSelection;
  treatmentColors: TreatmentColor[];
  notes?: string;
}

// --- Top-level component value ---

export interface SpatialChakraScanValue {
  chakras: ChakraDocEntry[];
}

// --- Component Props ---

export interface SpatialChakraScanProps {
  value: ChakraDocEntry[];
  onChange: (entries: ChakraDocEntry[]) => void;
  availableChakras?: ChakraCatalogEntry[];
  protocolCatalog?: ProtocolCatalogEntry[];
  pranaColorCatalog?: PranaColorEntry[];
  scanType?: ScanType;
  onComplete?: () => void;
  /** Switch body model: 'anatomical' (default) uses smooth mannequin, 'xbot' uses robotic Xbot */
  bodyModel?: BodyModelType;
}

// --- Camera Machine Types ---

export type CameraState = "overview" | "regionFocus" | "chakraFocus";

export interface CameraTarget {
  position: [number, number, number];
  lookAt: [number, number, number];
  duration: number;
}

// --- Store Types ---

export interface SpatialChakraState {
  selectedRegionId: string | null;
  selectedChakraId: string | null;
  expandedCardId: string | null;
  chakraEntries: Map<string, ChakraDocEntry>;
  documentedChakraIds: Set<string>;
  scanType: ScanType;
}

export interface SpatialChakraActions {
  selectRegion: (regionId: string) => void;
  selectChakra: (chakraId: string) => void;
  expandCard: (chakraId: string) => void;
  collapseCard: () => void;
  updateEnergyLevel: (chakraId: string, level: EnergyLevel) => void;
  updateActivationLevel: (chakraId: string, level: ActivationLevel) => void;
  setProtocol: (
    chakraId: string,
    protocol: ProtocolSelection | undefined
  ) => void;
  addTreatmentColor: (chakraId: string, color: TreatmentColor) => void;
  removeTreatmentColor: (chakraId: string, colorId: string) => void;
  navigateToNextChakra: () => void;
  navigateToPreviousChakra: () => void;
  initializeFromValue: (entries: ChakraDocEntry[]) => void;
  getRegionProgress: (regionId: string) => {
    documented: number;
    total: number;
  };
  getAllEntries: () => ChakraDocEntry[];
}
