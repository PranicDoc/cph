import { useState } from "react";
import { Eye } from "lucide-react";
import { Button } from "@/components/ui/button";

import { VoiceRecorder } from "./VoiceRecorder";
import { ScanPreviewDialog } from "./ScanPreviewDialog";
import { parseChakraTranscript } from "../../lib/chakraNlp";

import type {
  ChakraCatalogEntry,
  ChakraDocEntry,
} from "../ChakraScan/types/spatial-chakra";
import { ScanPreviewTable } from "./ScanPreviewTable";

interface ScanStepProps {
  title: string;
  value: ChakraDocEntry[];
  onChange: (data: ChakraDocEntry[]) => void;
  availableChakras: ChakraCatalogEntry[];
}

export function ScanStep({
  title,
  value,
  onChange,
  availableChakras,
}: ScanStepProps) {
  const [previewOpen, setPreviewOpen] = useState(false);

  function handleTranscript(transcript: string) {
    const updated = parseChakraTranscript(transcript, availableChakras, value);

    onChange(updated);
  }

  return (
    <>
      <div className="space-y-6">
        <h2 className="text-lg font-semibold border-b pb-2">{title}</h2>
        <div className="rounded-lg border border-sky-200 bg-sky-50 p-4">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
            <div className="flex-1">
              <p className="mt-1 text-sm leading-6 text-sky-700">
                Press the microphone button below and clearly dictate the
                scanned chakra details. Once recording is complete, you can
                preview the extracted information before proceeding.
              </p>
            </div>
            <Button
              type="button"
              variant="outline"
              onClick={() => setPreviewOpen(true)}
              className="rounded-md border-sky-300 bg-white px-4 py-2 h-auto gap-2 text-sky-700 hover:bg-sky-100 hover:text-sky-700 w-full sm:w-auto"
            >
              <Eye className="h-4 w-4" />
              Preview
            </Button>
          </div>
        </div>

        <div className="flex justify-center">
          <VoiceRecorder onTranscript={handleTranscript} />
        </div>
      </div>
      <ScanPreviewDialog
        open={previewOpen}
        onClose={() => setPreviewOpen(false)}
      >
        <ScanPreviewTable data={value} />
      </ScanPreviewDialog>
    </>
  );
}
