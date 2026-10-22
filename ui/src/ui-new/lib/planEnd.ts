const DAY = 24 * 60 * 60 * 1000;

function startOfDay(d: Date): number {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
}

/** "Plan ends 18 Nov 2026 · in 29 days", counted in calendar days. */
export function planEndLine(iso: string | null | undefined, now: Date = new Date()): string {
  if (!iso) return "No end date";
  const end = new Date(iso);
  const date = end.toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" });
  const days = Math.round((startOfDay(end) - startOfDay(now)) / DAY);
  if (days < 0) return `Plan ended ${date}`;
  const left = days === 0 ? "today" : days === 1 ? "tomorrow" : `in ${days} days`;
  return `Plan ends ${date} · ${left}`;
}
