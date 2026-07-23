import type { AudioInput, TranscriptionProvider, TranscriptResult } from "../types";

// Small (~50MB) English model — the realistic size for in-browser use.
// Swap this if you need a different language/accuracy tradeoff; nothing
// outside this file needs to know.
const MODEL_URL = "/models/vosk-model-small-en-us-0.15.tar.gz";

/**
 * Strategy implementation #2: Vosk (Kaldi-based, Apache 2.0) running
 * fully in-browser via vosk-browser's WASM build. Same reasoning as
 * WhisperTransformersProvider for why this is a thin worker bridge and
 * nothing more — see that file's comment.
 *
 * Vosk's real API is streaming/event-based, not naturally request/response
 * like Whisper's — vosk.worker.ts documents the adapter seam that makes
 * this class satisfy the exact same TranscriptionProvider contract anyway.
 */
export class VoskBrowserProvider implements TranscriptionProvider {
  readonly name = "vosk" as const;

  private worker: Worker | null = null;
  private readyPromise: Promise<void> | null = null;
  private pending = new Map<string, (result: TranscriptResult) => void>();
  private requestCounter = 0;

  async init(): Promise<void> {
    if (this.readyPromise) return this.readyPromise;

    this.worker = new Worker(new URL("./vosk.worker.ts", import.meta.url), {
      type: "module",
    });

    this.worker.onmessage = (event: MessageEvent) => {
      const message = event.data;

      if (message.type === "result") {
        const resolve = this.pending.get(message.requestId);
        resolve?.({ text: message.text, isFinal: true });
        this.pending.delete(message.requestId);
      }

      if (message.type === "error") {
        const resolve = this.pending.get(message.requestId);
        resolve?.({ text: "", isFinal: true });
        this.pending.delete(message.requestId);
      }

      // "partial" messages have no requestId (see the adapter note in
      // vosk.worker.ts) — this provider only surfaces final results through
      // transcribe()'s promise; a future streaming UI could subscribe to
      // partials separately without changing this contract.
    };

    this.readyPromise = new Promise<void>((resolve) => {
      const onReady = (event: MessageEvent) => {
        if (event.data?.type === "ready") {
          this.worker?.removeEventListener("message", onReady);
          resolve();
        }
      };
      this.worker!.addEventListener("message", onReady);
      this.worker!.postMessage({ type: "init", modelUrl: MODEL_URL });
    });

    return this.readyPromise;
  }

  async transcribe(audio: AudioInput): Promise<TranscriptResult> {
    if (!this.worker) {
      throw new Error("VoskBrowserProvider.transcribe() called before init()");
    }

    const requestId = `vosk-${this.requestCounter++}`;

    return new Promise<TranscriptResult>((resolve) => {
      this.pending.set(requestId, resolve);
      this.worker!.postMessage({
        type: "transcribe",
        requestId,
        audio,
      });
    });
  }

  dispose(): void {
    this.worker?.terminate();
    this.worker = null;
    this.readyPromise = null;
    this.pending.clear();
  }
}
