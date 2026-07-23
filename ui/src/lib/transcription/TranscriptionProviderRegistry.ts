import type { ProviderName, TranscriptionProvider } from "./types";
import { WhisperTransformersProvider } from "./whisper/WhisperTransformersProvider";
import { VoskBrowserProvider } from "./vosk/VoskBrowserProvider";

type ProviderFactory = () => TranscriptionProvider;

// The runtime-selection map — same shape as picking a Spring bean by
// qualifier/name. Adding a third engine is one new folder + one new line
// here (Open/Closed) — nothing below this file ever needs to change.
const factories: Record<ProviderName, ProviderFactory> = {
  whisper: () => new WhisperTransformersProvider(),
  vosk: () => new VoskBrowserProvider(),
};

// Providers own a loaded model / a live worker — expensive to create, so
// resolving the same name twice returns the same instance instead of
// reloading the model on every call.
const instances = new Map<ProviderName, TranscriptionProvider>();

export function resolveProvider(name: ProviderName): TranscriptionProvider {
  const existing = instances.get(name);
  if (existing) return existing;

  const instance = factories[name]();
  instances.set(name, instance);
  return instance;
}

/** Disposes and forgets a provider — mainly for tests and for freeing a
 * loaded model's memory if the user switches engines mid-session. */
export function releaseProvider(name: ProviderName): void {
  instances.get(name)?.dispose();
  instances.delete(name);
}
