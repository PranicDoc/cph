import { describe, expect, it } from "vitest";
import { endsSoon, relativeFromNow, shortDate } from "./format";

const now = new Date("2026-10-20T00:00:00Z").getTime();
const at = (d: number) => new Date(now + d * 86_400_000).toISOString();

describe("format helpers", () => {
  it("shows days, months and past dates relatively", () => {
    expect(relativeFromNow(at(29), now)).toBe("in 29 days");
    expect(relativeFromNow(at(210), now)).toBe("in 7 months");
    expect(relativeFromNow(at(-3), now)).toBe("3 days ago");
    expect(relativeFromNow(null, now)).toBe("");
  });
  it("flags a plan ending within a week", () => {
    expect(endsSoon(at(3), now)).toBe(true);
    expect(endsSoon(at(29), now)).toBe(false);
    expect(endsSoon(at(-1), now)).toBe(false);
  });
  it("formats a short date", () => {
    expect(shortDate("2026-11-18T10:00:00Z")).toBe("18 Nov 2026");
    expect(shortDate(null)).toBe("—");
  });
});
