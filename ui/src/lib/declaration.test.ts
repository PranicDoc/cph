import { describe, it, expect } from "vitest";
import { buildDeclaration } from "./declaration";

const base = { consent1: true, consent2: true, consent3: true, patientSignature: "Asha Rao" };

describe("buildDeclaration", () => {
  it("a healer filling in for the patient carries their consent confirmation", () => {
    const d = buildDeclaration({ ...base, healerConsentAttestation: true }, true);
    expect(d.healerDeclaration).toBe(true);
    expect(d.patientConsent).toBe(true);
    expect(d.patientSignature).toBe("Asha Rao");
  });

  it("is not marked as confirmed unless the healer ticked the box", () => {
    expect(buildDeclaration({ ...base, healerConsentAttestation: false }, true).healerDeclaration).toBe(false);
  });

  it("a patient registering themselves never sends a healer confirmation", () => {
    expect(buildDeclaration({ ...base, healerConsentAttestation: true }, false).healerDeclaration).toBe(false);
  });

  it("patient consent needs all three statements", () => {
    expect(buildDeclaration({ ...base, consent2: false }, false).patientConsent).toBe(false);
  });

  it("stamps the signing time", () => {
    expect(new Date(buildDeclaration(base, false).signedAt).getTime()).not.toBeNaN();
  });
});
