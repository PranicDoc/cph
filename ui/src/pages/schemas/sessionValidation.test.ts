import { describe, it, expect } from "vitest";
import {
  sessionStep0Errors,
  CHIEF_COMPLAINT_REQUIRED,
  PROTOCOL_REQUIRED,
} from "./sessionValidation";

const complete = {
  medicalCondition: "Neck pain for 5 days",
  healingDetails: { selectedProtocols: [{ id: "p1" }], detailedProtocol: "" },
};

describe("sessionStep0Errors", () => {
  it("returns no errors when chief complaint and a protocol are present", () => {
    expect(sessionStep0Errors(complete)).toEqual({});
  });

  it("flags a whitespace-only chief complaint", () => {
    expect(
      sessionStep0Errors({ ...complete, medicalCondition: "   " })
    ).toEqual({ medicalCondition: CHIEF_COMPLAINT_REQUIRED });
  });

  it("flags a missing chief complaint", () => {
    expect(sessionStep0Errors({ healingDetails: complete.healingDetails })).toEqual({
      medicalCondition: CHIEF_COMPLAINT_REQUIRED,
    });
  });

  it("flags missing protocol when neither a selected protocol nor detailed text is present", () => {
    expect(
      sessionStep0Errors({
        medicalCondition: "Neck pain",
        healingDetails: { selectedProtocols: [], detailedProtocol: "  " },
      })
    ).toEqual({ "healingDetails.selectedProtocols": PROTOCOL_REQUIRED });
  });

  it("accepts free-text detailed protocol in place of a selected protocol", () => {
    expect(
      sessionStep0Errors({
        medicalCondition: "Neck pain",
        healingDetails: { selectedProtocols: [], detailedProtocol: "Custom sweep protocol" },
      })
    ).toEqual({});
  });

  it("reports both errors on a fully empty form", () => {
    expect(sessionStep0Errors({})).toEqual({
      medicalCondition: CHIEF_COMPLAINT_REQUIRED,
      "healingDetails.selectedProtocols": PROTOCOL_REQUIRED,
    });
  });
});
