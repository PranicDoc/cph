import type { AudioInput, TranscriptionProvider, TranscriptResult } from "../types";

/**
 * Strategy implementation #1: Whisper-base running fully in-browser via
 * transformers.js (Hugging Face, Apache 2.0 — no vendor, no per-call cost,
 * audio never leaves the device).
 *
 * Inference always runs in whisper.worker.ts, never on the main thread —
 * ONNX Runtime Web's WASM backend is CPU-heavy enough to visibly freeze
 * React (typing, animations, clicks) if run inline. This class's only job
 * is to be a thin, promise-based request/response bridge to that worker —
 * it holds zero model/inference logic itself (Single Responsibility).
 */
export class WhisperTransformersProvider implements TranscriptionProvider {
  readonly name = "whisper" as const;

  private worker: Worker | null = null;
  private readyPromise: Promise<void> | null = null;
  private pending = new Map<string, (result: TranscriptResult) => void>();
  private requestCounter = 0;

  async init(): Promise<void> {
    if (this.readyPromise) return this.readyPromise;

    this.worker = new Worker(new URL("./whisper.worker.ts", import.meta.url), {
      type: "module",
    });

    // Worker-thread exceptions (e.g. a failed model fetch, a WASM init
    // failure) don't surface anywhere by default — without this, init()
    // just hangs forever since readyPromise never resolves or rejects.
    this.worker.onerror = (event: ErrorEvent) => {
      console.error("[whisper.worker] uncaught error:", event.message, event);
    };

    this.worker.onmessage = (event: MessageEvent) => {
      const message = event.data;

      if (message.type === "result") {
        const resolve = this.pending.get(message.requestId);
        resolve?.({ text: message.text, isFinal: true });
        this.pending.delete(message.requestId);
      }

      if (message.type === "error") {
        const resolve = this.pending.get(message.requestId);
        // Fail open rather than reject — a dropped chunk shouldn't crash a
        // 3-minute recording; the caller just gets an empty final result.
        resolve?.({ text: "", isFinal: true });
        this.pending.delete(message.requestId);
      }
    };

    this.readyPromise = new Promise<void>((resolve) => {
      const onReady = (event: MessageEvent) => {
        if (event.data?.type === "ready") {
          this.worker?.removeEventListener("message", onReady);
          resolve();
        }
      };
      this.worker!.addEventListener("message", onReady);
      this.worker!.postMessage({ type: "init" });
    });

    return this.readyPromise;
  }

  async transcribe(audio: AudioInput): Promise<TranscriptResult> {
    if (!this.worker) {
      throw new Error(
        "WhisperTransformersProvider.transcribe() called before init()"
      );
    }

    const requestId = `whisper-${this.requestCounter++}`;

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
