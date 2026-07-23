// The one contract every transcription engine must satisfy. Kept intentionally
// minimal (Interface Segregation) — no vendor/engine-specific config leaks
// through here. Anything provider-specific (model name, worker path, sample
// rate quirks) lives inside that provider's own folder, never in this file.

/** Both Whisper and Vosk are trained on 16kHz mono PCM — this is a real,
 * not arbitrary, shared contract between the two engines, not a convenience
 * abstraction invented for this codebase. */
export type AudioInput = Float32Array;

export interface TranscriptResult {
  text: string;
  /** Whisper only ever returns final chunks (batch). Vosk can emit partial
   * results while still listening — isFinal lets callers tell them apart
   * without knowing which engine produced the result. */
  isFinal: boolean;
  /** Not every engine reports this (Whisper's transformers.js pipeline
   * doesn't by default) — optional rather than faked. */
  confidence?: number;
}

export type ProviderName = "whisper" | "vosk";

export interface TranscriptionProvider {
  readonly name: ProviderName;

  /** Loads the model / spins up the worker. Idempotent — safe to call
   * more than once; subsequent calls are a no-op once ready. */
  init(): Promise<void>;

  /** Feeds one chunk of audio in, resolves with the transcript for that
   * chunk. Callers never see worker/message-passing details. */
  transcribe(audio: AudioInput): Promise<TranscriptResult>;

  /** Tears down the worker and releases the loaded model. */
  dispose(): void;
}
