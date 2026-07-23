import { CHAKRA_CATALOG } from "@/components/ChakraScan/data/chakra-catalog";

// Kaldi grammar mode hard-restricts Vosk's decoder to only ever emit words
// from this list (plus "[unk]" for anything genuinely outside it) — this is
// what eliminates domain-vocabulary substitutions like "forehead" -> "4th"
// at the source, something Whisper has no equivalent hard guarantee for
// (see WhisperTransformersProvider — it can only be soft-prompted, not
// constrained). Vosk-specific by nature, so this lives here rather than in
// the shared transcription/ types.

// Deliberately standard-dictionary English words only. "underactivated" /
// "overactivated" are PranicDoc-specific coinages that a small general
// English ASR model's lexicon has no pronunciation entry for at all — under
// grammar-constrained decoding, listing an out-of-lexicon word doesn't make
// it recognizable, it just gets silently dropped and replaced by the nearest
// word Vosk actually CAN decode (confirmed empirically: "underactivated" was
// substituted with "hyperactive" — the opposite meaning). chakraNlp.ts's
// ACTIVATION_KEYWORDS regex already accepts these standard synonyms, so no
// parser change is needed — only the grammar needs to offer words the model
// can genuinely hear.
const ENERGY_ACTIVATION_TERMS = [
  "depleted",
  "congested",
  "underactivated",
  "overactivated",
  "normal"
];

function buildChakraGrammar(): string[] {
  const words = new Set<string>();

  for (const chakra of CHAKRA_CATALOG) {
    for (const word of chakra.name.toLowerCase().split(/[\s-]+/)) {
      if (word) words.add(word);
    }
  }

  for (const term of ENERGY_ACTIVATION_TERMS) {
    words.add(term);
  }

  // Required by Kaldi grammar mode so out-of-vocabulary speech is marked
  // unknown instead of forced into the nearest in-grammar word.
  words.add("[unk]");

  return Array.from(words);
}

/** JSON string form KaldiRecognizer's constructor expects directly. */
export const CHAKRA_GRAMMAR = JSON.stringify(buildChakraGrammar());
