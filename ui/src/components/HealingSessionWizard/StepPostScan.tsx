import { ChakraAccordionPanel } from "../ChakraScan";
import type { ChakraDocEntry } from "../ChakraScan/types/spatial-chakra";

interface StepPostScanProps {
  value: ChakraDocEntry[];
  onChange: (data: ChakraDocEntry[]) => void;
}

export function StepPostScan({ value, onChange }: StepPostScanProps) {
  return (
    <div className="space-y-6">
      <h2 className="text-lg font-semibold border-b pb-2">Post-Healing Scan</h2>

      <ChakraAccordionPanel value={value} onChange={onChange} />
    </div>
  );
}
