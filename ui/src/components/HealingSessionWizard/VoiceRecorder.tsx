import { useEffect, useRef, useState } from "react";
import { Mic, MicOff } from "lucide-react";

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
        <button
          type="button"
          onClick={toggle}
          className={[
            "flex items-center justify-center w-24 h-24 rounded-full transition-all duration-300 ease-in-out shadow-md relative z-10",
            isRecording
              ? "bg-orange-500 text-white shadow-orange-500/50 animate-[pulse_1.5s_ease-in-out_infinite]"
              : "bg-purple-100 text-purple-600 hover:bg-purple-200",
          ].join(" ")}
          aria-label={isRecording ? "Stop recording" : "Start voice recording"}
        >
          {isRecording ? <MicOff size={36} /> : <Mic size={36} />}
        </button>
      </div>

      {/* FIXED Overlay Modal - Cannot be clipped by parent CSS */}
      {showPrompt && !isRecording && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/20 backdrop-blur-sm px-4">
          <div className="bg-white p-5 rounded-xl shadow-2xl max-w-sm w-full flex flex-col gap-4 animate-in fade-in zoom-in-95 duration-200">
            <h3 className="text-base font-semibold text-gray-900 text-center">
              Existing Recording Found
            </h3>
            <p className="text-sm text-gray-600 text-center">
              You already have words recorded. Do you want to continue adding to
              them, or start over?
            </p>
            <div className="flex gap-3 w-full mt-2">
              <button
                onClick={() => startRecording(true)}
                className="flex-1 px-4 py-2 text-sm font-medium bg-red-50 text-red-600 rounded-lg hover:bg-red-100 transition-colors"
              >
                Start Over
              </button>
              <button
                onClick={() => startRecording(false)}
                className="flex-1 px-4 py-2 text-sm font-medium bg-purple-600 text-white rounded-lg hover:bg-purple-700 transition-colors shadow-sm"
              >
                Continue
              </button>
            </div>
            <button
              onClick={() => setShowPrompt(false)}
              className="text-xs text-gray-400 hover:text-gray-600 underline text-center mt-1"
            >
              Cancel
            </button>
          </div>
        </div>
      )}
    </>
  );
}
