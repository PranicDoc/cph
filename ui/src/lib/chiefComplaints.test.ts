import { describe, expect, it } from "vitest";
import {
  CHIEF_COMPLAINT_REQUIRED,
  MAX_CHIEF_COMPLAINTS,
  type ChiefComplaintRow,
  chiefComplaintsError,
  chiefComplaintsPayload,
  complaintLine,
  complaintRowsFromResponse,
  durationError,
  emptyComplaintRow,
  formatDuration,
  hasProblemHistory,
  isCompleteRow,
  periodError,
} from "./chiefComplaints";

const row = (problem: string, durationValue: string | number, durationUnit: ChiefComplaintRow["durationUnit"]): ChiefComplaintRow =>
  ({ problem, durationValue, durationUnit });

describe("chiefComplaintsError", () => {
  it("needs at least one problem", () => {
    expect(chiefComplaintsError(undefined)).toBe(CHIEF_COMPLAINT_REQUIRED);
    expect(chiefComplaintsError([emptyComplaintRow()])).toBe(CHIEF_COMPLAINT_REQUIRED);
    expect(chiefComplaintsError([row("   ", 3, "DAYS")])).toBe(CHIEF_COMPLAINT_REQUIRED);
  });

  it("skips a blank problem even when it has a duration", () => {
    expect(chiefComplaintsError([row("Headache", 3, "MONTHS"), row("", 5, "DAYS")])).toBeUndefined();
  });

  it("names a problem that has no duration or no period", () => {
    expect(chiefComplaintsError([row("Knee pain", "", "DAYS")])).toContain("Knee pain");
    expect(chiefComplaintsError([row("Knee pain", 2, "")])).toContain("Knee pain");
    expect(chiefComplaintsError([row("Knee pain", 0, "DAYS")])).toContain("Knee pain");
  });

  it("allows ten problems and refuses eleven", () => {
    const ten = Array.from({ length: MAX_CHIEF_COMPLAINTS }, (_, i) => row(`Problem ${i}`, 1, "DAYS"));
    expect(chiefComplaintsError(ten)).toBeUndefined();
    expect(chiefComplaintsError([...ten, row("One more", 1, "DAYS")])).toContain("10");
  });
});

describe("row messages", () => {
  it("only flag a row whose problem is filled in", () => {
    expect(durationError(row("", "", ""))).toBeUndefined();
    expect(periodError(row("", "", ""))).toBeUndefined();
    expect(durationError(row("Pain", "", "DAYS"))).toBe("Enter a duration");
    expect(periodError(row("Pain", 2, ""))).toBe("Select a period");
    expect(isCompleteRow(row("Pain", 2, "DAYS"))).toBe(true);
  });
});

describe("chiefComplaintsPayload", () => {
  it("sends only filled rows, with numbers and trimmed text", () => {
    const rows: ChiefComplaintRow[] = [
      { id: "a", problem: " Headache ", durationValue: "3", durationUnit: "MONTHS",
        problemHistory: { onset: "GRADUAL", location: " Forehead ", timing: "PATTERN", timingPattern: " after 3 pm " } },
      row("", "", ""),
    ];
    const payload = chiefComplaintsPayload(rows);
    expect(payload).toHaveLength(1);
    expect(payload[0]).toMatchObject({
      id: "a", problem: "Headache", durationValue: 3, durationUnit: "MONTHS",
      problemHistory: { onset: "GRADUAL", location: "Forehead", timing: "PATTERN", timingPattern: "after 3 pm" },
    });
  });

  it("drops the pattern text unless timing is pattern", () => {
    const [p] = chiefComplaintsPayload([
      { problem: "Pain", durationValue: 1, durationUnit: "DAYS", problemHistory: { timing: "CONSTANT", timingPattern: "stale" } },
    ]);
    expect(p.problemHistory.timingPattern).toBeNull();
  });
});

describe("complaintRowsFromResponse", () => {
  it("always leaves a row to type in", () => {
    expect(complaintRowsFromResponse([])).toEqual([emptyComplaintRow()]);
    expect(complaintRowsFromResponse(undefined)).toEqual([emptyComplaintRow()]);
  });

  it("marks a row without a duration as legacy and gathers the flat history columns", () => {
    const rows = complaintRowsFromResponse([
      { id: "1", problem: "Back pain", durationValue: null, durationUnit: null },
      { id: "2", problem: "Headache", durationValue: 3, durationUnit: "MONTHS", onset: "SUDDEN", timing: "PATTERN", timingPattern: "mornings" },
    ]);
    expect(rows[0].legacy).toBe(true);
    expect(rows[1].legacy).toBe(false);
    expect(rows[1].problemHistory).toMatchObject({ onset: "SUDDEN", timing: "PATTERN", timingPattern: "mornings" });
  });
});

describe("wording", () => {
  it("formats durations and complaint lines", () => {
    expect(formatDuration(3, "MONTHS")).toBe("3 months");
    expect(formatDuration(1, "DAYS")).toBe("1 day");
    expect(formatDuration(null, "DAYS")).toBe("");
    expect(complaintLine({ problem: "Headache", durationValue: 3, durationUnit: "MONTHS" })).toBe("Headache since 3 months");
    expect(complaintLine({ problem: "Back pain" })).toBe("Back pain");
  });

  it("knows whether a problem has any presenting-illness answer", () => {
    expect(hasProblemHistory(null)).toBe(false);
    expect(hasProblemHistory({ location: "  " })).toBe(false);
    expect(hasProblemHistory({ onset: "SUDDEN" })).toBe(true);
  });
});
