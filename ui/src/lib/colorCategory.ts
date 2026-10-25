export type ColorCategory = "CLEANSING" | "ENERGISING";

export const COLOR_CATEGORIES: ColorCategory[] = ["CLEANSING", "ENERGISING"];

export const COLOR_CATEGORY_LABELS: Record<ColorCategory, string> = {
  CLEANSING: "Cleansing",
  ENERGISING: "Energising",
};

/**
 * Whether a prana color belongs to a category. A color with no categories yet (created
 * before categories existed and not backfilled) stays available everywhere, so pickers
 * never go blank while the one-time backfill is pending.
 */
export function hasColorCategory(
  color: { categories?: ColorCategory[] },
  category: ColorCategory
): boolean {
  return !color.categories?.length || color.categories.includes(category);
}
