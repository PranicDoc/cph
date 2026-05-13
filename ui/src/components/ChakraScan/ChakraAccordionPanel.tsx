import { useCallback, useEffect, useState } from "react";
import { BODY_REGIONS } from "./data/body-regions";
import { PROTOCOL_CATALOG } from "./data/protocol-catalog";
import { PRANA_COLOR_CATALOG } from "./data/prana-color-catalog";
import { ChakraCard } from "./components/ChakraCard";
import {
  createDefaultEntry,
  createEntriesMap,
  getOverallStats,
  getRegionStats,
} from "./utils";
import type {
  ChakraDocEntry,
  ProtocolCatalogEntry,
  PranaColorEntry,
} from "./types/spatial-chakra";

interface ChakraAccordionPanelProps {
  value: ChakraDocEntry[];
  onChange: (entries: ChakraDocEntry[]) => void;
  protocolCatalog?: ProtocolCatalogEntry[];
  pranaColorCatalog?: PranaColorEntry[];
}

function ChakraAccordionPanel({
  value,
  onChange,
  protocolCatalog = PROTOCOL_CATALOG,
  pranaColorCatalog = PRANA_COLOR_CATALOG,
}: ChakraAccordionPanelProps) {
  const [entries, setEntries] = useState(() => createEntriesMap(value));
  const [selectedRegionId, setSelectedRegionId] = useState(
    BODY_REGIONS[0]?.id ?? ""
  );
  const [expandedChakraId, setExpandedChakraId] = useState<string | null>(null);

  useEffect(() => {
    setEntries(createEntriesMap(value));
  }, [value]);

  const selectedRegion = BODY_REGIONS.find((r) => r.id === selectedRegionId);
  const chakraIds = selectedRegion?.chakraIds ?? [];
  const currentStats = getRegionStats(selectedRegionId, entries);
  const overallStats = getOverallStats(entries);

  const toggleChakra = useCallback((chakraId: string) => {
    setExpandedChakraId((prev) => (prev === chakraId ? null : chakraId));
  }, []);

  const handleRegionChange = (regionId: string) => {
    setSelectedRegionId(regionId);
    setExpandedChakraId(null);
  };

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

  return (
    <div>
      <label className="block text-sm font-semibold text-gray-500 mb-2 uppercase tracking-wide">
        Chakra Energy Levels
      </label>

      <div className="flex items-center gap-3 mb-3">
        <select
          value={selectedRegionId}
          onChange={(e) => handleRegionChange(e.target.value)}
          className="flex-1 text-sm p-2 border border-gray-200 rounded-lg bg-white outline-none focus:ring-1 focus:ring-violet-300 text-gray-700"
        >
          {BODY_REGIONS.map((region) => {
            const stats = getRegionStats(region.id, entries);

            return (
              <option key={region.id} value={region.id}>
                {region.icon} {region.name} ({stats.documented}/{stats.total})
              </option>
            );
          })}
        </select>

        <span
          className={`text-xs px-2.5 py-1 rounded-full font-medium ${
            currentStats.documented === currentStats.total &&
            currentStats.total > 0
              ? "bg-emerald-50 text-emerald-600"
              : currentStats.documented > 0
              ? "bg-amber-50 text-amber-600"
              : "bg-gray-100 text-gray-400"
          }`}
        >
          {overallStats.documented}/{overallStats.total}
        </span>
      </div>

      <div className="bg-gray-50 rounded-xl p-4 grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3">
        {chakraIds.map((id) => {
          const entry = entries[id] ?? createDefaultEntry(id);

          return (
            <ChakraCard
              key={id}
              chakraId={id}
              entry={entry}
              expanded={expandedChakraId === id}
              onToggle={() => toggleChakra(id)}
              onUpdate={handleUpdateEntry}
              protocolCatalog={protocolCatalog}
              pranaColorCatalog={pranaColorCatalog}
            />
          );
        })}
      </div>
    </div>
  );
}

export { ChakraAccordionPanel };
export default ChakraAccordionPanel;
