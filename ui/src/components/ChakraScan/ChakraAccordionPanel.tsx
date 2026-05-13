import React, { useState, useCallback, useMemo, useEffect } from "react";
import { BODY_REGIONS } from "./data/body-regions";
import { getChakraById } from "./data/chakra-catalog";
import { PROTOCOL_CATALOG, searchProtocols, PROTOCOL_LEVEL_LABELS } from "./data/protocol-catalog";
import { PRANA_COLOR_CATALOG } from "./data/prana-color-catalog";
import { ENERGY_OPTIONS, ACTIVATION_OPTIONS } from "./data/energy-options";
import type {
  ChakraDocEntry,
  EnergyLevel,
  ActivationLevel,
  ProtocolSelection,
  TreatmentColor,
  ProtocolCatalogEntry,
  PranaColorEntry,
} from "./types/spatial-chakra";

// ── Props ──

interface ChakraAccordionPanelProps {
  value: ChakraDocEntry[];
  onChange: (entries: ChakraDocEntry[]) => void;
  protocolCatalog?: ProtocolCatalogEntry[];
  pranaColorCatalog?: PranaColorEntry[];
}

// ── Helpers ──

function createDefaultEntry(chakraId: string): ChakraDocEntry {
  const c = getChakraById(chakraId);
  return {
    id: chakraId,
    name: c?.name ?? chakraId,
    color: c?.colorHex ?? "#888",
    energyLevel: "N",
    activationLevel: "N",
    treatmentColors: [],
  };
}

function isDocumented(entry: ChakraDocEntry | undefined): boolean {
  if (!entry) return false;
  return (
    entry.energyLevel !== "N" ||
    entry.activationLevel !== "N" ||
    !!entry.protocol ||
    entry.treatmentColors.length > 0
  );
}

// ═══════════════════════════════════════════════════════════
// Compact Chakra Card (grid card matching ChakraScan style)
// ═══════════════════════════════════════════════════════════

interface ChakraCardProps {
  chakraId: string;
  entry: ChakraDocEntry;
  expanded: boolean;
  onToggle: () => void;
  onUpdate: (entry: ChakraDocEntry) => void;
  protocolCatalog: ProtocolCatalogEntry[];
  pranaColorCatalog: PranaColorEntry[];
}

const ChakraCard = React.memo(function ChakraCard({
  chakraId,
  entry,
  expanded,
  onToggle,
  onUpdate,
  protocolCatalog,
  pranaColorCatalog,
}: ChakraCardProps) {
  const catalog = getChakraById(chakraId);
  const colorHex = catalog?.colorHex ?? "#888";
  const name = catalog?.name ?? chakraId;
  const documented = isDocumented(entry);

  const [protocolSearch, setProtocolSearch] = useState("");
  const [showProtocolDropdown, setShowProtocolDropdown] = useState(false);
  const [showColorPicker, setShowColorPicker] = useState(false);
  const [colorUsageType, setColorUsageType] = useState<"cleansing" | "energising">("cleansing");

  const filteredProtocols = useMemo(() => searchProtocols(protocolSearch), [protocolSearch]);

  const groupedProtocols = useMemo(() => {
    const groups: Record<string, ProtocolCatalogEntry[]> = {};
    for (const p of filteredProtocols) {
      const key = `${PROTOCOL_LEVEL_LABELS[p.level] || p.level} — ${p.category}`;
      if (!groups[key]) groups[key] = [];
      groups[key].push(p);
    }
    return groups;
  }, [filteredProtocols]);

  const selectedColorIds = new Set(entry.treatmentColors.map((c) => c.id));

  const updateEnergy = useCallback(
    (level: EnergyLevel) => onUpdate({ ...entry, energyLevel: level }),
    [entry, onUpdate]
  );
  const updateActivation = useCallback(
    (level: ActivationLevel) => onUpdate({ ...entry, activationLevel: level }),
    [entry, onUpdate]
  );
  const setProtocol = useCallback(
    (protocol: ProtocolSelection | undefined) => onUpdate({ ...entry, protocol }),
    [entry, onUpdate]
  );
  const addColor = useCallback(
    (color: PranaColorEntry) => {
      onUpdate({
        ...entry,
        treatmentColors: [
          ...entry.treatmentColors,
          { id: color.id, name: color.name, colorHex: color.colorHex, usageType: colorUsageType },
        ],
      });
    },
    [entry, onUpdate, colorUsageType]
  );
  const removeColor = useCallback(
    (colorId: string) =>
      onUpdate({ ...entry, treatmentColors: entry.treatmentColors.filter((c) => c.id !== colorId) }),
    [entry, onUpdate]
  );

  // ── Collapsed card (grid cell) ──
  if (!expanded) {
    return (
      <div
        onClick={onToggle}
        className={`flex flex-col gap-1.5 p-2.5 bg-white rounded-lg border cursor-pointer transition-all hover:shadow-sm ${
          documented ? "border-emerald-200" : "border-gray-200"
        }`}
      >
        <div className="flex items-center gap-2 min-w-0">
          <div
            className="w-3.5 h-3.5 rounded-full flex-shrink-0"
            style={{ backgroundColor: colorHex }}
          />
          <span className="text-sm font-medium text-gray-700 truncate flex-1">{name}</span>
          {documented && (
            <svg width="14" height="14" viewBox="0 0 16 16" className="flex-shrink-0 text-emerald-500">
              <circle cx="8" cy="8" r="7" fill="currentColor" fillOpacity="0.1" stroke="currentColor" strokeWidth="1" />
              <path d="M5 8l2 2 4-4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" fill="none" />
            </svg>
          )}
        </div>
        <select
          className="w-full text-xs p-1.5 border border-gray-200 rounded-md bg-transparent outline-none focus:ring-1 focus:ring-violet-300 text-gray-600"
          value={entry.energyLevel}
          onClick={(e) => e.stopPropagation()}
          onChange={(e) => {
            e.stopPropagation();
            updateEnergy(e.target.value as EnergyLevel);
          }}
        >
          {ENERGY_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
        {/* Status badges row */}
        {documented && (
          <div className="flex items-center gap-1 flex-wrap">
            {entry.activationLevel !== "N" && (
              <span
                className="text-[9px] font-semibold px-1.5 py-0.5 rounded"
                style={{
                  backgroundColor: `${ACTIVATION_OPTIONS.find((o) => o.value === entry.activationLevel)?.colorHex ?? "#888"}15`,
                  color: ACTIVATION_OPTIONS.find((o) => o.value === entry.activationLevel)?.colorHex ?? "#888",
                }}
              >
                {ACTIVATION_OPTIONS.find((o) => o.value === entry.activationLevel)?.label}
              </span>
            )}
            {entry.treatmentColors.length > 0 && (
              <span className="flex -space-x-1">
                {entry.treatmentColors.slice(0, 4).map((c) => (
                  <span
                    key={`${c.id}-${c.usageType}`}
                    className="w-3 h-3 rounded-full border-2 border-white"
                    style={{ backgroundColor: c.colorHex }}
                  />
                ))}
              </span>
            )}
            {entry.protocol && (
              <span className="text-[9px] text-violet-500 font-medium truncate max-w-[80px]">
                {entry.protocol.name}
              </span>
            )}
          </div>
        )}
      </div>
    );
  }

  // ── Expanded card (full-width detail) ──
  return (
    <div className="col-span-full bg-white rounded-xl border border-violet-200 shadow-md overflow-hidden">
      {/* Header */}
      <div className="flex items-center gap-3 px-4 py-3 border-b border-gray-100 bg-gray-50/50">
        <span
          className="w-3.5 h-3.5 rounded-full flex-shrink-0"
          style={{ backgroundColor: colorHex, boxShadow: `0 0 8px ${colorHex}44` }}
        />
        <h3 className="text-sm font-semibold text-gray-800 flex-1 truncate">{name}</h3>
        {catalog?.location && (
          <span className="text-[10px] text-gray-400">{catalog.location}</span>
        )}
        <button
          onClick={onToggle}
          className="w-6 h-6 flex items-center justify-center rounded-md hover:bg-gray-200/60 text-gray-400 transition-colors"
          aria-label="Collapse"
        >
          <svg width="12" height="12" viewBox="0 0 16 16" fill="none">
            <path d="M4 4l8 8M12 4l-8 8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
          </svg>
        </button>
      </div>

      <div className="px-4 py-3 space-y-4">
        {/* Energy Level */}
        <div>
          <label className="block text-[10px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
            Energy Level
          </label>
          <div className="flex flex-wrap gap-1">
            {ENERGY_OPTIONS.map((opt) => {
              const isSelected = entry.energyLevel === opt.value;
              return (
                <button
                  key={opt.value}
                  onClick={() => updateEnergy(opt.value)}
                  className={`px-2 py-1 rounded-md text-[11px] font-medium transition-all ${
                    isSelected ? "ring-1 shadow-sm" : "bg-gray-50 hover:bg-gray-100 text-gray-500"
                  }`}
                  style={
                    isSelected
                      ? { backgroundColor: `${opt.colorHex}18`, color: opt.colorHex, outlineColor: `${opt.colorHex}40` }
                      : undefined
                  }
                >
                  {opt.shortLabel}
                </button>
              );
            })}
          </div>
          <p className="text-[10px] text-gray-400 mt-1">
            {ENERGY_OPTIONS.find((o) => o.value === entry.energyLevel)?.label || "Normal"}
          </p>
        </div>

        {/* Activation Level */}
        <div>
          <label className="block text-[10px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
            Activation Level
          </label>
          <div className="flex gap-1">
            {ACTIVATION_OPTIONS.map((opt) => {
              const isSelected = entry.activationLevel === opt.value;
              return (
                <button
                  key={opt.value}
                  onClick={() => updateActivation(opt.value)}
                  className={`flex-1 px-2 py-1.5 rounded-md text-xs font-medium transition-all text-center ${
                    isSelected ? "ring-1 shadow-sm" : "bg-gray-50 hover:bg-gray-100 text-gray-500"
                  }`}
                  style={
                    isSelected
                      ? { backgroundColor: `${opt.colorHex}18`, color: opt.colorHex, outlineColor: `${opt.colorHex}40` }
                      : undefined
                  }
                >
                  {opt.label}
                </button>
              );
            })}
          </div>
        </div>

        {/* Protocol */}
        <div>
          <label className="block text-[10px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
            Protocol
          </label>
          {entry.protocol ? (
            <div className="flex items-center gap-2 px-2.5 py-1.5 bg-violet-50 rounded-lg border border-violet-100">
              <span className="text-xs text-violet-700 font-medium flex-1 truncate">
                {entry.protocol.name}
              </span>
              <span className="text-[9px] text-violet-400 uppercase">{entry.protocol.level}</span>
              <button
                onClick={() => setProtocol(undefined)}
                className="text-violet-300 hover:text-violet-500 transition-colors"
              >
                <svg width="12" height="12" viewBox="0 0 16 16" fill="none">
                  <path d="M4 4l8 8M12 4l-8 8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
                </svg>
              </button>
            </div>
          ) : (
            <div className="relative">
              <input
                type="text"
                value={protocolSearch}
                onChange={(e) => { setProtocolSearch(e.target.value); setShowProtocolDropdown(true); }}
                onFocus={() => setShowProtocolDropdown(true)}
                placeholder="Search protocols..."
                className="w-full px-2.5 py-1.5 text-xs border border-gray-200 rounded-lg bg-white focus:outline-none focus:ring-1 focus:ring-violet-200 focus:border-violet-300 placeholder:text-gray-300"
              />
              {showProtocolDropdown && (
                <div className="absolute z-20 mt-1 w-full bg-white border border-gray-200 rounded-lg shadow-lg max-h-48 overflow-y-auto">
                  {Object.entries(groupedProtocols).map(([groupName, protocols]) => (
                    <div key={groupName}>
                      <div className="px-2.5 py-1 text-[9px] font-semibold text-gray-400 uppercase tracking-wider bg-gray-50 sticky top-0">
                        {groupName}
                      </div>
                      {protocols.map((p) => (
                        <button
                          key={p.id}
                          onClick={() => {
                            setProtocol({ id: p.id, name: p.name, category: p.category, level: p.level });
                            setShowProtocolDropdown(false);
                            setProtocolSearch("");
                          }}
                          className="w-full text-left px-2.5 py-1.5 text-xs text-gray-700 hover:bg-violet-50 hover:text-violet-700 transition-colors"
                        >
                          {p.name}
                        </button>
                      ))}
                    </div>
                  ))}
                  {Object.keys(groupedProtocols).length === 0 && (
                    <div className="px-3 py-4 text-xs text-gray-400 text-center">No protocols found</div>
                  )}
                  <button
                    onClick={() => setShowProtocolDropdown(false)}
                    className="w-full text-left px-2.5 py-1.5 text-[10px] text-gray-400 hover:text-gray-600 border-t border-gray-100"
                  >
                    Cancel
                  </button>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Treatment Colors */}
        <div>
          <label className="block text-[10px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
            Treatment Colors
          </label>
          {entry.treatmentColors.length > 0 && (
            <div className="flex flex-wrap gap-1.5 mb-2">
              {entry.treatmentColors.map((color) => (
                <span
                  key={`${color.id}-${color.usageType}`}
                  className="inline-flex items-center gap-1.5 pl-1 pr-1.5 py-0.5 rounded-full bg-gray-50 border border-gray-200 text-[10px]"
                >
                  <span
                    className="w-3 h-3 rounded-full flex-shrink-0 border border-white"
                    style={{
                      background: `linear-gradient(135deg, ${color.colorHex}, ${color.colorHex}CC)`,
                      boxShadow: `0 1px 3px ${color.colorHex}44`,
                    }}
                  />
                  <span className="text-gray-600 font-medium">{color.name}</span>
                  <span className="text-gray-300">·</span>
                  <span className="text-gray-400">{color.usageType}</span>
                  <button onClick={() => removeColor(color.id)} className="ml-0.5 text-gray-300 hover:text-red-400 transition-colors">×</button>
                </span>
              ))}
            </div>
          )}
          {!showColorPicker ? (
            <button onClick={() => setShowColorPicker(true)} className="text-xs text-violet-600 font-medium hover:text-violet-700 transition-colors">
              + Add Color
            </button>
          ) : (
            <div className="border border-gray-200 rounded-lg p-2.5 bg-gray-50/50">
              <div className="flex gap-1 mb-2">
                {(["cleansing", "energising"] as const).map((type) => (
                  <button
                    key={type}
                    onClick={() => setColorUsageType(type)}
                    className={`flex-1 text-[10px] font-medium py-1 rounded-md transition-all ${
                      colorUsageType === type
                        ? "bg-white shadow-sm text-violet-600 border border-violet-200"
                        : "text-gray-400 hover:text-gray-600"
                    }`}
                  >
                    {type.charAt(0).toUpperCase() + type.slice(1)}
                  </button>
                ))}
              </div>
              <div className="grid grid-cols-5 gap-1.5">
                {pranaColorCatalog.map((color) => {
                  const alreadyAdded = selectedColorIds.has(color.id);
                  return (
                    <button
                      key={color.id}
                      onClick={() => !alreadyAdded && addColor(color)}
                      disabled={alreadyAdded}
                      className={`flex flex-col items-center gap-0.5 p-1.5 rounded-lg transition-all ${
                        alreadyAdded ? "opacity-30 cursor-not-allowed" : "hover:bg-white hover:shadow-sm cursor-pointer"
                      }`}
                      title={`${color.name} — ${color.primaryUses}`}
                    >
                      <span
                        className="w-6 h-6 rounded-full border border-white"
                        style={{
                          background: `linear-gradient(135deg, ${color.gradientFrom}, ${color.gradientTo})`,
                          boxShadow: `0 1px 3px ${color.colorHex}33`,
                        }}
                      />
                      <span className="text-[8px] text-gray-500 font-medium leading-tight text-center">{color.code}</span>
                    </button>
                  );
                })}
              </div>
              <button onClick={() => setShowColorPicker(false)} className="mt-2 text-[10px] text-gray-400 hover:text-gray-600 transition-colors">
                Done
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
});

// ═══════════════════════════════════════════════════════════
// Main Component: ChakraAccordionPanel
// ═══════════════════════════════════════════════════════════

function ChakraAccordionPanel({
  value,
  onChange,
  protocolCatalog = PROTOCOL_CATALOG,
  pranaColorCatalog = PRANA_COLOR_CATALOG,
}: ChakraAccordionPanelProps) {
  // Build entries map from value
  const [entries, setEntries] = useState<Record<string, ChakraDocEntry>>(() => {
    const map: Record<string, ChakraDocEntry> = {};
    for (const e of value) map[e.id] = e;
    return map;
  });

  // Sync external value changes
  useEffect(() => {
    const map: Record<string, ChakraDocEntry> = {};
    for (const e of value) map[e.id] = e;
    setEntries(map);
  }, [value]);

  const [selectedRegionId, setSelectedRegionId] = useState(BODY_REGIONS[0]?.id ?? "");
  const [expandedChakraId, setExpandedChakraId] = useState<string | null>(null);

  const selectedRegion = BODY_REGIONS.find((r) => r.id === selectedRegionId);
  const chakraIds = selectedRegion?.chakraIds ?? [];

  const toggleChakra = useCallback((chakraId: string) => {
    setExpandedChakraId((prev) => (prev === chakraId ? null : chakraId));
  }, []);

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

  // Region stats
  const getRegionStats = useCallback(
    (regionId: string) => {
      const region = BODY_REGIONS.find((r) => r.id === regionId);
      if (!region) return { documented: 0, total: 0 };
      const total = region.chakraIds.length;
      const documented = region.chakraIds.filter((id) => isDocumented(entries[id])).length;
      return { documented, total };
    },
    [entries]
  );

  const totalChakras = BODY_REGIONS.reduce((s, r) => s + r.chakraIds.length, 0);
  const totalDocumented = BODY_REGIONS.reduce(
    (s, r) => s + r.chakraIds.filter((id) => isDocumented(entries[id])).length,
    0
  );
  const currentStats = getRegionStats(selectedRegionId);

  return (
    <div>
      {/* Header */}
      <label className="block text-sm font-semibold text-gray-500 mb-2 uppercase tracking-wide">
        Chakra Energy Levels
      </label>

      {/* Region Selector */}
      <div className="flex items-center gap-3 mb-3">
        <select
          value={selectedRegionId}
          onChange={(e) => {
            setSelectedRegionId(e.target.value);
            setExpandedChakraId(null);
          }}
          className="flex-1 text-sm p-2 border border-gray-200 rounded-lg bg-white outline-none focus:ring-1 focus:ring-violet-300 text-gray-700"
        >
          {BODY_REGIONS.map((region) => {
            const stats = getRegionStats(region.id);
            return (
              <option key={region.id} value={region.id}>
                {region.icon} {region.name} ({stats.documented}/{stats.total})
              </option>
            );
          })}
        </select>
        <span
          className={`text-xs px-2.5 py-1 rounded-full font-medium ${
            currentStats.documented === currentStats.total && currentStats.total > 0
              ? "bg-emerald-50 text-emerald-600"
              : currentStats.documented > 0
              ? "bg-amber-50 text-amber-600"
              : "bg-gray-100 text-gray-400"
          }`}
        >
          {totalDocumented}/{totalChakras}
        </span>
      </div>

      {/* Chakra Cards Grid */}
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
