import { useState } from "react";
import { Eye, Info } from "lucide-react";
import { useUiVersion } from "../../ui-version";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { DEFAULT_SCAN_ENTRY_MODE, type ScanEntryMode } from "@/config/scanEntryConfig";

import { ScanPreviewDialog } from "./ScanPreviewDialog";

import type {
  ChakraCatalogEntry,
  ChakraDocEntry,
} from "../ChakraScan/types/spatial-chakra";
import { ChakraManualEntry } from "../ChakraScan/ChakraManualEntry";
import { ScanPreviewTable } from "./ScanPreviewTable";
import { VoiceRecorder } from "./VoiceRecorder";
import { parseChakraTranscript } from "@/lib/chakraNlp";

interface ScanStepProps {
  title: string;
  value: ChakraDocEntry[];
  onChange: (data: ChakraDocEntry[]) => void;
  availableChakras: ChakraCatalogEntry[];
  /** Chakras to pre-populate as rows (e.g. the selected protocols' linked chakras); the picker still offers `availableChakras`. */
  prefillChakras?: ChakraCatalogEntry[];
}

export function ScanStep({
  title,
  value,
  onChange,
  availableChakras,
  prefillChakras,
}: ScanStepProps) {
  const { isNewView } = useUiVersion();
  const [previewOpen, setPreviewOpen] = useState(false);
  const [mode, setMode] = useState<ScanEntryMode>(DEFAULT_SCAN_ENTRY_MODE);

  function handleTranscript(transcript: string) {
    const updated = parseChakraTranscript(transcript, availableChakras, value);
    onChange(updated);
  }

  const preview = (
    <ScanPreviewDialog
      open={previewOpen}
      onClose={() => setPreviewOpen(false)}
    >
      <ScanPreviewTable data={value} />
    </ScanPreviewDialog>
  );

  if (isNewView) {
    return (
      <>
        <div className="card-h" style={{ paddingBottom: 14, borderBottom: "1px solid var(--hair)", flexWrap: "wrap", gap: 10 }}>
          <h2>{title}</h2>
          <div className="seg" role="group" aria-label="Scan entry mode">
            <button type="button" className={mode === "manual" ? "on" : undefined} aria-pressed={mode === "manual"} onClick={() => setMode("manual")}>
              Manual
            </button>
            <button type="button" className={mode === "voice" ? "on" : undefined} aria-pressed={mode === "voice"} onClick={() => setMode("voice")}>
              Voice <span className="badge warn" style={{ height: 18, padding: "0 6px", fontSize: 10.5 }}>Beta</span>
            </button>
          </div>
        </div>
        {mode === "manual" ? (
          <div style={{ marginTop: 16 }}>
            <ChakraManualEntry
              value={value}
              onChange={onChange}
              availableChakras={availableChakras}
              prefillChakras={prefillChakras}
            />
          </div>
        ) : (
          <>
            <div style={{ display: "flex", gap: 10, alignItems: "flex-start", margin: "16px 0", padding: "14px 16px", borderRadius: 12, background: "var(--primary-soft)" }}>
              <Info className="ico" aria-hidden="true" style={{ color: "var(--accent-text)", flex: "none" }} />
              <div className="small">
                Press the microphone button below and clearly dictate the scanned chakra details. Once
                recording is complete, you can preview the extracted information before proceeding.
              </div>
            </div>
            <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 12, padding: "24px 0" }}>
              <VoiceRecorder onTranscript={handleTranscript} />
              <button type="button" className="btn btn-secondary" onClick={() => setPreviewOpen(true)}>
                <Eye className="ico" aria-hidden="true" />
                Preview
              </button>
            </div>
          </>
        )}
        {preview}
      </>
    );
  }

  return (
    <>
      <div className="space-y-6">
        <div className="flex flex-col gap-3 border-b pb-3 sm:flex-row sm:items-center sm:justify-between">
          <h2 className="text-lg font-semibold">{title}</h2>
          <div className="flex flex-wrap items-center gap-2">
            <div className="inline-flex rounded-lg border border-border bg-muted p-0.5">
              <Button
                type="button"
                variant={mode === "manual" ? "default" : "ghost"}
                size="sm"
                onClick={() => setMode("manual")}
                className={cn("rounded-md px-3", mode !== "manual" && "shadow-none")}
              >
                Manual
              </Button>
              <Button
                type="button"
                variant={mode === "voice" ? "default" : "ghost"}
                size="sm"
                onClick={() => setMode("voice")}
                className={cn("rounded-md px-3", mode !== "voice" && "shadow-none")}
              >
                Voice
                <span className="ml-1.5 rounded bg-amber-100 px-1 py-0.5 text-[10px] font-semibold uppercase leading-none text-amber-800">
                  Beta
                </span>
              </Button>
            </div>
            {mode === "voice" && (
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => setPreviewOpen(true)}
                className="gap-1.5"
              >
                <Eye className="h-4 w-4" />
                Preview
              </Button>
            )}
          </div>
        </div>

        {mode === "voice" && (
          <div className="rounded-lg border border-sky-200 bg-sky-50 p-4">
            <p className="text-sm leading-6 text-sky-700">
              Press the microphone button below and clearly dictate the
              scanned chakra details. Once recording is complete, you can
              preview the extracted information before proceeding.
            </p>
          </div>
        )}

        {mode === "manual" ? (
          <ChakraManualEntry
            value={value}
            onChange={onChange}
            availableChakras={availableChakras}
            prefillChakras={prefillChakras}
          />
        ) : (
          <div className="flex justify-center">
            <VoiceRecorder onTranscript={handleTranscript} />
          </div>
        )}
      </div>
      {preview}
    </>
  );
}
