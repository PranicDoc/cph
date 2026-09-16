import { describe, it, expect } from "vitest";
import { resolveScanChakras } from "./resolveScanChakras";

const crown = { id: "crown" };
const heart = { id: "heart" };
const solarPlexus = { id: "solar-plexus" };
const allChakras = [crown, heart, solarPlexus];

describe("resolveScanChakras", () => {
  it("shows the full catalog when no protocol is selected yet", () => {
    expect(resolveScanChakras(allChakras, [], [])).toEqual(allChakras);
  });

  it("shows only the linked chakras when a protocol is selected", () => {
    expect(resolveScanChakras(allChakras, [crown, heart], ["protocol-1"])).toEqual([
      crown,
      heart,
    ]);
  });

  it("shows an EMPTY list when a protocol is selected but has no linked chakra data — does not fall back to the full catalog", () => {
    expect(resolveScanChakras(allChakras, [], ["protocol-1"])).toEqual([]);
  });

  it("shows an empty list when the protocol's linked chakras don't intersect this session's chakra list — does not fall back to the full catalog", () => {
    const otherHealersChakra = { id: "not-in-session-catalog" };
    expect(resolveScanChakras(allChakras, [otherHealersChakra], ["protocol-1"])).toEqual([]);
  });
});
