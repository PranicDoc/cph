// chakraPositions.ts

export type ChakraId = string; // id from API or name as key

export const chakraPositions: Record<string, [number, number, number]> = {
  // Head region
  "Crown Chakra": [0, 4.2, 0],
  "Forehead Chakra": [0, 3.8, 0.4],
  "Ajna Chakra": [0, 3.6, 0.6],
  "Left Jaw Minor Chakra": [-0.6, 3.2, 0.5],
  "Right Jaw Minor Chakra": [0.6, 3.2, 0.5],

  // Throat
  "Throat Chakra": [0, 2.8, 0.7],

  // Chest
  "Front Heart Chakra": [0, 2.2, 1.2],
  "Back Heart Chakra": [0, 2.2, -1.2],
  "Front Solar Plexus Chakra": [0, 1.6, 1.0],
  "Back Solar Plexus Chakra": [0, 1.6, -1.0],

  // Abdomen / Pelvis
  "Navel Chakra": [0, 1.0, 0.8],
  "Meng Mein Chakra": [0, 1.0, -0.8],
  "Sex Chakra": [0, 0.4, 0.6],
  "Basic Chakra": [0, -0.2, 0.4],
  "Front Spleen Chakra": [-0.8, 1.2, 0.5],
  "Back Spleen Chakra": [-0.8, 1.2, -0.5],
  "Left Hip Minor Chakra": [-1.2, 0.2, 0],
  "Right Hip Minor Chakra": [1.2, 0.2, 0],

  // Arms
  "Left Shoulder Minor Chakra": [-1.4, 2.4, 0],
  "Right Shoulder Minor Chakra": [1.4, 2.4, 0],
  "Left Elbow Minor Chakra": [-1.8, 1.4, 0],
  "Right Elbow Minor Chakra": [1.8, 1.4, 0],
  "Left Hand Chakra": [-2.2, 0.6, 0],
  "Right Hand Chakra": [2.2, 0.6, 0],

  // Legs
  "Left Knee Minor Chakra": [-0.8, -1.2, 0],
  "Right Knee Minor Chakra": [0.8, -1.2, 0],
  "Left Sole Chakra": [-0.8, -2.6, 0.2],
  "Right Sole Chakra": [0.8, -2.6, 0.2],
};

// For grouping by region – same as earlier but using chakra names
export const regionGroups = {
  Head: [
    "Crown Chakra",
    "Forehead Chakra",
    "Ajna Chakra",
    "Left Jaw Minor Chakra",
    "Right Jaw Minor Chakra",
  ],
  Throat: ["Throat Chakra"],
  Chest: [
    "Front Heart Chakra",
    "Back Heart Chakra",
    "Front Solar Plexus Chakra",
    "Back Solar Plexus Chakra",
  ],
  AbdomenPelvis: [
    "Navel Chakra",
    "Meng Mein Chakra",
    "Sex Chakra",
    "Basic Chakra",
    "Front Spleen Chakra",
    "Back Spleen Chakra",
    "Left Hip Minor Chakra",
    "Right Hip Minor Chakra",
  ],
  Arms: [
    "Left Shoulder Minor Chakra",
    "Right Shoulder Minor Chakra",
    "Left Elbow Minor Chakra",
    "Right Elbow Minor Chakra",
    "Left Hand Chakra",
    "Right Hand Chakra",
  ],
  Legs: [
    "Left Knee Minor Chakra",
    "Right Knee Minor Chakra",
    "Left Sole Chakra",
    "Right Sole Chakra",
  ],
};
