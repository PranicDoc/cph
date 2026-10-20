export type ValidityUnit = "HOURS" | "DAYS" | "MONTHS" | "YEARS";

export const VALIDITY_UNITS: { value: ValidityUnit; label: string }[] = [
  { value: "HOURS", label: "Hours" },
  { value: "DAYS", label: "Days" },
  { value: "MONTHS", label: "Months" },
  { value: "YEARS", label: "Years" },
];

/** "30 days", "1 year"; "No expiry" when the package has no validity. */
export function formatValidity(
  value: number | null | undefined,
  unit: ValidityUnit | null | undefined
): string {
  if (!value || !unit) return "No expiry";
  const singular = unit.toLowerCase().slice(0, -1);
  return `${value} ${value === 1 ? singular : singular + "s"}`;
}

/** Date + time a plan ends, or a dash when the account has none. */
export function formatPlanEnd(iso: string | null | undefined): string {
  return iso ? new Date(iso).toLocaleString() : "—";
}
