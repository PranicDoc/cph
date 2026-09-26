import { describe, it, expect } from "vitest";
import { resolvePrefillChakras } from "./resolveScanChakras";

const crown = { id: "crown" };
const heart = { id: "heart" };
const solarPlexus = { id: "solar-plexus" };
const catalog = [crown, heart, solarPlexus];

describe("resolvePrefillChakras", () => {
  it("prefills nothing when no protocol is selected yet", () => {
    expect(resolvePrefillChakras(catalog, [], [])).toEqual([]);
  });

  it("prefills only the chakras linked to the selected protocol(s)", () => {
    expect(resolvePrefillChakras(catalog, [crown, heart], ["protocol-1"])).toEqual([
      crown,
      heart,
    ]);
  });

  it("prefills nothing when the protocol has no linked chakra data (the picker still offers the full catalog)", () => {
    expect(resolvePrefillChakras(catalog, [], ["protocol-1"])).toEqual([]);
  });

  it("ignores linked chakras that aren't in the catalog", () => {
    const notInCatalog = { id: "not-in-catalog" };
    expect(resolvePrefillChakras(catalog, [notInCatalog, heart], ["protocol-1"])).toEqual([heart]);
  });
});
