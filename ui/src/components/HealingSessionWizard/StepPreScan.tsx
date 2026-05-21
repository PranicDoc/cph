import { ChakraAccordionPanel } from "../ChakraScan";
import type {
  ChakraCatalogEntry,
  ChakraDocEntry,
  PranaColorEntry,
} from "../ChakraScan/types/spatial-chakra";

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
  return (
    <div className="space-y-6">
      <h2 className="text-lg font-semibold border-b pb-2">Pre-Healing Scan</h2>

      <ChakraAccordionPanel
        value={value}
        onChange={onChange}
        availableChakras={availableChakras}
        pranaColorCatalog={pranaColorCatalog}
      />
    </div>
  );
}
