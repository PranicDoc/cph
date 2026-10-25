import { describe, expect, it } from "vitest";
import { hasColorCategory } from "./colorCategory";

describe("hasColorCategory", () => {
  it("matches a color tagged with the category", () => {
    expect(hasColorCategory({ categories: ["CLEANSING"] }, "CLEANSING")).toBe(true);
    expect(hasColorCategory({ categories: ["CLEANSING"] }, "ENERGISING")).toBe(false);
  });

  it("matches both categories for a color tagged with both", () => {
    const both = { categories: ["CLEANSING", "ENERGISING"] as const };
    expect(hasColorCategory({ categories: [...both.categories] }, "CLEANSING")).toBe(true);
    expect(hasColorCategory({ categories: [...both.categories] }, "ENERGISING")).toBe(true);
  });

  it("keeps uncategorised colors available everywhere", () => {
    expect(hasColorCategory({}, "CLEANSING")).toBe(true);
    expect(hasColorCategory({ categories: [] }, "ENERGISING")).toBe(true);
  });
});
