import { describe, it, expect } from "vitest";
import { parseChakraTranscript, parseChakraTranscriptWithReview, rankCandidates } from "./chakraNlp";
import { CHAKRA_CATALOG } from "./../components/ChakraScan/data/chakra-catalog";
import type { ChakraDocEntry } from "./../components/ChakraScan/types/spatial-chakra";

describe("parseChakraTranscript", () => {
  it("splits a punctuation-free, multi-chakra transcript into separate entries", () => {
    // Whisper (and most in-browser ASR) frequently emits zero punctuation —
    // this is the exact shape of real output, not an idealized sentence.
    const transcript =
      "Crown chakra underactivated depleted Forehead chakra overactivated congested";

    const entries = parseChakraTranscript(transcript, CHAKRA_CATALOG, []);

    const crown = entries.find((e) => e.name === "Crown Chakra");
    const forehead = entries.find((e) => e.name === "Forehead Chakra");

    expect(crown?.activationLevel).toBe("U");
    expect(crown?.energyLevel).toBe("D");
    expect(forehead?.activationLevel).toBe("O");
    expect(forehead?.energyLevel).toBe("C");
  });

  it("does not bleed one chakra's energy/activation into another's entry", () => {
    const transcript =
      "Throat chakra congested overactivated Navel chakra depleted underactivated";

    const entries = parseChakraTranscript(transcript, CHAKRA_CATALOG, []);

    const navel = entries.find((e) => e.name === "Navel Chakra");
    expect(navel?.energyLevel).toBe("D");
    expect(navel?.activationLevel).toBe("U");
  });

  it("silently finds nothing when ASR mis-hears the chakra name itself (documents the remaining accuracy gap, not a splitting bug)", () => {
    // "Ground"/"4th chakra" are real Whisper mis-transcriptions of
    // "Crown"/"Forehead" observed in manual testing — no amount of better
    // segmentation recovers this; it needs a correction/synonym layer.
    const transcript =
      "Ground chakra under activated depleted 4th chakra over activated congested";

    const entries = parseChakraTranscript(transcript, CHAKRA_CATALOG, []);

    expect(entries).toEqual([]);
  });
});

describe("parseChakraTranscriptWithReview", () => {
  it("surfaces a mis-heard chakra name as an unresolved segment with the real chakra ranked as a candidate, instead of silently dropping it", () => {
    const transcript =
      "ground chakra depleted forehead chakra overactivated congested";

    const { entries, unresolved } = parseChakraTranscriptWithReview(
      transcript,
      CHAKRA_CATALOG,
      []
    );

    const forehead = entries.find((e) => e.name === "Forehead Chakra");
    expect(forehead?.energyLevel).toBe("C");
    expect(forehead?.activationLevel).toBe("O");

    expect(unresolved).toHaveLength(1);
    expect(unresolved[0].rawText).toBe("ground chakra depleted");
    expect(unresolved[0].detectedEnergy).toBe("D");
    expect(unresolved[0].detectedActivation).toBeNull();
    expect(unresolved[0].candidates.map((c) => c.name)).toContain("Crown Chakra");
    expect(unresolved[0].knownChakra).toBeNull();
  });

  it("splits two consecutive unresolved phrases when neither chakra name is recognized", () => {
    // Real Whisper mis-hearing observed in testing: neither "ground" nor
    // "4th" match any catalog name, so there's no chakra-name anchor at all
    // between them — the repeated energy keyword is what signals a new
    // phrase has started.
    const transcript = "ground chakra depleted 4th chakra congested";

    const { unresolved } = parseChakraTranscriptWithReview(
      transcript,
      CHAKRA_CATALOG,
      []
    );

    expect(unresolved).toHaveLength(2);
    expect(unresolved[0].detectedEnergy).toBe("D");
    expect(unresolved[1].detectedEnergy).toBe("C");
  });

  it("flags a recognized chakra as unresolved when its activation level is missing (ChakraDocEntry requires both)", () => {
    const transcript = "crown chakra depleted [unk] basic [unk]";

    const { entries, unresolved } = parseChakraTranscriptWithReview(
      transcript,
      CHAKRA_CATALOG,
      []
    );

    const crown = entries.find((e) => e.name === "Crown Chakra");
    expect(crown?.energyLevel).toBe("D");

    expect(unresolved).toHaveLength(1);
    expect(unresolved[0].knownChakra?.name).toBe("Crown Chakra");
    expect(unresolved[0].detectedEnergy).toBe("D");
    expect(unresolved[0].detectedActivation).toBeNull();

    // "basic" alone (no "chakra") never matched a catalog name, so it's not
    // touched at all — same as the plain parser.
    expect(entries.find((e) => e.name === "Basic Chakra")).toBeUndefined();
  });

  it("flags a recognized chakra as unresolved when its energy level is missing", () => {
    const transcript = "crown chakra overactivated";

    const { entries, unresolved } = parseChakraTranscriptWithReview(
      transcript,
      CHAKRA_CATALOG,
      []
    );

    const crown = entries.find((e) => e.name === "Crown Chakra");
    expect(crown?.activationLevel).toBe("O");

    expect(unresolved).toHaveLength(1);
    expect(unresolved[0].knownChakra?.name).toBe("Crown Chakra");
    expect(unresolved[0].detectedEnergy).toBeNull();
    expect(unresolved[0].detectedActivation).toBe("O");
  });

  it("does not re-flag a chakra whose missing level is already filled in from a previous scan pass", () => {
    // "crown chakra overactivated" alone is missing energy — but if a prior
    // pass (or manual entry) already set it, review shouldn't re-ask.
    const crown = CHAKRA_CATALOG.find((c) => c.name === "Crown Chakra")!;
    const transcript = "crown chakra overactivated";

    const { unresolved } = parseChakraTranscriptWithReview(transcript, CHAKRA_CATALOG, [
      {
        id: crown.id,
        name: crown.name,
        energyLevel: "N",
        activationLevel: "N",
      } as ChakraDocEntry,
    ]);

    expect(unresolved).toEqual([]);
  });

  it("returns no unresolved segments for a fully-recognized transcript", () => {
    const transcript = "Crown chakra depleted underactivated";

    const { unresolved } = parseChakraTranscriptWithReview(
      transcript,
      CHAKRA_CATALOG,
      []
    );

    expect(unresolved).toEqual([]);
  });
});

describe("rankCandidates", () => {
  it("ranks the real chakra highly even when the mis-heard word shares no starting letter", () => {
    // "crown" vs "ground" is the exact real confusion pair from manual
    // testing — Soundex alone would miss this (different first letter), so
    // this test guards the edit-distance-first design decision.
    const candidates = rankCandidates("ground chakra depleted", CHAKRA_CATALOG);
    expect(candidates.map((c) => c.name)).toContain("Crown Chakra");
  });

  it("falls back to a generic list when no distinctive leftover word exists", () => {
    const candidates = rankCandidates("[unk]", CHAKRA_CATALOG, 3);
    expect(candidates).toHaveLength(3);
  });
});
