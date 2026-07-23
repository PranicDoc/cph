import type { AudioInput, ProviderName, TranscriptResult } from "./types";
import { resolveProvider } from "./TranscriptionProviderRegistry";

/**
 * The pending step returned by `.transcribe()`. Kept as its own tiny class
 * (rather than making `.transcribe()` itself async) so the chain reads as
 * init -> pick model -> feed audio -> get result, with exactly one await
 * point at the end — `.result()` — instead of one per step.
 */
class TranscriptionExecution {
  constructor(
    private readonly providerName: ProviderName,
    private readonly audio: AudioInput
  ) {}

  /** The only async step in the chain. Resolves to the transcript alone —
   * nothing about the provider, the worker, or the model leaks out here. */
  async result(): Promise<TranscriptResult> {
    const provider = resolveProvider(this.providerName);
    await provider.init(); // no-op once already loaded
    return provider.transcribe(this.audio);
  }
}

export class TranscriptionPipeline {
  private providerName: ProviderName | null = null;

  /** Picks which engine backs this pipeline — the ONLY thing that changes
   * between "Whisper via transformers.js" and "Vosk in-browser". Every step
   * after this is identical regardless of which name was passed in. */
  model(name: ProviderName): this {
    this.providerName = name;
    return this;
  }

  transcribe(audio: AudioInput): TranscriptionExecution {
    if (!this.providerName) {
      throw new Error(
        "TranscriptionPipeline: call .model(name) before .transcribe(audio)"
      );
    }
    return new TranscriptionExecution(this.providerName, audio);
  }
}

/**
 * `initPipeline().model("whisper").transcribe(chunk).result()`
 * `initPipeline().model("vosk").transcribe(chunk).result()`
 *
 * Same chain, same return type, only the string passed to `.model()`
 * changes which engine actually runs.
 */
export function initPipeline(): TranscriptionPipeline {
  return new TranscriptionPipeline();
}
