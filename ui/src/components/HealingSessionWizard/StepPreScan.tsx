import { ChakraDataGrid } from "../ChakraScan";
import type {
  ChakraCatalogEntry,
  ChakraDocEntry,
  PranaColorEntry,
} from "../ChakraScan/types/spatial-chakra";
import { VoiceRecorder } from "./VoiceRecorder";
import { parseChakraTranscript } from "../../lib/chakraNlp";

interface StepPreScanProps {
  value: ChakraDocEntry[];
  onChange: (data: ChakraDocEntry[]) => void;
  availableChakras: ChakraCatalogEntry[];
  pranaColorCatalog: PranaColorEntry[];
}

export function StepPreScan({
  value,
  onChange,
  availableChakras,
  pranaColorCatalog,
}: StepPreScanProps) {
  function handleTranscript(transcript: string) {
    const updated = parseChakraTranscript(transcript, availableChakras, value);
    onChange(updated);
  }

  return (
    <div className="space-y-6">
      <h2 className="text-lg font-semibold border-b pb-2">Pre-Healing Scan</h2>
      <div className="flex justify-center w-full">
        <VoiceRecorder onTranscript={handleTranscript} />
      </div>
      {/* <ChakraDataGrid
        value={value}
        onChange={onChange}
        availableChakras={availableChakras}
        pranaColorCatalog={pranaColorCatalog}
      /> */}
    </div>
  );
}
