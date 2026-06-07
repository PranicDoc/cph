import { parseChakraTranscript } from "@/src/lib/chakraNlp";
import { ChakraAccordionPanel, ChakraDataGrid } from "../ChakraScan";
import type {
  ChakraCatalogEntry,
  ChakraDocEntry,
  PranaColorEntry,
} from "../ChakraScan/types/spatial-chakra";
import { VoiceRecorder } from "./VoiceRecorder";

interface StepPostScanProps {
  value: ChakraDocEntry[];
  onChange: (data: ChakraDocEntry[]) => void;
  availableChakras: ChakraCatalogEntry[];
  pranaColorCatalog: PranaColorEntry[];
}

export function StepPostScan({
  value,
  onChange,
  availableChakras,
  pranaColorCatalog,
}: StepPostScanProps) {
  function handleTranscript(transcript: string) {
    const updated = parseChakraTranscript(transcript, availableChakras, value);
    onChange(updated);
  }

  return (
    <div className="space-y-6">
      <h2 className="text-lg font-semibold border-b pb-2">Post-Healing Scan</h2>
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
