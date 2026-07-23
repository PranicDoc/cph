// Captures microphone audio and emits fixed-size, resampled 16kHz mono
// Float32Array chunks — the one shared input contract both providers
// require (see types.ts). This file knows nothing about Whisper or Vosk;
// it only knows how to turn a live mic stream into correctly-shaped audio
// chunks (Single Responsibility) — whichever provider the pipeline is
// wired to just receives chunks the same way either time.

const TARGET_SAMPLE_RATE = 16_000;
const CHUNK_SECONDS = 8;

export interface AudioChunkerOptions {
  onChunk: (chunk: Float32Array) => void;
  chunkSeconds?: number;
}

export class AudioChunker {
  private audioContext: AudioContext | null = null;
  private mediaStream: MediaStream | null = null;
  private sourceNode: MediaStreamAudioSourceNode | null = null;
  // ScriptProcessorNode is deprecated in favor of AudioWorkletNode, kept
  // here to avoid an extra registered worklet-module file for this pass —
  // swap to AudioWorkletNode if ScriptProcessorNode's main-thread callback
  // overhead becomes a measurable issue.
  private processorNode: ScriptProcessorNode | null = null;

  private buffer: number[] = [];
  private readonly chunkSamples: number;
  private readonly onChunk: (chunk: Float32Array) => void;

  constructor(options: AudioChunkerOptions) {
    this.onChunk = options.onChunk;
    this.chunkSamples = TARGET_SAMPLE_RATE * (options.chunkSeconds ?? CHUNK_SECONDS);
  }

  async start(): Promise<void> {
    // autoGainControl directly targets "user is far from the mic" —
    // noiseSuppression/echoCancellation target background noise. This is
    // the control Web Speech API never exposed (it manages capture
    // internally); capturing the stream ourselves is what makes it
    // available at all.
    this.mediaStream = await navigator.mediaDevices.getUserMedia({
      audio: {
        autoGainControl: true,
        noiseSuppression: true,
        echoCancellation: true,
      },
    });

    this.audioContext = new AudioContext({ sampleRate: TARGET_SAMPLE_RATE });
    this.sourceNode = this.audioContext.createMediaStreamSource(this.mediaStream);

    // Buffer size 4096 at 16kHz = ~256ms per callback — small enough to
    // keep memory low, large enough to avoid excessive callback overhead.
    this.processorNode = this.audioContext.createScriptProcessor(4096, 1, 1);
    this.processorNode.onaudioprocess = (event) => {
      const input = event.inputBuffer.getChannelData(0);
      this.buffer.push(...input);

      if (this.buffer.length >= this.chunkSamples) {
        const chunk = Float32Array.from(this.buffer.splice(0, this.chunkSamples));
        this.onChunk(chunk);
      }
    };

    this.sourceNode.connect(this.processorNode);
    this.processorNode.connect(this.audioContext.destination);
  }

  /** Flushes whatever's left in the buffer as a final, possibly short-of-
   * `chunkSeconds`, chunk — call this once when recording stops so the
   * last few seconds of speech aren't silently dropped. */
  flush(): void {
    if (this.buffer.length === 0) return;
    const chunk = Float32Array.from(this.buffer.splice(0, this.buffer.length));
    this.onChunk(chunk);
  }

  stop(): void {
    this.flush();
    this.processorNode?.disconnect();
    this.sourceNode?.disconnect();
    this.mediaStream?.getTracks().forEach((track) => track.stop());
    this.audioContext?.close();

    this.processorNode = null;
    this.sourceNode = null;
    this.mediaStream = null;
    this.audioContext = null;
    this.buffer = [];
  }
}
