import { describe, it, expect, vi, beforeEach } from "vitest";
import type { TranscriptionProvider, TranscriptResult } from "./types";

// The registry resolves real Whisper/Vosk workers, which need a live
// browser + downloaded models — not something a unit test should exercise.
// This test only verifies the pipeline's own chaining/dispatch logic, with
// a fake provider standing in for "whichever engine .model() picked".
vi.mock("./TranscriptionProviderRegistry", () => ({
  resolveProvider: vi.fn(),
}));

import { resolveProvider } from "./TranscriptionProviderRegistry";
import { initPipeline } from "./TranscriptionPipeline";

function makeFakeProvider(result: TranscriptResult): TranscriptionProvider {
  return {
    name: "whisper",
    init: vi.fn().mockResolvedValue(undefined),
    transcribe: vi.fn().mockResolvedValue(result),
    dispose: vi.fn(),
  };
}

describe("TranscriptionPipeline", () => {
  beforeEach(() => {
    vi.mocked(resolveProvider).mockReset();
  });

  it("chains model -> transcribe -> result and returns only the TranscriptResult", async () => {
    const fakeResult: TranscriptResult = { text: "sacral chakra depleted", isFinal: true };
    const provider = makeFakeProvider(fakeResult);
    vi.mocked(resolveProvider).mockReturnValue(provider);

    const audio = new Float32Array([0.1, 0.2, 0.3]);
    const result = await initPipeline().model("whisper").transcribe(audio).result();

    expect(result).toEqual(fakeResult);
    expect(provider.init).toHaveBeenCalledOnce();
    expect(provider.transcribe).toHaveBeenCalledWith(audio);
  });

  it("resolves whichever provider name .model() was given — same chain, different engine", async () => {
    const provider = makeFakeProvider({ text: "vosk output", isFinal: true });
    vi.mocked(resolveProvider).mockReturnValue(provider);

    await initPipeline().model("vosk").transcribe(new Float32Array()).result();

    expect(resolveProvider).toHaveBeenCalledWith("vosk");
  });

  it("throws if .transcribe() is called before .model()", () => {
    const pipeline = initPipeline();
    expect(() => pipeline.transcribe(new Float32Array())).toThrow(
      /call \.model\(name\) before \.transcribe/
    );
  });
});
