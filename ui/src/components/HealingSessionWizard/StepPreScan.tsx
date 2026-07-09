import { ScanStep } from "./ScanStep";
import type {
  ChakraCatalogEntry,
  ChakraDocEntry,
} from "../ChakraScan/types/spatial-chakra";

interface StepPreScanProps {
  value: ChakraDocEntry[];
  onChange: (data: ChakraDocEntry[]) => void;
  availableChakras: ChakraCatalogEntry[];
}

export function StepPreScan(props: StepPreScanProps) {
  return <ScanStep {...props} title="Pre-Healing Scan" />;
}
