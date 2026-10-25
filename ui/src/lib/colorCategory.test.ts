import { describe, expect, it } from "vitest";
import { findColorInCategory, hasColorCategory } from "./colorCategory";

describe("hasColorCategory", () => {
  it("matches only the color's own category", () => {
    expect(hasColorCategory({ category: "CLEANSING" }, "CLEANSING")).toBe(true);
    expect(hasColorCategory({ category: "CLEANSING" }, "ENERGISING")).toBe(false);
  });

  it("keeps uncategorised colors available everywhere", () => {
    expect(hasColorCategory({}, "CLEANSING")).toBe(true);
    expect(hasColorCategory({ category: null }, "ENERGISING")).toBe(true);
  });
});

describe("findColorInCategory", () => {
  const colors = [
    { name: "Green", category: "ENERGISING" as const, properties: ["Energizing"] },
    { name: "Green", category: "CLEANSING" as const, properties: ["Cleansing"] },
    { name: "Violet", category: null, properties: ["All-purpose"] },
  ];

  it("picks the same-named row of the requested category", () => {
    expect(findColorInCategory(colors, "Green", "CLEANSING")?.properties).toEqual(["Cleansing"]);
    expect(findColorInCategory(colors, "Green", "ENERGISING")?.properties).toEqual(["Energizing"]);
  });

  it("falls back to the name alone", () => {
    expect(findColorInCategory(colors, "Violet", "CLEANSING")?.properties).toEqual(["All-purpose"]);
    expect(findColorInCategory(colors, "Blue", "CLEANSING")).toBeUndefined();
  });
});
