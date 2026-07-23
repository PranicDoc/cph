import { useRef, useState } from "react";
import { Mic, MicOff } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { cn } from "@/lib/utils";
import { AudioChunker, initPipeline, type ProviderName } from "@/lib/transcription";
import { parseChakraTranscriptWithReview, type UnresolvedSegment } from "@/lib/chakraNlp";
import { TranscriptionReviewModal, type SegmentResolution } from "./TranscriptionReviewModal";
import type {
  ChakraCatalogEntry,
  ChakraDocEntry,
} from "@/components/ChakraScan/types/spatial-chakra";

interface TranscriptionRecorderProps {
  /** Runtime engine choice — the only thing that changes between the two
   * strategies; everything else in this component is identical either way. */
  engine: ProviderName;
  availableChakras: ChakraCatalogEntry[];
  currentEntries: ChakraDocEntry[];
  onEntriesResolved: (entries: ChakraDocEntry[]) => void;
}

/**
 * Chunking is a transcription-engine necessity (models need bounded audio
 * input), not a parsing boundary — chunk text is only ever concatenated
 * here. Parsing/correction runs exactly once, on the full session's
 * transcript, after recording stops: splitting a chakra name from its
 * energy/activation descriptor across two independent chunk-level parses
 * would silently lose that pairing, and per-chunk review popups would be
 * far more disruptive than one pass at the end.
 */
export function TranscriptionRecorder({
  engine,
  availableChakras,
  currentEntries,
  onEntriesResolved,
}: TranscriptionRecorderProps) {
  const [isRecording, setIsRecording] = useState(false);
  const [modalState, setModalState] = useState<"closed" | "processing" | "review">(
    "closed"
  );
  const [unresolved, setUnresolved] = useState<UnresolvedSegment[]>([]);
  const [pendingEntries, setPendingEntries] = useState<ChakraDocEntry[]>([]);
  // True once a stop cycle has left unsent recognized text behind — drives
  // the "start over vs continue" prompt on the next recording.
  const [hasPendingTranscript, setHasPendingTranscript] = useState(false);
  const [showResumePrompt, setShowResumePrompt] = useState(false);

  const chunkerRef = useRef<AudioChunker | null>(null);
  const accumulatedRef = useRef("");
  const pendingChunksRef = useRef<Promise<void>[]>([]);

  async function startRecording(clearPrevious: boolean) {
    if (clearPrevious) {
      accumulatedRef.current = "";
    }
    pendingChunksRef.current = [];
    setShowResumePrompt(false);

    const chunker = new AudioChunker({
      chunkSeconds: 8,
      onChunk: (chunk) => {
        // Tracked so stopRecording() can wait for every in-flight
        // transcription — including the final flush() chunk — before
        // running the one-shot parse over the complete transcript.
        const work = (async () => {
          const { text } = await initPipeline().model(engine).transcribe(chunk).result();
          if (text) {
            accumulatedRef.current += text + " ";
          }
        })();
        pendingChunksRef.current.push(work);
      },
    });

    chunkerRef.current = chunker;
    await chunker.start();
    setIsRecording(true);
  }

  async function stopRecording() {
    chunkerRef.current?.stop();
    chunkerRef.current = null;
    setIsRecording(false);
    setModalState("processing");

    await Promise.all(pendingChunksRef.current);

    const finalTranscript = accumulatedRef.current.trim();
    setHasPendingTranscript(finalTranscript.length > 0);

    const { entries, unresolved: newUnresolved } = parseChakraTranscriptWithReview(
      finalTranscript,
      availableChakras,
      currentEntries
    );

    if (newUnresolved.length === 0) {
      setModalState("closed");
      onEntriesResolved(entries);
      return;
    }

    setPendingEntries(entries);
    setUnresolved(newUnresolved);
    setModalState("review");
  }

  function handleConfirm(resolutions: SegmentResolution[]) {
    const entryMap = new Map(pendingEntries.map((e) => [e.id, { ...e }]));

    resolutions.forEach((resolution, index) => {
      if (resolution.type === "skip") return;
      const segment = unresolved[index];

      if (resolution.type === "levels") {
        const chakra = segment.knownChakra!;
        let entry = entryMap.get(chakra.id);
        if (!entry) {
          entry = { id: chakra.id, name: chakra.name } as ChakraDocEntry;
        }
        if (resolution.energyLevel) entry.energyLevel = resolution.energyLevel;
        if (resolution.activationLevel) entry.activationLevel = resolution.activationLevel;
        entryMap.set(chakra.id, entry);
        return;
      }

      const chakra = resolution.chakra;
      let entry = entryMap.get(chakra.id);
      if (!entry) {
        entry = { id: chakra.id, name: chakra.name } as ChakraDocEntry;
      }
      if (segment.detectedEnergy) entry.energyLevel = segment.detectedEnergy;
      if (segment.detectedActivation) entry.activationLevel = segment.detectedActivation;
      entryMap.set(chakra.id, entry);
    });

    setModalState("closed");
    setUnresolved([]);
    setPendingEntries([]);
    onEntriesResolved(Array.from(entryMap.values()));
  }

  function toggle() {
    if (isRecording) {
      void stopRecording();
    } else if (hasPendingTranscript) {
      setShowResumePrompt(true);
    } else {
      void startRecording(true);
    }
  }

  return (
    <>
      <Button
        type="button"
        variant="ghost"
        onClick={toggle}
        className={cn(
          "flex h-20 w-20 items-center justify-center rounded-full transition-all duration-300 ease-in-out shadow-md sm:h-24 sm:w-24 [&_svg:not([class*='size-'])]:size-8 sm:[&_svg:not([class*='size-'])]:size-9",
          isRecording
            ? "bg-orange-500 text-white shadow-orange-500/50 animate-[pulse_1.5s_ease-in-out_infinite] hover:bg-orange-500 hover:text-white"
            : "bg-purple-100 text-purple-600 hover:bg-purple-200 hover:text-purple-600"
        )}
        aria-label={isRecording ? "Stop recording" : "Start voice recording"}
      >
        {isRecording ? <MicOff size={32} /> : <Mic size={32} />}
      </Button>

      <Dialog open={showResumePrompt} onOpenChange={setShowResumePrompt}>
        <DialogContent className="max-w-sm gap-4 rounded-xl p-5">
          <DialogHeader>
            <DialogTitle className="text-center">Existing recording found</DialogTitle>
            <DialogDescription className="text-center">
              You already have words recorded. Continue adding to them, or
              start over?
            </DialogDescription>
          </DialogHeader>
          <DialogFooter className="flex-row gap-2 sm:justify-center">
            <Button
              variant="destructive-soft"
              onClick={() => void startRecording(true)}
              className="flex-1"
            >
              Start Over
            </Button>
            <Button onClick={() => void startRecording(false)} className="flex-1">
              Continue
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <TranscriptionReviewModal
        open={modalState !== "closed"}
        status={modalState === "processing" ? "processing" : "review"}
        unresolved={unresolved}
        availableChakras={availableChakras}
        onConfirm={handleConfirm}
      />
    </>
  );
}
