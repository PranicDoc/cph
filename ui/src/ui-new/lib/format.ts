/** "18 Nov 2026" */
export function shortDate(iso: string | null | undefined): string {
  return iso ? new Date(iso).toLocaleDateString("en-GB", { day: "2-digit", month: "short", year: "numeric" }) : "—";
}

/** "in 29 days", "in 7 months", "3 days ago"; empty for no date. */
export function relativeFromNow(iso: string | null | undefined, now: number = Date.now()): string {
  if (!iso) return "";
  const days = Math.round((new Date(iso).getTime() - now) / 86_400_000);
  if (days === 0) return "today";
  const abs = Math.abs(days);
  const years = Math.round(abs / 365);
  const unit = abs < 60 ? `${abs} day${abs === 1 ? "" : "s"}` : abs < 365 ? `${Math.round(abs / 30)} months` : `${years} year${years === 1 ? "" : "s"}`;
  return days > 0 ? `in ${unit}` : `${unit} ago`;
}

/** True when a date is less than a week away (shown in the warning colour). */
export function endsSoon(iso: string | null | undefined, now: number = Date.now()): boolean {
  if (!iso) return false;
  const days = (new Date(iso).getTime() - now) / 86_400_000;
  return days >= 0 && days < 7;
}
