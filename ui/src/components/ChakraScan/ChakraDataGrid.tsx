import { useCallback, useEffect, useMemo, useState } from "react";
import type { ColumnDef } from "@tanstack/react-table";
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
} from "./types/spatial-chakra";
import { DataTable } from "@/components/ui/data-table";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { cn } from "@/lib/utils";

interface ChakraDataGridProps {
  value: ChakraDocEntry[];
  onChange: (entries: ChakraDocEntry[]) => void;
  availableChakras?: ChakraCatalogEntry[];
  protocolCatalog?: ProtocolCatalogEntry[];
  pranaColorCatalog?: PranaColorEntry[];
}

// Borderless-until-hover inline control styling for the spreadsheet cells
const gridControlCls =
  "w-full text-sm p-1.5 border border-transparent hover:border-gray-300 focus-visible:border-violet-400 rounded rounded-md bg-transparent focus-visible:bg-white outline-none transition-all cursor-pointer";

const mobileControlCls =
  "text-sm p-2 border border-gray-200 rounded-lg bg-gray-50 focus-visible:bg-white focus-visible:border-violet-300";

function emptyEntry(chakra: ChakraCatalogEntry): ChakraDocEntry {
  return {
    id: chakra.id,
    name: chakra.name,
    code: chakra.code,
    color: chakra.colorHex,
    energyLevel: "N" as EnergyLevel,
    activationLevel: "N" as ActivationLevel,
    treatmentColors: [],
    notes: "",
  };
}

function LevelSelect({
  value,
  labels,
  onChange,
  className,
}: {
  value: string;
  labels: Record<string, string>;
  onChange: (v: string) => void;
  className?: string;
}) {
  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger className={className}>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {Object.entries(labels).map(([key, label]) => (
          <SelectItem key={key} value={key}>
            {label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
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

        const existingEntry = prev[chakraId] || emptyEntry(chakra);
        const updatedEntry = { ...existingEntry, [field]: newValue };
        const nextMap = { ...prev, [chakraId]: updatedEntry };

        onChange(Object.values(nextMap));
        return nextMap;
      });
    },
    [chakraCatalog, onChange]
  );

  const totalChakras = chakraCatalog.length;
  const documentedChakras = Object.values(entriesMap).filter(
    (entry) =>
      entry.energyLevel !== "N" ||
      entry.activationLevel !== "N" ||
      entry.treatmentColors?.length > 0 ||
      entry.notes
  ).length;

  // One row per catalog chakra, hydrated with any documented entry
  const rows = useMemo(
    () =>
      chakraCatalog.map((chakra) => ({
        chakra,
        entry: entriesMap[chakra.id] ?? emptyEntry(chakra),
      })),
    [chakraCatalog, entriesMap]
  );

  type GridRow = (typeof rows)[number];

  const columns: ColumnDef<GridRow, unknown>[] = [
    {
      id: "chakra",
      header: "Chakra",
      meta: {
        headClassName:
          "p-3 font-semibold w-40 sticky left-0 bg-gray-100/80 z-10 shadow-[1px_0_0_0_#e5e7eb] text-gray-500 normal-case uppercase tracking-wider",
        cellClassName:
          "p-2 sticky left-0 bg-white group-hover:bg-violet-50 z-10 shadow-[1px_0_0_0_#f3f4f6]",
      },
      cell: ({ row }) => (
        <div className="font-medium text-gray-800 text-sm pl-1 flex items-center gap-2">
          <span
            className="w-2 h-2 rounded-full"
            style={{ backgroundColor: row.original.chakra.colorHex }}
          ></span>
          {row.original.chakra.name}
        </div>
      ),
    },
    {
      id: "energyLevel",
      header: "Energy Level",
      meta: { headClassName: "p-3 font-semibold w-48 text-gray-500 tracking-wider" },
      cell: ({ row }) => (
        <LevelSelect
          value={row.original.entry.energyLevel}
          labels={ENERGY_LEVEL_LABELS}
          onChange={(v) =>
            handleUpdate(row.original.chakra.id, "energyLevel", v as EnergyLevel)
          }
          className={gridControlCls}
        />
      ),
    },
    {
      id: "activationLevel",
      header: "Activation",
      meta: { headClassName: "p-3 font-semibold w-40 text-gray-500 tracking-wider" },
      cell: ({ row }) => (
        <LevelSelect
          value={row.original.entry.activationLevel}
          labels={ACTIVATION_LEVEL_LABELS}
          onChange={(v) =>
            handleUpdate(
              row.original.chakra.id,
              "activationLevel",
              v as ActivationLevel
            )
          }
          className={gridControlCls}
        />
      ),
    },
    {
      id: "notes",
      header: "Notes",
      meta: { headClassName: "p-3 font-semibold min-w-[200px] text-gray-500 tracking-wider" },
      cell: ({ row }) => (
        <Input
          type="text"
          placeholder="Notes..."
          value={row.original.entry.notes || ""}
          onChange={(e) =>
            handleUpdate(row.original.chakra.id, "notes", e.target.value)
          }
          className={cn(gridControlCls, "cursor-text")}
        />
      ),
    },
  ];

  return (
    <div className="flex flex-col border border-gray-200 rounded-xl bg-white shadow-sm overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b bg-gray-50/50 sticky top-0 z-20">
        <Label className="text-sm font-semibold text-gray-700 uppercase tracking-wide">
          Chakra Assessment
        </Label>
        <Badge
          className={cn(
            "px-2.5 py-1 h-auto font-medium",
            documentedChakras === totalChakras && totalChakras > 0
              ? "bg-emerald-50 text-emerald-600"
              : documentedChakras > 0
              ? "bg-amber-50 text-amber-600"
              : "bg-gray-100 text-gray-400"
          )}
        >
          {documentedChakras}/{totalChakras} Documented
        </Badge>
      </div>

      {/* ========================================= */}
      {/* MOBILE VIEW: Vertical Cards (Hidden on md+) */}
      {/* ========================================= */}
      <div className="block md:hidden divide-y divide-gray-100 bg-gray-50/50">
        {rows.map(({ chakra, entry }) => (
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
                <Label className="text-xs text-gray-500 font-medium">
                  Energy
                </Label>
                <LevelSelect
                  value={entry.energyLevel}
                  labels={ENERGY_LEVEL_LABELS}
                  onChange={(v) =>
                    handleUpdate(chakra.id, "energyLevel", v as EnergyLevel)
                  }
                  className={mobileControlCls}
                />
              </div>

              <div className="flex flex-col gap-1">
                <Label className="text-xs text-gray-500 font-medium">
                  Activation
                </Label>
                <LevelSelect
                  value={entry.activationLevel}
                  labels={ACTIVATION_LEVEL_LABELS}
                  onChange={(v) =>
                    handleUpdate(
                      chakra.id,
                      "activationLevel",
                      v as ActivationLevel
                    )
                  }
                  className={mobileControlCls}
                />
              </div>
            </div>

            <div className="flex flex-col gap-1">
              <Label className="text-xs text-gray-500 font-medium">
                Notes
              </Label>
              <Input
                type="text"
                placeholder="Add notes..."
                value={entry.notes || ""}
                onChange={(e) =>
                  handleUpdate(chakra.id, "notes", e.target.value)
                }
                className={mobileControlCls}
              />
            </div>
          </div>
        ))}
      </div>

      {/* ========================================= */}
      {/* DESKTOP VIEW: Spreadsheet (Hidden on sm)  */}
      {/* ========================================= */}
      <div className="hidden md:block w-full overflow-x-auto no-scrollbar">
        <DataTable<GridRow>
          columns={columns}
          data={rows}
          enableSorting={false}
          compact
          className="rounded-none border-0 min-w-[900px]"
          headerRowClassName="bg-gray-100/80 hover:bg-gray-100/80 border-b border-gray-200"
          rowClassName="group hover:bg-violet-50/30 transition-colors border-b border-gray-100"
          cellClassName="p-2"
        />
      </div>
    </div>
  );
}

export default ChakraDataGrid;
