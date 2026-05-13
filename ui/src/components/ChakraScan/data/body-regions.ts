import { BodyRegion } from "../types/spatial-chakra";

export interface BilateralConfig {
  centerChakraIds: string[];
  pairedChakras: { left: string; right: string; label: string }[];
  leftCamera: { position: [number, number, number]; lookAt: [number, number, number] };
  rightCamera: { position: [number, number, number]; lookAt: [number, number, number] };
}

export const BILATERAL_REGIONS: Record<string, BilateralConfig> = {
  "hands-arms": {
    centerChakraIds: [],
    pairedChakras: [
      { left: "SHOULDER_L", right: "SHOULDER_R", label: "Shoulder" },
      { left: "ELBOW_L", right: "ELBOW_R", label: "Elbow" },
      { left: "HAND_L", right: "HAND_R", label: "Hand" },
      { left: "FINGER_L", right: "FINGER_R", label: "Finger Tips" },
    ],
    leftCamera: { position: [-0.5, 1.15, 1.8], lookAt: [-0.3, 1.15, 0] },
    rightCamera: { position: [0.5, 1.15, 1.8], lookAt: [0.3, 1.15, 0] },
  },
  "pelvis-legs": {
    centerChakraIds: ["SEX", "BASIC", "PERINEUM"],
    pairedChakras: [
      { left: "HIP_L", right: "HIP_R", label: "Hip" },
      { left: "KNEE_L", right: "KNEE_R", label: "Knee" },
      { left: "FOOT_L", right: "FOOT_R", label: "Foot" },
      { left: "TOE_L", right: "TOE_R", label: "Toe" },
    ],
    leftCamera: { position: [-0.25, 0.35, 1.6], lookAt: [-0.1, 0.35, 0] },
    rightCamera: { position: [0.25, 0.35, 1.6], lookAt: [0.1, 0.35, 0] },
  },
};

export type BodySide = "left" | "right";

/** Get filtered chakra IDs for a bilateral region based on selected side */
export function getChakraIdsForSide(regionId: string, side: BodySide): string[] {
  const config = BILATERAL_REGIONS[regionId];
  if (!config) {
    const region = BODY_REGIONS.find((r) => r.id === regionId);
    return region?.chakraIds ?? [];
  }
  const sideIds = config.pairedChakras.map((p) =>
    side === "left" ? p.left : p.right
  );
  return [...config.centerChakraIds, ...sideIds];
}

/** Check if a region is bilateral */
export function isBilateralRegion(regionId: string): boolean {
  return regionId in BILATERAL_REGIONS;
}

export const BODY_REGIONS: BodyRegion[] = [
  {
    id: "head-neck",
    name: "Head & Neck",
    icon: "🧠",
    chakraIds: [
      "CROWN", "FOREHEAD", "AJNA", "THROAT",
      "JAW_L", "JAW_R", "TEMPLE_L", "TEMPLE_R",
      "EAR_L", "EAR_R", "EYE_L", "EYE_R",
    ],
    cameraPosition: [0, 1.65, 1.2],
    cameraLookAt: [0, 1.65, 0],
    regionDotPosition: [0, 1.7, 0.2],
  },
  {
    id: "upper-torso",
    name: "Upper Torso",
    icon: "💚",
    chakraIds: [
      "HEART_F", "HEART_B", "SOLAR_F", "SOLAR_B",
      "ARMPIT_L", "ARMPIT_R", "NIPPLE_L", "NIPPLE_R",
    ],
    cameraPosition: [0, 1.2, 1.5],
    cameraLookAt: [0, 1.2, 0],
    regionDotPosition: [0, 1.2, 0.2],
  },
  {
    id: "lower-torso",
    name: "Lower Torso",
    icon: "🟠",
    chakraIds: ["SPLEEN_F", "SPLEEN_B", "NAVEL", "MENG_MEIN", "NAVEL_MINOR"],
    cameraPosition: [0, 0.85, 1.3],
    cameraLookAt: [0, 0.85, 0],
    regionDotPosition: [0, 0.85, 0.2],
  },
  {
    id: "pelvis-legs",
    name: "Pelvis & Legs",
    icon: "🦵",
    chakraIds: [
      "SEX", "BASIC",
      "HIP_L", "HIP_R", "KNEE_L", "KNEE_R",
      "FOOT_L", "FOOT_R", "TOE_L", "TOE_R",
      "PERINEUM",
    ],
    cameraPosition: [0, 0.35, 1.8],
    cameraLookAt: [0, 0.35, 0],
    regionDotPosition: [0, 0.4, 0.2],
  },
  {
    id: "hands-arms",
    name: "Hands & Arms",
    icon: "🤲",
    chakraIds: [
      "SHOULDER_L", "SHOULDER_R",
      "ELBOW_L", "ELBOW_R",
      "HAND_L", "HAND_R",
      "FINGER_L", "FINGER_R",
    ],
    cameraPosition: [0, 1.1, 2.8],
    cameraLookAt: [0, 1.1, 0],
    regionDotPosition: [0.7, 1.2, 0.1],
  },
];

export const OVERVIEW_CAMERA = {
  position: [0, 0.9, 3.2] as [number, number, number],
  lookAt: [0, 0.9, 0] as [number, number, number],
};

export function getRegionById(id: string) {
  return BODY_REGIONS.find((r) => r.id === id);
}
