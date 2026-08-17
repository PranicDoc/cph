import { describe, it, expect } from "vitest";
import { formatConsentTypeLabel } from "./consentLabels";

describe("formatConsentTypeLabel", () => {
  it("formats all four system-defined keys to their exact display strings", () => {
    expect(formatConsentTypeLabel("TERMS_AND_CONDITIONS")).toBe("Terms and Conditions");
    expect(formatConsentTypeLabel("PRIVACY_POLICY")).toBe("Privacy Policy");
    expect(formatConsentTypeLabel("MEDICAL_DISCLAIMER")).toBe("Medical Disclaimer");
    expect(formatConsentTypeLabel("PRACTITIONER_AGREEMENT")).toBe("Practitioner Agreement");
  });

  it("formats a dynamic admin-created lowercase-underscore key", () => {
    expect(formatConsentTypeLabel("ai_data_processing")).toBe("Ai Data Processing");
  });

  it("handles a hyphenated key", () => {
    expect(formatConsentTypeLabel("cookie-policy")).toBe("Cookie Policy");
  });

  it("returns an empty string unchanged", () => {
    expect(formatConsentTypeLabel("")).toBe("");
  });

  it("returns a whitespace-only string unchanged", () => {
    expect(formatConsentTypeLabel("   ")).toBe("   ");
  });
});
