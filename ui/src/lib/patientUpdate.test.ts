import { describe, it, expect } from "vitest";
import { buildPatientUpdate } from "./patientUpdate";

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
