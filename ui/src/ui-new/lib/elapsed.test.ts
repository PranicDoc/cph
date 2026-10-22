import { describe, expect, it } from "vitest";
import { formatElapsed } from "./elapsed";

describe("formatElapsed", () => {
  it("pads minutes and seconds", () => {
    expect(formatElapsed(0)).toBe("00:00");
    expect(formatElapsed(12)).toBe("00:12");
    expect(formatElapsed(75)).toBe("01:15");
  });
  it("keeps counting past an hour in minutes", () => {
    expect(formatElapsed(3725)).toBe("62:05");
  });
});
