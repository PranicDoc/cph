/**
 * The chief complaint as problem/duration rows, with the history of presenting illness of each problem.
 * The rules mirror the server's ChiefComplaintRules: keep the two in step.
 */

export const MAX_CHIEF_COMPLAINTS = 10;
export const MAX_DURATION = 999;

export type DurationUnit = "HOURS" | "DAYS" | "MONTHS" | "YEARS";
export type Onset = "SUDDEN" | "GRADUAL";
export type Timing = "CONSTANT" | "INTERMITTENT" | "PATTERN";

/** In the order the form offers them. */
export const DURATION_UNITS: { value: DurationUnit; label: string }[] = [
  { value: "DAYS", label: "Days" },
  { value: "HOURS", label: "Hours" },
  { value: "MONTHS", label: "Months" },
  { value: "YEARS", label: "Years" },
];

export type ProblemHistory = {
  onset?: Onset | null;
  location?: string | null;
  characterText?: string | null;
  aggravatingFactor?: string | null;
  relievingFactor?: string | null;
  timing?: Timing | null;
  timingPattern?: string | null;
};

export type ChiefComplaintRow = {
  id?: string;
  problem: string;
  /** What the number input holds: text while typing, a number from the server. */
  durationValue: string | number | null;
  durationUnit: DurationUnit | "" | null;
  /** A row migrated from the old free-text complaint: shown as saved, completed on the next edit. */
  legacy?: boolean;
  problemHistory?: ProblemHistory | null;
};

export const emptyComplaintRow = (): ChiefComplaintRow => ({ problem: "", durationValue: "", durationUnit: "" });

export const CHIEF_COMPLAINT_REQUIRED = "Chief complaint is required";
export const DURATION_REQUIRED = "Enter a duration";
export const PERIOD_REQUIRED = "Select a period";

const durationNumber = (row: ChiefComplaintRow): number => {
  const n = Number(row.durationValue);
  return Number.isFinite(n) ? n : 0;
};

/** A row counts only when its problem is filled in; a blank problem is skipped, duration or not. */
export const isFilledRow = (row: ChiefComplaintRow): boolean => (row.problem ?? "").trim().length > 0;

export const hasDuration = (row: ChiefComplaintRow): boolean => {
  const n = durationNumber(row);
  return Number.isInteger(n) && n >= 1 && n <= MAX_DURATION;
};

/** A filled row with both duration and period: the only kind that can be saved. */
export const isCompleteRow = (row: ChiefComplaintRow): boolean =>
  isFilledRow(row) && hasDuration(row) && !!row.durationUnit;

export function durationError(row: ChiefComplaintRow): string | undefined {
  if (!isFilledRow(row)) return undefined;
  if (!hasDuration(row)) return DURATION_REQUIRED;
  return undefined;
}

export function periodError(row: ChiefComplaintRow): string | undefined {
  if (!isFilledRow(row)) return undefined;
  return row.durationUnit ? undefined : PERIOD_REQUIRED;
}

/** The message for the whole table, or undefined when it may be saved. */
export function chiefComplaintsError(rows: ChiefComplaintRow[] | null | undefined): string | undefined {
  const filled = (rows ?? []).filter(isFilledRow);
  if (filled.length === 0) return CHIEF_COMPLAINT_REQUIRED;
  if (filled.length > MAX_CHIEF_COMPLAINTS) return `You can add up to ${MAX_CHIEF_COMPLAINTS} problems`;
  const incomplete = filled.find((r) => !isCompleteRow(r));
  if (incomplete) return `Enter how long you have had "${incomplete.problem.trim()}"`;
  return undefined;
}

export const hasChiefComplaint = (rows: ChiefComplaintRow[] | null | undefined): boolean =>
  (rows ?? []).some(isFilledRow);

const blankToNull = (v: string | null | undefined) => (v && v.trim() ? v.trim() : null);

/** What the case update sends: only the filled rows, with numbers as numbers and the history nested. */
export function chiefComplaintsPayload(rows: ChiefComplaintRow[] | null | undefined) {
  return (rows ?? []).filter(isFilledRow).map((r) => {
    const h = r.problemHistory ?? {};
    return {
      id: r.id ?? null,
      problem: r.problem.trim(),
      durationValue: hasDuration(r) ? durationNumber(r) : null,
      durationUnit: r.durationUnit || null,
      problemHistory: {
        onset: h.onset ?? null,
        location: blankToNull(h.location),
        characterText: blankToNull(h.characterText),
        aggravatingFactor: blankToNull(h.aggravatingFactor),
        relievingFactor: blankToNull(h.relievingFactor),
        timing: h.timing ?? null,
        timingPattern: h.timing === "PATTERN" ? blankToNull(h.timingPattern) : null,
      },
    };
  });
}

/** The server's rows (flat history columns) as form rows, always ending with at least one row to type in. */
export function complaintRowsFromResponse(list: ChiefComplaintDetails[] | null | undefined): ChiefComplaintRow[] {
  const rows: ChiefComplaintRow[] = (list ?? []).map((c) => ({
    id: c.id,
    problem: c.problem ?? "",
    durationValue: c.durationValue ?? "",
    durationUnit: (c.durationUnit ?? "") as ChiefComplaintRow["durationUnit"],
    legacy: c.legacy === true || c.durationValue == null || !c.durationUnit,
    problemHistory: {
      onset: (c.onset ?? null) as Onset | null,
      location: c.location ?? null,
      characterText: c.characterText ?? null,
      aggravatingFactor: c.aggravatingFactor ?? null,
      relievingFactor: c.relievingFactor ?? null,
      timing: (c.timing ?? null) as Timing | null,
      timingPattern: c.timingPattern ?? null,
    },
  }));
  return rows.length > 0 ? rows : [emptyComplaintRow()];
}

/** "3 months", "1 day". */
export function formatDuration(value: number | string | null | undefined, unit: string | null | undefined): string {
  const n = Number(value);
  if (!unit || !Number.isFinite(n) || n < 1) return "";
  const word = unit.toLowerCase().replace(/s$/, "");
  return `${n} ${word}${n === 1 ? "" : "s"}`;
}

/** "Headache since 3 months"; a legacy row is the problem alone. */
export function complaintLine(c: { problem?: string; durationValue?: number | string | null; durationUnit?: string | null }): string {
  const duration = formatDuration(c.durationValue, c.durationUnit);
  return duration ? `${c.problem ?? ""} since ${duration}` : (c.problem ?? "");
}

/** Whether a problem has any presenting-illness answer, for the "n filled" counter. */
export function hasProblemHistory(h: ProblemHistory | null | undefined): boolean {
  if (!h) return false;
  return !!(h.onset || h.timing || blankToNull(h.location) || blankToNull(h.characterText)
    || blankToNull(h.aggravatingFactor) || blankToNull(h.relievingFactor) || blankToNull(h.timingPattern));
}

/** A chief-complaint row as the case endpoints return it (history columns are flat). */
export type ChiefComplaintDetails = {
  id?: string;
  problem: string;
  durationValue?: number | null;
  durationUnit?: string | null;
  legacy?: boolean;
  onset?: string | null;
  location?: string | null;
  characterText?: string | null;
  aggravatingFactor?: string | null;
  relievingFactor?: string | null;
  timing?: string | null;
  timingPattern?: string | null;
};

/** "162 cm, 71 kg" or "Not provided". */
export function heightWeightText(heightCm?: number | null, weightKg?: number | null): string {
  if (heightCm == null && weightKg == null) return "Not provided";
  return [heightCm != null ? `${heightCm} cm` : null, weightKg != null ? `${weightKg} kg` : null]
    .filter(Boolean)
    .join(", ");
}
