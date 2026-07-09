import { useEffect, useRef, useState } from "react";
import { Mic, MicOff } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogTitle,
} from "@/components/ui/dialog";
import { cn } from "@/lib/utils";

interface VoiceRecorderProps {
  onTranscript: (transcript: string) => void;
}

// Web Speech API is not in the default TS DOM lib
interface ISpeechRecognition {
  continuous: boolean;
  interimResults: boolean;
  lang: string;
  start(): void;
  stop(): void;
  onresult: ((event: ISpeechRecognitionEvent) => void) | null;
  onend: (() => void) | null;
}

interface ISpeechRecognitionResult {
  readonly isFinal: boolean;
  readonly 0: { readonly transcript: string };
}

interface ISpeechRecognitionEvent {
  readonly resultIndex: number;
  readonly results: ISpeechRecognitionResult[] & { length: number };
}

interface SpeechRecognitionConstructor {
  new (): ISpeechRecognition;
}

function getSpeechRecognition(): SpeechRecognitionConstructor | null {
  if (typeof window === "undefined") return null;
  const w = window as typeof window & {
    SpeechRecognition?: SpeechRecognitionConstructor;
    webkitSpeechRecognition?: SpeechRecognitionConstructor;
  };
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null;
}

export function VoiceRecorder({ onTranscript }: VoiceRecorderProps) {
  const SpeechRecognitionAPI = getSpeechRecognition();

  const [isRecording, setIsRecording] = useState(false);
  const [showPrompt, setShowPrompt] = useState(false);

  // Explicitly track if we have recorded words so React triggers the popup
  const [hasSavedText, setHasSavedText] = useState(false);

  const recognitionRef = useRef<ISpeechRecognition | null>(null);
  const accumulatedRef = useRef("");
  const interimRef = useRef("");

  const onTranscriptRef = useRef(onTranscript);
  const explicitStopRef = useRef(false);

  useEffect(() => {
    onTranscriptRef.current = onTranscript;
  }, [onTranscript]);

  useEffect(() => {
    return () => {
      explicitStopRef.current = true;
      if (recognitionRef.current) {
        recognitionRef.current.stop();
      }
    };
  }, []);

  if (!SpeechRecognitionAPI) return null;

  function startRecording(clearPrevious: boolean) {
    const recognition = new SpeechRecognitionAPI!();
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.lang = "en-US";

    if (clearPrevious) {
      accumulatedRef.current = "";
      interimRef.current = "";
      setHasSavedText(false);
    }

    recognition.onresult = (event: ISpeechRecognitionEvent) => {
      let interim = "";
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const result = event.results[i];
        if (result.isFinal) {
          accumulatedRef.current += result[0].transcript + " ";
        } else {
          interim += result[0].transcript;
        }
      }
      interimRef.current = interim;
      console.log("Live generating text:", accumulatedRef.current + interim);
    };

    recognition.onend = () => {
      if (!explicitStopRef.current) {
        setTimeout(() => {
          if (!explicitStopRef.current) {
            try {
              recognitionRef.current?.start();
            } catch (e) {
              console.error("Failed to auto-restart recognition", e);
            }
          }
        }, 50);
      } else {
        const final = (accumulatedRef.current + interimRef.current).trim();
        accumulatedRef.current = final;
        interimRef.current = "";
        console.log("Final saved text:", final);

        if (final) {
          setHasSavedText(true);
          onTranscriptRef.current(final);
        }

        setIsRecording(false);
        recognitionRef.current = null;
      }
    };

    explicitStopRef.current = false;
    recognitionRef.current = recognition;
    recognition.start();
    setIsRecording(true);
    setShowPrompt(false);
  }

  function stopRecording() {
    explicitStopRef.current = true;
    recognitionRef.current?.stop();
  }

  function toggle() {
    if (isRecording) {
      stopRecording();
    } else {
      if (showPrompt) {
        setShowPrompt(false);
        return;
      }

      // If we recorded actual words previously, show the modal
      if (hasSavedText) {
        setShowPrompt(true);
      } else {
        startRecording(true);
      }
    }
  }

  return (
    <>
      <div className="relative inline-flex flex-col items-center">
        <Button
          type="button"
          variant="ghost"
          onClick={toggle}
          className={cn(
            "flex items-center justify-center w-24 h-24 rounded-full transition-all duration-300 ease-in-out shadow-md relative z-10 [&_svg:not([class*='size-'])]:size-9",
            isRecording
              ? "bg-orange-500 text-white shadow-orange-500/50 animate-[pulse_1.5s_ease-in-out_infinite] hover:bg-orange-500 hover:text-white"
              : "bg-purple-100 text-purple-600 hover:bg-purple-200 hover:text-purple-600"
          )}
          aria-label={isRecording ? "Stop recording" : "Start voice recording"}
        >
          {isRecording ? <MicOff size={36} /> : <Mic size={36} />}
        </Button>
      </div>

      <Dialog
        open={showPrompt && !isRecording}
        onOpenChange={(o) => !o && setShowPrompt(false)}
      >
        <DialogContent className="max-w-sm p-5 gap-4 rounded-xl" showCloseButton={false}>
          <DialogTitle className="text-base font-semibold text-gray-900 text-center">
            Existing Recording Found
          </DialogTitle>
          <p className="text-sm text-gray-600 text-center">
            You already have words recorded. Do you want to continue adding to
            them, or start over?
          </p>
          <div className="flex gap-3 w-full mt-2">
            <Button
              variant="destructive-soft"
              onClick={() => startRecording(true)}
              className="flex-1 bg-red-50 text-red-600 hover:bg-red-100"
            >
              Start Over
            </Button>
            <Button
              onClick={() => startRecording(false)}
              className="flex-1 bg-purple-600 hover:bg-purple-700 shadow-sm"
            >
              Continue
            </Button>
          </div>
          <Button
            variant="link"
            onClick={() => setShowPrompt(false)}
            className="h-auto p-0 text-xs font-normal text-gray-400 hover:text-gray-600 underline mx-auto mt-1"
          >
            Cancel
          </Button>
        </DialogContent>
      </Dialog>
    </>
  );
}
