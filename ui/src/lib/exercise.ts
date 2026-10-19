export const EXERCISE_OPTIONS = [
  { label: "None", value: "NONE" },
  { label: "Walking", value: "WALKING" },
  { label: "Yoga", value: "YOGA" },
  { label: "Gym", value: "GYM" },
  { label: "Sports", value: "SPORTS" },
] as const;

/** The backend keeps exercise as one string; several picks are stored comma-separated. */
export function serializeExercise(selected: readonly string[] | null | undefined): string | null {
  const picks = (selected ?? []).filter(Boolean);
  const real = picks.filter((v) => v !== "NONE");
  if (real.length > 0) return real.join(",");
  return picks.length > 0 ? "NONE" : null;
}

export function parseExercise(stored: string | null | undefined): string[] {
  return (stored ?? "")
    .split(",")
    .map((v) => v.trim())
    .filter(Boolean);
}

/** Display labels for a stored value; NONE yields no labels. */
export function exerciseLabels(stored: string | null | undefined): string[] {
  return parseExercise(stored)
    .filter((v) => v !== "NONE")
    .map((v) => EXERCISE_OPTIONS.find((o) => o.value === v)?.label ?? v);
}
