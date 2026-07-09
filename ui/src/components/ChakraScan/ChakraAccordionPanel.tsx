import { useCallback, useEffect, useMemo, useState } from "react";
import { PROTOCOL_CATALOG } from "./data/protocol-catalog";
import { PRANA_COLOR_CATALOG } from "./data/prana-color-catalog";
import { ChakraCard } from "./components/ChakraCard";
import {
  CHAKRA_CATALOG,
  buildRegionsForChakras,
  createDefaultEntry,
  createEntriesMap,
  getOverallStats,
  getRegionStats,
} from "./utils";
import type {
  ChakraCatalogEntry,
  ChakraDocEntry,
  ProtocolCatalogEntry,
  PranaColorEntry,
} from "./types/spatial-chakra";
import { Badge } from "@/components/ui/badge";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { cn } from "@/lib/utils";

interface ChakraAccordionPanelProps {
  value: ChakraDocEntry[];
  onChange: (entries: ChakraDocEntry[]) => void;
  availableChakras?: ChakraCatalogEntry[];
  protocolCatalog?: ProtocolCatalogEntry[];
  pranaColorCatalog?: PranaColorEntry[];
}

function ChakraAccordionPanel({
  value,
  onChange,
  availableChakras,
  protocolCatalog = PROTOCOL_CATALOG,
  pranaColorCatalog = PRANA_COLOR_CATALOG,
}: ChakraAccordionPanelProps) {
  const [entries, setEntries] = useState(() => createEntriesMap(value));
  const [selectedRegionId, setSelectedRegionId] = useState("");
  const [expandedChakraId, setExpandedChakraId] = useState<string | null>(null);
  const chakraCatalog = availableChakras ?? CHAKRA_CATALOG;
  const regions = useMemo(
    () => buildRegionsForChakras(chakraCatalog),
    [chakraCatalog]
  );
  const chakraById = useMemo(
    () => new Map(chakraCatalog.map((chakra) => [chakra.id, chakra])),
    [chakraCatalog]
  );

  useEffect(() => {
    setEntries(createEntriesMap(value));
  }, [value]);

  useEffect(() => {
    if (regions.length === 0) {
      setSelectedRegionId("");
      return;
    }

    if (!regions.some((region) => region.id === selectedRegionId)) {
      setSelectedRegionId(regions[0].id);
    }
  }, [regions, selectedRegionId]);

  const selectedRegion = regions.find((r) => r.id === selectedRegionId);
  const chakraIds = selectedRegion?.chakraIds ?? [];
  const currentStats = getRegionStats(selectedRegionId, regions, entries);
  const overallStats = getOverallStats(regions, entries);

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
      <Label className="block text-sm font-semibold text-gray-500 mb-2 uppercase tracking-wide">
        Chakra Energy Levels
      </Label>

      <div className="flex items-center gap-3 mb-3">
        <Select value={selectedRegionId} onValueChange={handleRegionChange}>
          <SelectTrigger className="flex-1 p-2 border border-gray-200 text-gray-700">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {regions.map((region) => {
              const stats = getRegionStats(region.id, regions, entries);

              return (
                <SelectItem key={region.id} value={region.id}>
                  {region.icon} {region.name} ({stats.documented}/{stats.total})
                </SelectItem>
              );
            })}
          </SelectContent>
        </Select>

        <Badge
          className={cn(
            "px-2.5 py-1 h-auto font-medium",
            currentStats.documented === currentStats.total &&
              currentStats.total > 0
              ? "bg-emerald-50 text-emerald-600"
              : currentStats.documented > 0
              ? "bg-amber-50 text-amber-600"
              : "bg-gray-100 text-gray-400"
          )}
        >
          {overallStats.documented}/{overallStats.total}
        </Badge>
      </div>

      <div className="bg-gray-50 rounded-xl p-4 grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3">
        {chakraIds.map((id) => {
          const chakra = chakraById.get(id);

          if (!chakra) {
            return null;
          }

          const entry = entries[id] ?? createDefaultEntry(chakra);

          return (
            <ChakraCard
              key={id}
              chakra={chakra}
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
