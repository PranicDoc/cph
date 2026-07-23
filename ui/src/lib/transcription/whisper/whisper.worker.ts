// Runs inside a dedicated Web Worker — never on the main thread. Whisper
// inference is CPU-heavy; running it here keeps React responsive while a
// chunk is being transcribed (see WhisperTransformersProvider's comment on
// why this is not optional).

import { pipeline, env, type AutomaticSpeechRecognitionPipeline } from "@xenova/transformers";

// Without this, transformers.js tries a local "/models/..." path first; Vite's
// dev-server SPA fallback returns index.html for that 404 instead of a real
// 404, which then fails JSON.parse. Force remote-only (Hugging Face CDN) loading.
env.allowLocalModels = false;
// transformers.js's Cache Storage layer can persist a bad/partial response
// (e.g. from a transient network hiccup) and replay it forever afterward,
// with no way to invalidate short of clearing Cache Storage by hand. Disabled
// until we add our own caching wrapper that validates responses before storing.
env.useBrowserCache = false;

type InitMessage = { type: "init" };
type TranscribeMessage = {
  type: "transcribe";
  requestId: string;
  audio: Float32Array;
};
type InboundMessage = InitMessage | TranscribeMessage;

let transcriber: AutomaticSpeechRecognitionPipeline | null = null;

self.onmessage = async (event: MessageEvent<InboundMessage>) => {
  const message = event.data;

  if (message.type === "init") {
    // whisper-base, quantized (int8) — the realistic size/accuracy tradeoff
    // for in-browser use discussed with the team; swap the model id here if
    // that tradeoff changes, nothing else in the pipeline needs to know.
    transcriber = await pipeline(
      "automatic-speech-recognition",
      "Xenova/whisper-base",
      { quantized: true }
    );
    self.postMessage({ type: "ready" });
    return;
  }

  if (message.type === "transcribe") {
    if (!transcriber) {
      self.postMessage({
        type: "error",
        requestId: message.requestId,
        error: "whisper.worker: transcribe requested before init completed",
      });
      return;
    }

    const output = await transcriber(message.audio, {
      language: "english",
      task: "transcribe",
    });

    // transformers.js returns { text } for a single chunk, or an array for
    // batched input — this pipeline only ever sends one chunk at a time.
    const text = Array.isArray(output) ? output[0]?.text ?? "" : output.text;

    self.postMessage({
      type: "result",
      requestId: message.requestId,
      text,
    });
  }
};
