import { describe, it, expect } from "vitest";
import { buildMedicalHistoryUpdate } from "./patientUpdate";

describe("buildMedicalHistoryUpdate", () => {
  it("stores exercise as one string and sends the whole form", () => {
    const body = buildMedicalHistoryUpdate({
      generalHealth: "GOOD",
      diet: " MIXED ",
      exercise: ["YOGA", "WALKING"],
      exerciseFrequency: "DAILY",
      hypertension: true,
      additionalNotes: "",
    });

    expect(body.exercise).toBe("YOGA,WALKING");
    expect(body.diet).toBe("MIXED");
    expect(body.hypertension).toBe(true);
    expect(body.giIssues).toBe(false);
    expect(body.additionalNotes).toBeNull();
  });

  it("drops the cancer details when cancer is not ticked, and keeps them when it is", () => {
    const off = buildMedicalHistoryUpdate({ cancer: false, cancerLocation: "Lung", cancerDetails: "Stage 1" });
    expect(off.cancerLocation).toBeNull();
    expect(off.cancerDetails).toBeNull();

    const on = buildMedicalHistoryUpdate({ cancer: true, cancerLocation: "Lung", cancerDetails: "Stage 1" });
    expect(on.cancerLocation).toBe("Lung");
    expect(on.cancerDetails).toBe("Stage 1");
  });
});

describe("height, weight and the four histories", () => {
  it("sends height and weight as numbers, empty boxes as null, and trims the histories", () => {
    const body = buildMedicalHistoryUpdate({
      heightCm: "162", weightKg: " 71.5 ", pastHistory: " Typhoid 2016 ", surgicalHistory: "  ",
      familyHistory: "Father: diabetes", psychosocialHistory: null,
    });
    expect(body.heightCm).toBe(162);
    expect(body.weightKg).toBe(71.5);
    expect(body.pastHistory).toBe("Typhoid 2016");
    expect(body.surgicalHistory).toBeNull();
    expect(body.familyHistory).toBe("Father: diabetes");
    expect(body.psychosocialHistory).toBeNull();

    const empty = buildMedicalHistoryUpdate({ heightCm: "", weightKg: null });
    expect(empty.heightCm).toBeNull();
    expect(empty.weightKg).toBeNull();
  });
});
