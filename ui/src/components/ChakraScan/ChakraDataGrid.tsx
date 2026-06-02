import { useCallback, useEffect, useState } from "react";
import { PROTOCOL_CATALOG } from "./data/protocol-catalog";
import { PRANA_COLOR_CATALOG } from "./data/prana-color-catalog";
import { CHAKRA_CATALOG } from "./utils";
import {
  ENERGY_LEVEL_LABELS,
  ACTIVATION_LEVEL_LABELS,
} from "./types/spatial-chakra";
import type {
  ChakraCatalogEntry,
  ChakraDocEntry,
  ProtocolCatalogEntry,
  PranaColorEntry,
  EnergyLevel,
  ActivationLevel,
  TreatmentColor,
} from "./types/spatial-chakra";

interface ChakraDataGridProps {
  value: ChakraDocEntry[];
  onChange: (entries: ChakraDocEntry[]) => void;
  availableChakras?: ChakraCatalogEntry[];
  protocolCatalog?: ProtocolCatalogEntry[];
  pranaColorCatalog?: PranaColorEntry[];
}

export function ChakraDataGrid({
  value,
  onChange,
  availableChakras,
  protocolCatalog = PROTOCOL_CATALOG,
  pranaColorCatalog = PRANA_COLOR_CATALOG,
}: ChakraDataGridProps) {
  const [entriesMap, setEntriesMap] = useState<Record<string, ChakraDocEntry>>(
    () => {
      return value.reduce((acc, entry) => {
        acc[entry.id] = entry;
        return acc;
      }, {} as Record<string, ChakraDocEntry>);
    }
  );

  const chakraCatalog = availableChakras ?? CHAKRA_CATALOG;

  useEffect(() => {
    setEntriesMap(
      value.reduce((acc, entry) => {
        acc[entry.id] = entry;
        return acc;
      }, {} as Record<string, ChakraDocEntry>)
    );
  }, [value]);

  const handleUpdate = useCallback(
    <K extends keyof ChakraDocEntry>(
      chakraId: string,
      field: K,
      newValue: ChakraDocEntry[K]
    ) => {
      setEntriesMap((prev) => {
        const chakra = chakraCatalog.find((c) => c.id === chakraId);
        if (!chakra) return prev;

        const existingEntry = prev[chakraId] || {
          id: chakra.id,
          name: chakra.name,
          code: chakra.code,
          color: chakra.colorHex,
          energyLevel: "N" as EnergyLevel,
          activationLevel: "N" as ActivationLevel,
          treatmentColors: [],
          notes: "",
        };

        const updatedEntry = { ...existingEntry, [field]: newValue };
        const nextMap = { ...prev, [chakraId]: updatedEntry };

        onChange(Object.values(nextMap));
        return nextMap;
      });
    },
    [chakraCatalog, onChange]
  );

  const toggleTreatmentColor = (
    chakraId: string,
    color: PranaColorEntry,
    currentEntry: ChakraDocEntry
  ) => {
    const currentColors = currentEntry.treatmentColors || [];
    const exists = currentColors.find((c) => c.id === color.id);

    let newColors: TreatmentColor[];
    if (exists) {
      newColors = currentColors.filter((c) => c.id !== color.id);
    } else {
      newColors = [
        ...currentColors,
        {
          id: color.id,
          name: color.name,
          colorHex: color.colorHex,
          usageType: "energising",
        },
      ];
    }
    handleUpdate(chakraId, "treatmentColors", newColors);
  };

  const totalChakras = chakraCatalog.length;
  const documentedChakras = Object.values(entriesMap).filter(
    (entry) =>
      entry.energyLevel !== "N" ||
      entry.activationLevel !== "N" ||
      entry.treatmentColors?.length > 0 ||
      entry.notes
  ).length;

  return (
    <div className="flex flex-col border border-gray-200 rounded-xl bg-white shadow-sm overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b bg-gray-50/50 sticky top-0 z-20">
        <label className="text-sm font-semibold text-gray-700 uppercase tracking-wide">
          Chakra Assessment
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

      {/* ========================================= */}
      {/* MOBILE VIEW: Vertical Cards (Hidden on md+) */}
      {/* ========================================= */}
      <div className="block md:hidden divide-y divide-gray-100 bg-gray-50/50">
        {chakraCatalog.map((chakra) => {
          const entry = entriesMap[chakra.id] ?? {
            id: chakra.id,
            name: chakra.name,
            code: chakra.code,
            color: chakra.colorHex,
            energyLevel: "N" as EnergyLevel,
            activationLevel: "N" as ActivationLevel,
            treatmentColors: [],
            notes: "",
          };

          return (
            <div key={chakra.id} className="p-4 bg-white">
              <div className="font-semibold text-gray-800 text-base mb-3 flex items-center gap-2">
                <span
                  className="w-3 h-3 rounded-full"
                  style={{ backgroundColor: chakra.colorHex }}
                ></span>
                {chakra.name}
              </div>

              <div className="grid grid-cols-2 gap-3 mb-3">
                <div className="flex flex-col gap-1">
                  <label className="text-xs text-gray-500 font-medium">
                    Energy
                  </label>
                  <select
                    value={entry.energyLevel}
                    onChange={(e) =>
                      handleUpdate(
                        chakra.id,
                        "energyLevel",
                        e.target.value as EnergyLevel
                      )
                    }
                    className="text-sm p-2 border border-gray-200 rounded-lg bg-gray-50 focus:bg-white outline-none focus:ring-1 focus:ring-violet-300"
                  >
                    {Object.entries(ENERGY_LEVEL_LABELS).map(([key, label]) => (
                      <option key={key} value={key}>
                        {label}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="flex flex-col gap-1">
                  <label className="text-xs text-gray-500 font-medium">
                    Activation
                  </label>
                  <select
                    value={entry.activationLevel}
                    onChange={(e) =>
                      handleUpdate(
                        chakra.id,
                        "activationLevel",
                        e.target.value as ActivationLevel
                      )
                    }
                    className="text-sm p-2 border border-gray-200 rounded-lg bg-gray-50 focus:bg-white outline-none focus:ring-1 focus:ring-violet-300"
                  >
                    {Object.entries(ACTIVATION_LEVEL_LABELS).map(
                      ([key, label]) => (
                        <option key={key} value={key}>
                          {label}
                        </option>
                      )
                    )}
                  </select>
                </div>
              </div>

              {/* <div className="flex flex-col gap-1 mb-3">
                <label className="text-xs text-gray-500 font-medium">
                  Colors Applied
                </label>
                <select
                  value=""
                  onChange={(e) => {
                    if (e.target.value) {
                      const colorObj = pranaColorCatalog.find(
                        (c) => c.id === e.target.value
                      );
                      if (colorObj)
                        toggleTreatmentColor(chakra.id, colorObj, entry);
                    }
                  }}
                  className="text-sm p-2 border border-gray-200 rounded-lg bg-gray-50 focus:bg-white outline-none focus:ring-1 focus:ring-violet-300"
                >
                  <option value="">+ Add Color</option>
                  {pranaColorCatalog.map((color) => (
                    <option key={color.id} value={color.id}>
                      {entry.treatmentColors?.find((c) => c.id === color.id)
                        ? "✓ "
                        : ""}
                      {color.name}
                    </option>
                  ))}
                </select>

                {entry.treatmentColors && entry.treatmentColors.length > 0 && (
                  <div className="flex flex-wrap gap-1 mt-1">
                    {entry.treatmentColors.map((tc) => (
                      <span
                        key={tc.id}
                        onClick={() => {
                          const colorRef = pranaColorCatalog.find(
                            (c) => c.id === tc.id
                          );
                          if (colorRef)
                            toggleTreatmentColor(chakra.id, colorRef, entry);
                        }}
                        className="inline-flex items-center text-xs px-2 py-1 rounded font-medium cursor-pointer"
                        style={{
                          backgroundColor: `${tc.colorHex}22`,
                          color: tc.colorHex,
                          border: `1px solid ${tc.colorHex}44`,
                        }}
                      >
                        {tc.name} &times;
                      </span>
                    ))}
                  </div>
                )}
              </div> */}

              <div className="flex flex-col gap-1">
                <label className="text-xs text-gray-500 font-medium">
                  Notes
                </label>
                <input
                  type="text"
                  placeholder="Add notes..."
                  value={entry.notes || ""}
                  onChange={(e) =>
                    handleUpdate(chakra.id, "notes", e.target.value)
                  }
                  className="text-sm p-2 border border-gray-200 rounded-lg bg-gray-50 focus:bg-white outline-none focus:ring-1 focus:ring-violet-300"
                />
              </div>
            </div>
          );
        })}
      </div>

      {/* ========================================= */}
      {/* DESKTOP VIEW: Spreadsheet (Hidden on sm)  */}
      {/* ========================================= */}
      <div className="hidden md:block w-full overflow-x-auto no-scrollbar">
        <table className="w-full text-left border-collapse min-w-[900px]">
          <thead>
            <tr className="bg-gray-100/80 border-b border-gray-200 text-xs uppercase text-gray-500 tracking-wider">
              <th className="p-3 font-semibold w-40 sticky left-0 bg-gray-100/80 z-10 shadow-[1px_0_0_0_#e5e7eb]">
                Chakra
              </th>
              <th className="p-3 font-semibold w-48">Energy Level</th>
              <th className="p-3 font-semibold w-40">Activation</th>
              {/* <th className="p-3 font-semibold w-48">Protocol</th> */}
              {/* <th className="p-3 font-semibold w-48">Colors Applied</th> */}
              <th className="p-3 font-semibold min-w-[200px]">Notes</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {chakraCatalog.map((chakra) => {
              const entry = entriesMap[chakra.id] ?? {
                id: chakra.id,
                name: chakra.name,
                code: chakra.code,
                color: chakra.colorHex,
                energyLevel: "N" as EnergyLevel,
                activationLevel: "N" as ActivationLevel,
                treatmentColors: [],
                notes: "",
              };

              return (
                <tr
                  key={chakra.id}
                  className="hover:bg-violet-50/30 transition-colors group"
                >
                  <td className="p-2 sticky left-0 bg-white group-hover:bg-violet-50 z-10 shadow-[1px_0_0_0_#f3f4f6]">
                    <div className="font-medium text-gray-800 text-sm pl-1 flex items-center gap-2">
                      <span
                        className="w-2 h-2 rounded-full"
                        style={{ backgroundColor: chakra.colorHex }}
                      ></span>
                      {chakra.name}
                    </div>
                  </td>
                  {/* ... The rest of the desktop <td> elements remain exactly the same as the previous version ... */}
                  <td className="p-2">
                    <select
                      value={entry.energyLevel}
                      onChange={(e) =>
                        handleUpdate(
                          chakra.id,
                          "energyLevel",
                          e.target.value as EnergyLevel
                        )
                      }
                      className="w-full text-sm p-1.5 border border-transparent hover:border-gray-300 focus:border-violet-400 rounded bg-transparent focus:bg-white outline-none transition-all cursor-pointer"
                    >
                      {Object.entries(ENERGY_LEVEL_LABELS).map(
                        ([key, label]) => (
                          <option key={key} value={key}>
                            {label}
                          </option>
                        )
                      )}
                    </select>
                  </td>
                  <td className="p-2">
                    <select
                      value={entry.activationLevel}
                      onChange={(e) =>
                        handleUpdate(
                          chakra.id,
                          "activationLevel",
                          e.target.value as ActivationLevel
                        )
                      }
                      className="w-full text-sm p-1.5 border border-transparent hover:border-gray-300 focus:border-violet-400 rounded bg-transparent focus:bg-white outline-none transition-all cursor-pointer"
                    >
                      {Object.entries(ACTIVATION_LEVEL_LABELS).map(
                        ([key, label]) => (
                          <option key={key} value={key}>
                            {label}
                          </option>
                        )
                      )}
                    </select>
                  </td>
                  {/* <td className="p-2">
                    <select
                      value={entry.protocol?.id || ""}
                      onChange={(e) => {
                        const selectedId = e.target.value;
                        if (!selectedId) {
                          handleUpdate(chakra.id, "protocol", undefined);
                          return;
                        }
                        const protocolRef = protocolCatalog.find(
                          (p) => p.id === selectedId
                        );
                        if (protocolRef) {
                          handleUpdate(chakra.id, "protocol", {
                            id: protocolRef.id,
                            name: protocolRef.name,
                            category: protocolRef.category,
                            level: protocolRef.level,
                          });
                        }
                      }}
                      className="w-full text-sm p-1.5 border border-transparent hover:border-gray-300 focus:border-violet-400 rounded bg-transparent focus:bg-white outline-none transition-all cursor-pointer"
                    >
                      <option value="">-- None --</option>
                      {protocolCatalog.map((proto) => (
                        <option key={proto.id} value={proto.id}>
                          {proto.name}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td className="p-2">
                    <select
                      value=""
                      onChange={(e) => {
                        if (e.target.value) {
                          const colorObj = pranaColorCatalog.find(
                            (c) => c.id === e.target.value
                          );
                          if (colorObj)
                            toggleTreatmentColor(chakra.id, colorObj, entry);
                        }
                      }}
                      className="w-full text-sm p-1.5 border border-transparent hover:border-gray-300 focus:border-violet-400 rounded bg-transparent focus:bg-white outline-none transition-all cursor-pointer"
                    >
                      <option value="">+ Add Color</option>
                      {pranaColorCatalog.map((color) => (
                        <option key={color.id} value={color.id}>
                          {entry.treatmentColors?.find((c) => c.id === color.id)
                            ? "✓ "
                            : ""}
                          {color.name}
                        </option>
                      ))}
                    </select>
                    {entry.treatmentColors &&
                      entry.treatmentColors.length > 0 && (
                        <div className="flex flex-wrap gap-1 mt-1 pl-1">
                          {entry.treatmentColors.map((tc) => (
                            <span
                              key={tc.id}
                              onClick={() => {
                                const colorRef = pranaColorCatalog.find(
                                  (c) => c.id === tc.id
                                );
                                if (colorRef)
                                  toggleTreatmentColor(
                                    chakra.id,
                                    colorRef,
                                    entry
                                  );
                              }}
                              className="inline-flex items-center text-[10px] px-1.5 py-0.5 rounded-sm font-medium cursor-pointer hover:opacity-80"
                              style={{
                                backgroundColor: `${tc.colorHex}33`,
                                color: tc.colorHex,
                                border: `1px solid ${tc.colorHex}`,
                              }}
                            >
                              {tc.name} &times;
                            </span>
                          ))}
                        </div>
                      )}
                  </td> */}
                  <td className="p-2">
                    <input
                      type="text"
                      placeholder="Notes..."
                      value={entry.notes || ""}
                      onChange={(e) =>
                        handleUpdate(chakra.id, "notes", e.target.value)
                      }
                      className="w-full text-sm p-1.5 border border-transparent hover:border-gray-300 focus:border-violet-400 rounded bg-transparent focus:bg-white outline-none transition-all"
                    />
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default ChakraDataGrid;
