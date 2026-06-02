import { ChakraAccordionPanel, ChakraDataGrid } from "../ChakraScan";
import type {
  ChakraCatalogEntry,
  ChakraDocEntry,
  PranaColorEntry,
} from "../ChakraScan/types/spatial-chakra";

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
  return (
    <div className="space-y-6">
      <h2 className="text-lg font-semibold border-b pb-2">Post-Healing Scan</h2>

      <ChakraDataGrid
        value={value}
        onChange={onChange}
        availableChakras={availableChakras}
        pranaColorCatalog={pranaColorCatalog}
      />
    </div>
  );
}
