import { describe, it, expect } from "vitest";
import { reconstructSelectedProtocols, isLegacyProtocol } from "./protocolSelection";
import type { Protocol } from "../hooks/admin/useMasterData";

const catalog: Protocol[] = [
  { id: "p1", name: "Toothache", sourceBook: "Advanced Pranic Healing", pageNumber: 76, protocolType: "PROTOCOL" },
  { id: "p2", name: "Migraine", sourceBook: "Advanced Pranic Healing", pageNumber: 80, protocolType: "PROTOCOL" },
];

describe("reconstructSelectedProtocols", () => {
  it("picks catalog protocols by name, in the stored order", () => {
    expect(reconstructSelectedProtocols("Migraine, Toothache", catalog).map((p) => p.id)).toEqual(["p2", "p1"]);
  });

  it("matches ignoring case and extra spaces", () => {
    expect(reconstructSelectedProtocols("  toothache ", catalog).map((p) => p.id)).toEqual(["p1"]);
  });

  it("keeps a stored name that is not in the catalog (e.g. 'Headache') so it still shows when the case is edited", () => {
    const result = reconstructSelectedProtocols("Headache", catalog);

    expect(result).toHaveLength(1);
    expect(result[0].name).toBe("Headache");
    expect(isLegacyProtocol(result[0])).toBe(true);
  });

  it("mixes matched and unmatched names, keeping the order and dropping duplicates", () => {
    const result = reconstructSelectedProtocols("Headache, Toothache, headache", catalog);

    expect(result.map((p) => p.name)).toEqual(["Headache", "Toothache"]);
    expect(isLegacyProtocol(result[1])).toBe(false);
  });

  it("with the catalog not loaded yet every name is kept, so nothing is lost before it arrives", () => {
    expect(reconstructSelectedProtocols("Toothache", []).map((p) => p.name)).toEqual(["Toothache"]);
  });

  it("nothing stored means nothing selected", () => {
    expect(reconstructSelectedProtocols("", catalog)).toEqual([]);
    expect(reconstructSelectedProtocols(" , ", catalog)).toEqual([]);
  });

  it("a real catalog protocol is never reported as legacy", () => {
    expect(isLegacyProtocol(catalog[0])).toBe(false);
  });
});
