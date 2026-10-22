import { describe, expect, it } from "vitest";
import { sentenceCase } from "./sentenceCase";

describe("sentenceCase", () => {
  it("lowercases every word after the first", () => {
    expect(sentenceCase("Chief Complaint")).toBe("Chief complaint");
    expect(sentenceCase("Complementary Modalities")).toBe("Complementary modalities");
  });
  it("keeps acronyms and single words as they are", () => {
    expect(sentenceCase("PDF Template")).toBe("PDF template");
    expect(sentenceCase("Upload BP Report")).toBe("Upload BP report");
    expect(sentenceCase("Symptoms")).toBe("Symptoms");
  });
  it("keeps text in brackets untouched after the first word", () => {
    expect(sentenceCase("Date of Birth (DD/MM/YYYY)")).toBe("Date of birth (DD/MM/YYYY)");
  });
});
