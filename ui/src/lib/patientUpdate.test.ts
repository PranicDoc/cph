import { describe, it, expect } from "vitest";
import { buildMedicalHistoryUpdate, buildPatientUpdate } from "./patientUpdate";

describe("buildPatientUpdate", () => {
  it("trims text, maps contact to contactWhatsapp and sends an empty text to clear an optional field", () => {
    const body = buildPatientUpdate({
      firstName: "  Asha ",
      lastName: "Rao",
      dob: "1990-04-02",
      gender: "F",
      maritalStatus: null,
      email: null,
      contact: " +911111111111 ",
      occupation: "",
      city: "Pune",
    });

    expect(body.firstName).toBe("Asha");
    expect(body.contactWhatsapp).toBe("+911111111111");
    expect(body.email).toBe("");
    expect(body.occupation).toBe("");
    expect(body.city).toBe("Pune");
    expect(body.dob).toBe("1990-04-02");
    expect(body.gender).toBe("F");
    expect(body.maritalStatus).toBeUndefined();
  });
});

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
