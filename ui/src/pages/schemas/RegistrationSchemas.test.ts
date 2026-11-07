import { describe, it, expect } from "vitest";
import { consentDeclarationSchema, patientEditSchema, patientMedicalHistorySchema } from "./RegistrationSchemas";

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

  it("asks a healer for the declaration date, but not a patient registering themselves", () => {
    const date = fieldsOf(consentDeclarationSchema("Asha Rao", { onBehalf: true })).find((f) => f.name === "declarationDate");
    expect(date?.type).toBe("date");
    expect(date?.required).toBe(true);

    expect(fieldsOf(consentDeclarationSchema("Asha Rao", { onBehalf: false })).some((f) => f.name === "declarationDate")).toBe(false);
  });
});

describe("patientEditSchema", () => {
  it("puts the declaration date straight after the exercise fields, and only here", () => {
    const names = patientEditSchema({ required: true, readOnly: false }).flatMap((g) => g.fields).map((f) => f.name);

    expect(names.slice(names.indexOf("exercise"), names.indexOf("exercise") + 3)).toEqual([
      "exercise",
      "exerciseFrequency",
      "declarationDate",
    ]);
    expect(patientMedicalHistorySchema.flatMap((g) => g.fields).some((f) => f.name === "declarationDate")).toBe(false);
  });

  it("marks the field read-only while the case is locked", () => {
    const date = patientEditSchema({ required: false, readOnly: true })
      .flatMap((g) => g.fields)
      .find((f) => f.name === "declarationDate");

    expect(date?.readOnly).toBe(true);
    expect(date?.required).toBe(false);
    expect(date?.label).toMatch(/read-only/);
  });
});

describe("General Health options", () => {
  it("keeps Fair for records already saved with it, but hides it from new picks", () => {
    const options = patientMedicalHistorySchema.flatMap((g) => g.fields).find((f) => f.name === "generalHealth")?.options;

    expect(options?.find((o) => o.value === "FAIR")).toMatchObject({ label: "Fair", hidden: true });
    expect(options?.filter((o) => !o.hidden).map((o) => o.value)).toEqual(["EXCELLENT", "GOOD", "POOR"]);
  });
});
