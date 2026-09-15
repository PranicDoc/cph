import { useEffect, useState } from "react";
import { Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Combobox } from "@/components/common/Combobox";
import { CHAKRA_CATALOG, createDefaultEntry } from "./utils";
import {
  ENERGY_LEVEL_LABELS,
  ACTIVATION_LEVEL_LABELS,
} from "./types/spatial-chakra";
import type {
  ChakraCatalogEntry,
  ChakraDocEntry,
  EnergyLevel,
  ActivationLevel,
} from "./types/spatial-chakra";

interface ChakraManualEntryProps {
  value: ChakraDocEntry[];
  onChange: (entries: ChakraDocEntry[]) => void;
  availableChakras?: ChakraCatalogEntry[];
}

interface Row {
  /** Stable key that outlives chakra selection — a row exists (and needs a
   * React key) before any chakra has been picked for it, so it can't be
   * keyed by chakra id from the start. */
  key: string;
  entry: ChakraDocEntry | null;
}

let rowCounter = 0;
function nextRowKey() {
  rowCounter += 1;
  return `row-${rowCounter}`;
}

function entriesToRows(entries: ChakraDocEntry[]): Row[] {
  return entries.map((entry) => ({ key: entry.id, entry }));
}

/** Compact 3-way tag toggle (N/D/C or N/O/U) — replaces a dropdown for a fixed, tiny option set. */
function LevelTags({
  value,
  labels,
  onChange,
}: {
  value: string;
  labels: Record<string, string>;
  onChange: (v: string) => void;
}) {
  return (
    <div className="inline-flex gap-1.5" role="group">
      {Object.entries(labels).map(([key, label]) => {
        const selected = value === key;
        return (
          <button
            key={key}
            type="button"
            title={label}
            aria-label={label}
            aria-pressed={selected}
            onClick={() => onChange(key)}
            className={cn(
              "flex h-7 w-7 items-center justify-center rounded-full border text-xs font-semibold transition-colors",
              selected
                ? "border-primary bg-primary text-primary-foreground"
                : "border-border bg-background text-muted-foreground hover:border-primary/50 hover:text-foreground"
            )}
          >
            {key}
          </button>
        );
      })}
    </div>
  );
}

function ChakraPicker({
  chakra,
  options,
  onSelect,
}: {
  chakra: ChakraCatalogEntry | null;
  options: ChakraCatalogEntry[];
  onSelect: (chakra: ChakraCatalogEntry) => void;
}) {
  const [editing, setEditing] = useState(chakra === null);

  if (!editing && chakra) {
    return (
      <button
        type="button"
        onClick={() => setEditing(true)}
        className="flex w-full items-center gap-2 rounded-lg border border-transparent px-2 py-1.5 text-left text-sm transition-colors hover:border-border hover:bg-muted"
      >
        <span
          className="h-2 w-2 shrink-0 rounded-full"
          style={{ backgroundColor: chakra.colorHex }}
        />
        <span className="truncate font-medium">{chakra.name}</span>
      </button>
    );
  }

  return (
    <Combobox
      options={options}
      getOptionLabel={(c) => c.name}
      onSelect={(c) => {
        onSelect(c);
        setEditing(false);
      }}
      placeholder="Select chakra..."
      className="[&_input]:text-sm"
    />
  );
}

/**
 * Sparse, add/remove entry table for manual chakra scan data — unlike
 * ChakraDataGrid (one fixed row per catalog chakra), rows here only exist
 * for chakras the healer explicitly adds, each with a chakra combobox plus
 * energy/activation dropdowns. Desktop renders a table; mobile renders one
 * card per row (chakra picker on top, energy/activation as a 2-col grid
 * below) — both mounted together and toggled by breakpoint, matching the
 * pattern already used by ChakraDataGrid.
 */
export function ChakraManualEntry({
  value,
  onChange,
  availableChakras = CHAKRA_CATALOG,
}: ChakraManualEntryProps) {
  const [rows, setRows] = useState<Row[]>(() => entriesToRows(value));

  useEffect(() => {
    setRows(entriesToRows(value));
  }, [value]);

  // Keep the row set in sync with availableChakras: any chakra in the (possibly
  // protocol-filtered) list that doesn't already have a row gets a default one appended
  // — the healer never clicks "Add Chakra" for a fresh scan, AND if the case is later
  // edited to add another protocol, that protocol's chakras get appended to the
  // existing set rather than requiring a fresh scan (never removes or overwrites a row
  // that already exists, so recorded readings are always preserved).
  useEffect(() => {
    if (availableChakras.length === 0) return; // still loading — nothing to reconcile yet
    const existingIds = new Set(value.map((e) => e.id));
    const missing = availableChakras.filter((c) => !existingIds.has(c.id));
    if (missing.length === 0) return;
    onChange([...value, ...missing.map((c) => createDefaultEntry(c))]);
    // Only re-run when the resolved chakra list changes — `value`/`onChange` are read from
    // the same render that triggered this (not stale), including them as deps would loop
    // (onChange here changes `value`, which would re-trigger this effect on the *same* list).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [availableChakras]);

  function commit(nextRows: Row[]) {
    setRows(nextRows);
    onChange(
      nextRows.map((r) => r.entry).filter((e): e is ChakraDocEntry => e !== null)
    );
  }

  function addRow() {
    setRows((prev) => [...prev, { key: nextRowKey(), entry: null }]);
  }

  function removeRow(key: string) {
    commit(rows.filter((r) => r.key !== key));
  }

  function selectChakra(key: string, chakra: ChakraCatalogEntry) {
    commit(
      rows.map((r) => (r.key === key ? { ...r, entry: createDefaultEntry(chakra) } : r))
    );
  }

  function updateLevel(
    key: string,
    field: "energyLevel" | "activationLevel",
    level: EnergyLevel | ActivationLevel
  ) {
    commit(
      rows.map((r) =>
        r.key === key && r.entry ? { ...r, entry: { ...r.entry, [field]: level } } : r
      )
    );
  }

  const usedChakraIds = new Set(
    rows.map((r) => r.entry?.id).filter((id): id is string => !!id)
  );

  function rowContext(row: Row) {
    const chakra = row.entry
      ? availableChakras.find((c) => c.id === row.entry!.id) ?? null
      : null;
    const options = availableChakras.filter(
      (c) => !usedChakraIds.has(c.id) || c.id === chakra?.id
    );
    return { chakra, options };
  }

  return (
    <div className="space-y-3">
      {rows.length === 0 ? (
        <p className="rounded-xl border border-dashed border-border py-8 text-center text-sm text-muted-foreground">
          No chakras documented yet — add one below.
        </p>
      ) : (
        <>
          {/* Desktop table */}
          <div className="hidden overflow-hidden rounded-xl border border-border md:block">
            <Table>
              <TableHeader>
                <TableRow className="bg-muted/50 hover:bg-muted/50">
                  <TableHead className="w-1/2">Chakra</TableHead>
                  <TableHead>Energy</TableHead>
                  <TableHead>Activation</TableHead>
                  <TableHead className="w-10" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((row) => {
                  const { chakra, options } = rowContext(row);
                  return (
                    <TableRow key={row.key}>
                      <TableCell>
                        <ChakraPicker
                          chakra={chakra}
                          options={options}
                          onSelect={(c) => selectChakra(row.key, c)}
                        />
                      </TableCell>
                      <TableCell>
                        {row.entry && (
                          <LevelTags
                            value={row.entry.energyLevel}
                            labels={ENERGY_LEVEL_LABELS}
                            onChange={(v) =>
                              updateLevel(row.key, "energyLevel", v as EnergyLevel)
                            }
                          />
                        )}
                      </TableCell>
                      <TableCell>
                        {row.entry && (
                          <LevelTags
                            value={row.entry.activationLevel}
                            labels={ACTIVATION_LEVEL_LABELS}
                            onChange={(v) =>
                              updateLevel(row.key, "activationLevel", v as ActivationLevel)
                            }
                          />
                        )}
                      </TableCell>
                      <TableCell>
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon-sm"
                          onClick={() => removeRow(row.key)}
                          aria-label="Remove chakra"
                          className="text-muted-foreground hover:text-destructive"
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </div>

          {/* Mobile cards */}
          <div className="space-y-3 md:hidden">
            {rows.map((row) => {
              const { chakra, options } = rowContext(row);
              return (
                <div key={row.key} className="rounded-xl border border-border p-3">
                  <div className="mb-3 flex items-center gap-2">
                    <div className="flex-1">
                      <ChakraPicker
                        chakra={chakra}
                        options={options}
                        onSelect={(c) => selectChakra(row.key, c)}
                      />
                    </div>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon-sm"
                      onClick={() => removeRow(row.key)}
                      aria-label="Remove chakra"
                      className="shrink-0 text-muted-foreground hover:text-destructive"
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                  {row.entry && (
                    <div className="grid grid-cols-2 gap-3">
                      <LevelTags
                        value={row.entry.energyLevel}
                        labels={ENERGY_LEVEL_LABELS}
                        onChange={(v) =>
                          updateLevel(row.key, "energyLevel", v as EnergyLevel)
                        }
                      />
                      <LevelTags
                        value={row.entry.activationLevel}
                        labels={ACTIVATION_LEVEL_LABELS}
                        onChange={(v) =>
                          updateLevel(row.key, "activationLevel", v as ActivationLevel)
                        }
                      />
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </>
      )}

      <Button type="button" variant="outline" onClick={addRow} className="gap-1.5">
        <Plus className="h-4 w-4" />
        Add Chakra
      </Button>
    </div>
  );
}
