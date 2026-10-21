import { describe, expect, it } from "vitest";
import { barPercent } from "./RankedCard";

describe("barPercent", () => {
  it("fills the top row and scales the rest", () => {
    expect(barPercent(46, 46)).toBe(100);
    expect(barPercent(23, 46)).toBe(50);
  });
  it("keeps tiny counts visible and handles empty data", () => {
    expect(barPercent(1, 500)).toBe(4);
    expect(barPercent(0, 10)).toBe(0);
    expect(barPercent(5, 0)).toBe(0);
  });
});
