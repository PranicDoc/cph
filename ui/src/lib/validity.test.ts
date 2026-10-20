import { describe, it, expect } from "vitest";
import { formatValidity, formatPlanEnd } from "./validity";

describe("formatValidity", () => {
  it("pluralises the unit", () => {
    expect(formatValidity(30, "DAYS")).toBe("30 days");
    expect(formatValidity(1, "YEARS")).toBe("1 year");
    expect(formatValidity(6, "MONTHS")).toBe("6 months");
    expect(formatValidity(36, "HOURS")).toBe("36 hours");
  });

  it("says no expiry when nothing is set", () => {
    expect(formatValidity(null, null)).toBe("No expiry");
    expect(formatValidity(undefined, undefined)).toBe("No expiry");
  });
});

describe("formatPlanEnd", () => {
  it("shows a dash for accounts without a plan end", () => {
    expect(formatPlanEnd(null)).toBe("—");
  });

  it("shows the date for a plan end in the future", () => {
    expect(formatPlanEnd("2099-01-02T10:00:00Z")).toMatch(/2099/);
  });
});
