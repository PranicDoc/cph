import { ScanStep } from "./ScanStep";
import type {
  ChakraCatalogEntry,
  ChakraDocEntry,
} from "../ChakraScan/types/spatial-chakra";

interface StepPostScanProps {
  value: ChakraDocEntry[];
  onChange: (data: ChakraDocEntry[]) => void;
  availableChakras: ChakraCatalogEntry[];
}

export function StepPostScan(props: StepPostScanProps) {
  return <ScanStep {...props} title="Post-Healing Scan" />;
}
