import { describe, it, expect } from "vitest";
import { serializeExercise, parseExercise, exerciseLabels } from "./exercise";

describe("exercise multi-select <-> stored string", () => {
  it("joins several selections with commas", () => {
    expect(serializeExercise(["YOGA", "WALKING"])).toBe("YOGA,WALKING");
  });

  it("drops NONE when real exercises are also picked", () => {
    expect(serializeExercise(["NONE", "GYM"])).toBe("GYM");
  });

  it("keeps NONE on its own", () => {
    expect(serializeExercise(["NONE"])).toBe("NONE");
  });

  it("stores nothing for an empty selection", () => {
    expect(serializeExercise([])).toBeNull();
  });

  it("parses a stored string back, tolerating legacy single values and blanks", () => {
    expect(parseExercise("YOGA,WALKING")).toEqual(["YOGA", "WALKING"]);
    expect(parseExercise("GYM")).toEqual(["GYM"]);
    expect(parseExercise(null)).toEqual([]);
    expect(parseExercise(" ")).toEqual([]);
  });

  it("labels each stored value for display and skips NONE", () => {
    expect(exerciseLabels("YOGA,SPORTS")).toEqual(["Yoga", "Sports"]);
    expect(exerciseLabels("NONE")).toEqual([]);
  });
});
