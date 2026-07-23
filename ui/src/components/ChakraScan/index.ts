// ── Chakra Accordion Panel — Standalone Export ──

export { ChakraAccordionPanel, default } from "./ChakraAccordionPanel";
export { ChakraQuickPanel } from "./ChakraQuickPanel"
export { ChakraDataGrid } from "./ChakraDataGrid"
export { ChakraManualEntry } from "./ChakraManualEntry"
export {
  buildBackendChakraCatalog,
  buildPranaColorCatalog,
} from "./utils";

// Types (re-export the ones consumers need)
export type {
  ChakraDocEntry,
  EnergyLevel,
  ActivationLevel,
  ProtocolSelection,
  TreatmentColor,
  PranaColorEntry,
  ProtocolCatalogEntry,
} from "./types/spatial-chakra";

// Data catalogs (if consumer wants to pass custom ones)
export { BODY_REGIONS } from "./data/body-regions";
export { CHAKRA_CATALOG } from "./data/chakra-catalog";
export { PROTOCOL_CATALOG } from "./data/protocol-catalog";
export { PRANA_COLOR_CATALOG } from "./data/prana-color-catalog";
export { ENERGY_OPTIONS, ACTIVATION_OPTIONS } from "./data/energy-options";
