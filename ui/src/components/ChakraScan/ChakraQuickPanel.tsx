import { useCallback, useEffect, useMemo, useState } from "react";
import { PROTOCOL_CATALOG } from "./data/protocol-catalog";
import { PRANA_COLOR_CATALOG } from "./data/prana-color-catalog";
import { ChakraCard } from "./components/ChakraCard";
import { CHAKRA_CATALOG, createDefaultEntry, createEntriesMap } from "./utils";
import type {
  ChakraCatalogEntry,
  ChakraDocEntry,
  ProtocolCatalogEntry,
  PranaColorEntry,
} from "./types/spatial-chakra";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

interface ChakraQuickPanelProps {
  value: ChakraDocEntry[];
  onChange: (entries: ChakraDocEntry[]) => void;
  availableChakras?: ChakraCatalogEntry[];
  protocolCatalog?: ProtocolCatalogEntry[];
  pranaColorCatalog?: PranaColorEntry[];
}

export function ChakraQuickPanel({
  value,
  onChange,
  availableChakras,
  protocolCatalog = PROTOCOL_CATALOG,
  pranaColorCatalog = PRANA_COLOR_CATALOG,
}: ChakraQuickPanelProps) {
  const [entries, setEntries] = useState(() => createEntriesMap(value));

  const chakraCatalog = availableChakras ?? CHAKRA_CATALOG;
  const [activeChakraId, setActiveChakraId] = useState<string>(
    chakraCatalog[0]?.id || ""
  );

  const chakraById = useMemo(
    () => new Map(chakraCatalog.map((chakra) => [chakra.id, chakra])),
    [chakraCatalog]
  );

  useEffect(() => {
    setEntries(createEntriesMap(value));
  }, [value]);

  const handleUpdateEntry = useCallback(
    (updated: ChakraDocEntry) => {
      setEntries((prev) => {
        const next = { ...prev, [updated.id]: updated };
        onChange(Object.values(next));
        return next;
      });
    },
    [onChange]
  );

  const activeChakra = chakraById.get(activeChakraId);
  const activeEntry = activeChakraId
    ? entries[activeChakraId] ??
      (activeChakra ? createDefaultEntry(activeChakra) : null)
    : null;

  // Calculate overall completion for a quick progress indicator
  const totalChakras = chakraCatalog.length;
  const documentedChakras = Object.keys(entries).length;

  return (
    <div className="flex flex-col border border-gray-200 rounded-xl bg-white shadow-sm overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b bg-gray-50/50">
        <label className="text-sm font-semibold text-gray-700 uppercase tracking-wide">
          Chakra Energy Levels
        </label>
        <span
          className={`text-xs px-2.5 py-1 rounded-full font-medium ${
            documentedChakras === totalChakras && totalChakras > 0
              ? "bg-emerald-50 text-emerald-600"
              : documentedChakras > 0
              ? "bg-amber-50 text-amber-600"
              : "bg-gray-100 text-gray-400"
          }`}
        >
          {documentedChakras}/{totalChakras} Documented
        </span>
      </div>

      {/* Split Pane Layout */}
      <div className="flex flex-col md:flex-row min-h-[400px]">
        {/* Left Pane: Quick Select List (Click 1) */}
        <div className="w-full md:w-1/3 border-r border-gray-100 bg-gray-50 p-2 overflow-y-auto max-h-[500px]">
          <div className="flex flex-col gap-1">
            {chakraCatalog.map((chakra) => {
              const isDocumented = !!entries[chakra.id];
              const isActive = activeChakraId === chakra.id;

              return (
                <Button
                  key={chakra.id}
                  variant="ghost"
                  onClick={() => setActiveChakraId(chakra.id)}
                  className={cn(
                    "flex items-center justify-between px-3 py-2.5 h-auto text-sm rounded-lg text-left font-normal w-full",
                    isActive
                      ? "bg-violet-100 text-violet-800 font-medium shadow-sm hover:bg-violet-100 hover:text-violet-800"
                      : "hover:bg-gray-200 text-gray-600 hover:text-gray-600"
                  )}
                >
                  <span>{chakra.name}</span>
                  {isDocumented && (
                    <span className="w-2 h-2 rounded-full bg-emerald-400"></span>
                  )}
                </Button>
              );
            })}
          </div>
        </div>

        {/* Right Pane: Active Form (Click 2) */}
        <div className="w-full md:w-2/3 p-4 bg-white">
          {activeChakra && activeEntry ? (
            <div className="animate-in fade-in slide-in-from-right-4 duration-200">
              <ChakraCard
                key={activeChakra.id}
                chakra={activeChakra}
                entry={activeEntry}
                expanded={true} // Force expansion so there is no accordion behavior
                onToggle={() => {}} // Disable manual toggling
                onUpdate={handleUpdateEntry}
                protocolCatalog={protocolCatalog}
                pranaColorCatalog={pranaColorCatalog}
              />
            </div>
          ) : (
            <div className="h-full flex items-center justify-center text-sm text-gray-400">
              Select a chakra to begin documentation
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default ChakraQuickPanel;
