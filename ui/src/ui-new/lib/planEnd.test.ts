import { describe, expect, it } from "vitest";
import { planEndLine } from "./planEnd";

const now = new Date("2026-10-20T10:00:00");

describe("planEndLine", () => {
  it("shows the end date and the days left", () => {
    expect(planEndLine("2026-11-18T09:00:00", now)).toBe("Plan ends 18 Nov 2026 · in 29 days");
  });
  it("says tomorrow and today for the last days", () => {
    expect(planEndLine("2026-10-21T08:00:00", now)).toBe("Plan ends 21 Oct 2026 · tomorrow");
    expect(planEndLine("2026-10-20T23:00:00", now)).toBe("Plan ends 20 Oct 2026 · today");
  });
  it("says ended for a past date and nothing without a date", () => {
    expect(planEndLine("2026-10-01T08:00:00", now)).toBe("Plan ended 1 Oct 2026");
    expect(planEndLine(null, now)).toBe("No end date");
  });
});
