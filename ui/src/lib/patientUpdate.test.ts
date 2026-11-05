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
