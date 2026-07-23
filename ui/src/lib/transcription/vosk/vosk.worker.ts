// Runs inside a dedicated Web Worker, same reasoning as whisper.worker.ts.
//
// IMPORTANT ADAPTER NOTE: Vosk's real API is a continuous streaming
// recognizer that emits "result"/"partialresult" events on its own schedule
// (e.g. when it detects an utterance boundary) — it does NOT naturally
// return "the transcript of this one chunk you just fed me". Whisper's API
// is genuinely request/response; Vosk's is genuinely push/event-stream.
// To satisfy the same TranscriptionProvider contract as Whisper (Liskov
// Substitution — callers must not care which engine is behind the
// interface), this worker treats each "transcribe" message as a
// synchronization point: feed the chunk in, resolve with whatever the next
// result/partialresult event delivers. That's a real design tradeoff, not
// a hidden one — documented here rather than silently glossed over.
//
// Verify createModel/KaldiRecognizer's exact call signature against the
// installed vosk-browser version's README before shipping — the shape
// below matches its documented public API but has not been exercised
// against a live model in this environment.

import { createModel } from "vosk-browser";
import { CHAKRA_GRAMMAR } from "./chakraGrammar";

type InitMessage = { type: "init"; modelUrl: string };
type TranscribeMessage = {
  type: "transcribe";
  requestId: string;
  audio: Float32Array;
};
type InboundMessage = InitMessage | TranscribeMessage;

// vosk-browser's published type definitions don't cleanly expose the
// KaldiRecognizer instance type for reuse here — `any` is honest rather
// than faking a precise type this codebase hasn't verified against a real
// installed version yet.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let recognizer: any = null;

// Vosk resolves whichever request is "current" when its next event fires —
// see the ADAPTER NOTE above for why this is a queue, not a map keyed by id.
const requestQueue: string[] = [];

const SAMPLE_RATE = 16_000;

self.onmessage = async (event: MessageEvent<InboundMessage>) => {
  const message = event.data;

  if (message.type === "init") {
    const model = await createModel(message.modelUrl);
    // Passing the grammar hard-restricts recognition to this vocabulary —
    // see chakraGrammar.ts for why this is the real fix for domain-vocabulary
    // substitution errors, not just a mitigation.
    recognizer = new model.KaldiRecognizer(SAMPLE_RATE, CHAKRA_GRAMMAR);
    recognizer.setWords(true);

    recognizer.on("result", (msg: { result: { text: string } }) => {
      const requestId = requestQueue.shift();
      if (requestId) {
        self.postMessage({ type: "result", requestId, text: msg.result.text });
      }
    });

    recognizer.on("partialresult", (msg: { result: { partial: string } }) => {
      // Partial results don't correspond to a specific request — broadcast
      // without a requestId, the provider surfaces these as isFinal: false.
      self.postMessage({ type: "partial", text: msg.result.partial });
    });

    self.postMessage({ type: "ready" });
    return;
  }

  if (message.type === "transcribe") {
    if (!recognizer) {
      self.postMessage({
        type: "error",
        requestId: message.requestId,
        error: "vosk.worker: transcribe requested before init completed",
      });
      return;
    }

    requestQueue.push(message.requestId);
    // acceptWaveform expects a real AudioBuffer; our pipeline hands over raw
    // Float32Array samples, so acceptWaveformFloat is the correct call here.
    recognizer.acceptWaveformFloat(message.audio, SAMPLE_RATE);
    // Vosk's "result" event only fires on its own silence/endpoint detection —
    // it does NOT emit one result per acceptWaveform call. Since our pipeline
    // is chunk-boundary-based (one request per fixed-size chunk), force a
    // flush here so every chunk deterministically produces exactly one
    // "result" event instead of occasionally waiting forever for a pause
    // that may never land inside a given chunk.
    recognizer.retrieveFinalResult();
  }
};
