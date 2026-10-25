export type ColorCategory = "CLEANSING" | "ENERGISING";

export const COLOR_CATEGORIES: ColorCategory[] = ["CLEANSING", "ENERGISING"];

export const COLOR_CATEGORY_LABELS: Record<ColorCategory, string> = {
  CLEANSING: "Cleansing",
  ENERGISING: "Energising",
};

/**
 * Whether a prana color belongs to a category. Each color row has one category (a color used
 * both ways is two rows). A color with no category yet (created before categories existed and
 * not backfilled) stays available everywhere, so pickers never go blank while the one-time
 * backfill is pending.
 */
export function hasColorCategory(
  color: { category?: ColorCategory | null },
  category: ColorCategory
): boolean {
  return !color.category || color.category === category;
}

/**
 * The row for a color name within a category: the same name can be one color per category,
 * each with its own properties. Falls back to any row with that name (uncategorised colors).
 */
export function findColorInCategory<T extends { name: string; category?: ColorCategory | null }>(
  colors: T[],
  name: string,
  category: ColorCategory
): T | undefined {
  return (
    colors.find((c) => c.name === name && c.category === category) ??
    colors.find((c) => c.name === name)
  );
}
