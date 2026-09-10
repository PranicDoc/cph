import { describe, it, expect, beforeEach } from "vitest";

import {
  IS_FINAL_STORAGE_KEY,
  parseIsFinalParam,
  persistIsFinalFlag,
  readIsFinalFlag,
} from "./feedbackFlag";

describe("feedbackFlag", () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  it("parseIsFinalParam_absentParameter_isFalse", () => {
    const params = new URLSearchParams();

    expect(parseIsFinalParam(params)).toBe(false);

    const stored = persistIsFinalFlag(params, sessionStorage);
    expect(stored).toBe(false);

    const raw = sessionStorage.getItem(IS_FINAL_STORAGE_KEY);
    expect(raw).toBe("false");
    expect(raw).not.toBe("null");
    // Anti-vacuity: persistIsFinalFlag can only ever write one of these two
    // literals -- the raw "null" string this WR-01 bug used to store is
    // structurally impossible to produce.
    expect(["true", "false"]).toContain(raw);
  });

  it("parseIsFinalParam_backendFinalTrue_isTrue", () => {
    // The shape FeedbackLink.buildUrl actually emits -- anti-regression
    // guard against a fix that makes FINAL unreachable.
    const params = new URLSearchParams("final=true");

    expect(parseIsFinalParam(params)).toBe(true);
  });

  it("parseIsFinalParam_legacyIsFinalTrue_isTrue", () => {
    const params = new URLSearchParams("isFinal=true");

    expect(parseIsFinalParam(params)).toBe(true);
  });

  it("parseIsFinalParam_explicitFalse_isFalse", () => {
    expect(parseIsFinalParam(new URLSearchParams("final=false"))).toBe(false);
    expect(parseIsFinalParam(new URLSearchParams("isFinal=false"))).toBe(
      false
    );
  });

  it("parseIsFinalParam_unrecognisedValue_isFalse", () => {
    expect(parseIsFinalParam(new URLSearchParams("final=null"))).toBe(false);
    expect(parseIsFinalParam(new URLSearchParams("final=yes"))).toBe(false);
  });

  it("readIsFinalFlag_legacyNullStringInStorage_isFalse", () => {
    // The exact corrupted state an already-open browser tab may hold,
    // written by the unfixed build.
    sessionStorage.setItem(IS_FINAL_STORAGE_KEY, "null");

    expect(readIsFinalFlag(new URLSearchParams(), sessionStorage)).toBe(
      false
    );
  });

  it("readIsFinalFlag_urlParameterWinsOverStorage", () => {
    sessionStorage.setItem(IS_FINAL_STORAGE_KEY, "false");

    expect(
      readIsFinalFlag(new URLSearchParams("final=true"), sessionStorage)
    ).toBe(true);
  });

  it("persistThenRead_roundTripsTheBoolean", () => {
    const writeParams = new URLSearchParams("final=true");
    const stored = persistIsFinalFlag(writeParams, sessionStorage);
    expect(stored).toBe(true);

    const readBack = readIsFinalFlag(new URLSearchParams(), sessionStorage);
    expect(readBack).toBe(true);
  });
});
