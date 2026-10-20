import { describe, it, expect } from "vitest";
import { consentDeclarationSchema } from "./RegistrationSchemas";

const fieldsOf = (schema: ReturnType<typeof consentDeclarationSchema>) => schema.flatMap((g) => g.fields);

describe("consentDeclarationSchema", () => {
  it("for a healer registering the patient: asks for the healer's consent confirmation and fixes the signature to the patient's name", () => {
    const fields = fieldsOf(consentDeclarationSchema("Asha Rao", { onBehalf: true }));

    const attest = fields.find((f) => f.name === "healerConsentAttestation");
    expect(attest?.type).toBe("checkbox");
    expect(attest?.required).toBe(true);
    expect(attest?.label).toMatch(/consent from the patient|patient's consent/i);

    const signature = fields.find((f) => f.name === "patientSignature");
    expect(signature?.readOnly).toBe(true);
    expect(signature?.validate?.("Asha Rao", {}, undefined)).toBeUndefined();
    expect(signature?.validate?.("Someone", {}, undefined)).toBe("Signature mismatch");
  });

  it("for a patient registering themselves: no healer confirmation, signature typed by the patient", () => {
    const fields = fieldsOf(consentDeclarationSchema("Asha Rao", { onBehalf: false }));

    expect(fields.find((f) => f.name === "healerConsentAttestation")).toBeUndefined();
    expect(fields.find((f) => f.name === "patientSignature")?.readOnly).toBeFalsy();
  });

  it("defaults to the patient's own flow", () => {
    expect(fieldsOf(consentDeclarationSchema("Asha Rao")).some((f) => f.name === "healerConsentAttestation")).toBe(false);
  });
});
