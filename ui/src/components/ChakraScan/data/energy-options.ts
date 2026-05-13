import { EnergyLevel, ActivationLevel } from "../types/spatial-chakra";

export interface EnergyOption {
  value: EnergyLevel;
  label: string;
  shortLabel: string;
  colorClass: string;
  colorHex: string;
}

export interface ActivationOption {
  value: ActivationLevel;
  label: string;
  shortLabel: string;
  colorClass: string;
  colorHex: string;
}

export const ENERGY_OPTIONS: EnergyOption[] = [
  { value: "N", label: "Normal", shortLabel: "N", colorClass: "text-emerald-600", colorHex: "#059669" },
  // { value: "SLD", label: "Slightly Depleted", shortLabel: "SLD", colorClass: "text-amber-600", colorHex: "#D97706" },
  { value: "D", label: "Depleted", shortLabel: "D", colorClass: "text-orange-600", colorHex: "#EA580C" },
  // { value: "SD", label: "Severely Depleted", shortLabel: "SD", colorClass: "text-red-600", colorHex: "#DC2626" },
  // { value: "SC", label: "Slightly Congested", shortLabel: "SC", colorClass: "text-amber-600", colorHex: "#D97706" },
  { value: "C", label: "Congested", shortLabel: "C", colorClass: "text-orange-600", colorHex: "#EA580C" },
  // { value: "SVC", label: "Severely Congested", shortLabel: "SVC", colorClass: "text-red-600", colorHex: "#DC2626" },
  // { value: "PCD", label: "Pranic Congestion (Dirty)", shortLabel: "PCD", colorClass: "text-stone-600", colorHex: "#57534E" },
];

export const ACTIVATION_OPTIONS: ActivationOption[] = [
  { value: "N", label: "Normal", shortLabel: "N", colorClass: "text-emerald-600", colorHex: "#059669" },
  { value: "U", label: "Underactivated", shortLabel: "U", colorClass: "text-indigo-600", colorHex: "#4F46E5" },
  { value: "O", label: "Overactivated", shortLabel: "O", colorClass: "text-violet-600", colorHex: "#7C3AED" },
];

export function getEnergyOption(value: EnergyLevel): EnergyOption {
  return ENERGY_OPTIONS.find((o) => o.value === value) ?? ENERGY_OPTIONS[0];
}

export function getActivationOption(value: ActivationLevel): ActivationOption {
  return ACTIVATION_OPTIONS.find((o) => o.value === value) ?? ACTIVATION_OPTIONS[0];
}
