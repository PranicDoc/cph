import { useUiVersion } from "../ui-version";
import { useMemo, useRef, useState } from "react";
import { Plus, X } from "lucide-react";
import type { ColumnDef } from "@tanstack/react-table";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Checkbox } from "@/components/ui/checkbox";
import { DataTable } from "@/components/ui/data-table";

interface EditableFieldConfig {
  label: string;
  placeholder?: string;
  type?: "text" | "checkbox-group";
  options?: { label: string; value: string }[];
}

interface TableInputProps {
  value: any[];
  onChange: (value: any[]) => void;
  placeholder?: string;
  displayKey: string;
  editableFields?: Record<string, EditableFieldConfig>;
  createNewTag?: (input: string) => any;
  readOnly?: boolean;
}

function camelToLabel(key: string): string {
  return key
    .replace(/([A-Z])/g, " $1")
    .replace(/^./, (s) => s.toUpperCase())
    .trim();
}

function MedicationPopup({
  row,
  displayKey,
  editableFields,
  onClose,
}: {
  row: any;
  displayKey: string;
  editableFields?: Record<string, EditableFieldConfig>;
  onClose: () => void;
}) {
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="w-80 p-6 gap-4 space-y-0">
        <div className="flex items-center justify-between">
          <DialogTitle className="font-semibold text-text-dark text-base">
            {row[displayKey]}
          </DialogTitle>
        </div>

        <div className="space-y-3 text-sm">
          {Object.entries(editableFields ?? {}).map(([key, cfg]) => {
            if (cfg.type === "checkbox-group" && cfg.options) {
              const active = cfg.options
                .filter((opt) => !!row[opt.value])
                .map((opt) => opt.label);
              return (
                <div key={key} className="flex justify-between items-center">
                  <span className="text-text-light">{cfg.label}</span>
                  <span className="font-medium text-right">
                    {active.length ? active.join(", ") : "—"}
                  </span>
                </div>
              );
            }
            return (
              <div key={key} className="flex justify-between items-center">
                <span className="text-text-light">{cfg.label}</span>
                <span className="font-medium">{row[key] || "—"}</span>
              </div>
            );
          })}
          {row.notes && (
            <div className="flex justify-between items-start gap-2">
              <span className="text-text-light shrink-0">Notes</span>
              <span className="font-medium text-right">{row.notes}</span>
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}

export function TableInput({
  value,
  onChange,
  placeholder,
  displayKey,
  editableFields,
  createNewTag,
  readOnly = false,
}: TableInputProps) {
  const { isNewView } = useUiVersion();
  const [selectedRow, setSelectedRow] = useState<any | null>(null);

  const addRow = () => {
    const newRow = createNewTag ? createNewTag("") : { [displayKey]: "" };
    onChange([...value, newRow]);
  };

  const updateCell = (
    rowIdx: number,
    key: string,
    cellValue: string | boolean
  ) => {
    const updated = [...value];
    updated[rowIdx] = { ...updated[rowIdx], [key]: cellValue };
    onChange(updated);
  };

  const deleteRow = (rowIdx: number) => {
    onChange(value.filter((_, i) => i !== rowIdx));
  };

  // updateCell/deleteRow close over `value`/`onChange`, which change on every keystroke —
  // routed through `meta` (not closed over inside `editableColumns` below) so `columns`
  // itself stays referentially stable. See data-table.tsx's DataTableRowProps comment: an
  // unstable `columns` array makes flexRender treat each cell as a brand-new component type
  // and fully remount it every render, which was silently stealing focus from this table's
  // text inputs after every character typed.
  const editableMeta = useMemo(() => ({ updateCell, deleteRow }), [value, onChange]);

  const textCols = useMemo(
    () =>
      Object.entries(editableFields ?? {}).filter(
        ([, cfg]) => cfg.type !== "checkbox-group"
      ),
    [editableFields]
  );
  const checkboxCols = useMemo(
    () =>
      Object.entries(editableFields ?? {}).filter(
        ([, cfg]) => cfg.type === "checkbox-group"
      ),
    [editableFields]
  );

  // ── Read-only view ──────────────────────────────────────────────────────────
  const readOnlyColumns = useMemo<ColumnDef<any, unknown>[]>(() => {
    const cols: ColumnDef<any, unknown>[] = [
      {
        id: displayKey,
        header: camelToLabel(displayKey),
        cell: ({ row }) => (
          <span className="font-medium text-primary underline underline-offset-2 decoration-primary/40">
            {row.original[displayKey]}
          </span>
        ),
      },
      ...textCols.map(
        ([key, cfg]): ColumnDef<any, unknown> => ({
          id: key,
          header: cfg.label,
          cell: ({ row }) => (
            <span className="text-text-light">{row.original[key] || "—"}</span>
          ),
        })
      ),
    ];
    if (checkboxCols.length > 0) {
      cols.push({
        id: "timing",
        header: checkboxCols[0][1].label,
        cell: ({ row }) => {
          const timing =
            checkboxCols
              .flatMap(([, cfg]) =>
                (cfg.options ?? [])
                  .filter((opt) => !!row.original[opt.value])
                  .map((opt) => opt.label)
              )
              .join(", ") || "—";
          return <span className="text-text-light text-xs">{timing}</span>;
        },
      });
    }
    return cols;
  }, [displayKey, textCols, checkboxCols]);

  // Editable-view columns. Declared BEFORE the `readOnly` early return below: hooks must run in
  // the same order every render, and `readOnly` can flip on a mounted table (e.g. a session
  // leaving DRAFT). Calling this after the early return threw React error #300.
  // Stable across keystrokes (depends only on displayKey/editableFields, never on
  // value/onChange) — cell renderers pull the current updateCell/deleteRow off
  // table.options.meta (editableMeta above) instead of closing over them, so this
  // array's identity never changes while typing. See editableMeta's comment for why
  // that matters: an unstable columns array was remounting the <input> on every key.
  const editableColumns = useMemo<ColumnDef<any, unknown>[]>(() => {
    const columns = [
      {
        key: displayKey,
        label: camelToLabel(displayKey),
        type: "text" as const,
        options: undefined as { label: string; value: string }[] | undefined,
        placeholder: undefined as string | undefined,
      },
      ...Object.entries(editableFields ?? {}).map(([key, cfg]) => ({
        key,
        label: cfg.label,
        type: cfg.type ?? "text",
        options: cfg.options,
        placeholder: cfg.placeholder,
      })),
    ];

    return [
      ...columns.map(
        (col): ColumnDef<any, unknown> => ({
          id: col.key,
          header: col.label,
          cell: ({ row, table }) => {
            const rowIdx = row.index;
            const rowData = row.original;
            const meta = table.options.meta as
              | { updateCell: typeof updateCell; deleteRow: typeof deleteRow }
              | undefined;
            if (col.type === "checkbox-group" && col.options) {
              return (
                <div className="flex flex-col gap-1 px-1">
                  {col.options.map((opt) => (
                    <label
                      key={opt.value}
                      className="flex items-center gap-1.5 text-xs cursor-pointer select-none"
                    >
                      <Checkbox
                        checked={!!rowData[opt.value]}
                        onCheckedChange={(checked) =>
                          meta?.updateCell(rowIdx, opt.value, checked === true)
                        }
                        className="w-3.5 h-3.5"
                      />
                      <span>{opt.label}</span>
                    </label>
                  ))}
                </div>
              );
            }
            return (
              <Input
                type="text"
                value={rowData[col.key] ?? ""}
                onChange={(e) => meta?.updateCell(rowIdx, col.key, e.target.value)}
                placeholder={col.placeholder}
                className="p-1.5 border border-transparent rounded rounded-md bg-transparent hover:border-border focus-visible:border-primary"
              />
            );
          },
        })
      ),
      {
        id: "delete",
        header: "",
        meta: { headClassName: "w-10", cellClassName: "px-2 py-1.5 text-center" },
        cell: ({ row, table }) => {
          const meta = table.options.meta as { deleteRow: typeof deleteRow } | undefined;
          return (
            <Button
              type="button"
              variant="ghost"
              size="icon-xs"
              onClick={() => meta?.deleteRow(row.index)}
              className="h-auto w-auto p-1 text-text-light/50 hover:text-danger hover:bg-transparent"
              aria-label="Delete row"
            >
              <X className="w-3.5 h-3.5" />
            </Button>
          );
        },
      },
    ];
    // eslint-disable-next-line react-hooks/exhaustive-deps -- deliberately excludes
    // updateCell/deleteRow (recreated every render via value/onChange) so this array
    // stays referentially stable; current versions are read from meta at call time.
  }, [displayKey, editableFields]);

  if (readOnly) {
    if (value.length === 0) {
      return <p className="text-sm text-text-light/50 italic">No entries</p>;
    }

    return (
      <>
        <DataTable
          columns={readOnlyColumns}
          data={value}
          enableSorting={false}
          className="rounded-lg"
          headerRowClassName="bg-background hover:bg-background"
          headCellClassName="px-3 py-2 font-semibold tracking-wider"
          cellClassName="px-3 py-2"
          rowClassName="bg-white hover:bg-primary/5"
          onRowClick={(row) => setSelectedRow(row.original)}
        />

        {selectedRow && (
          <MedicationPopup
            row={selectedRow}
            displayKey={displayKey}
            editableFields={editableFields}
            onClose={() => setSelectedRow(null)}
          />
        )}
      </>
    );
  }

  if (isNewView) {
    const fields = Object.entries(editableFields ?? {});
    const addLabel = placeholder ?? (displayKey.toLowerCase().includes("medic") ? "Add medicine" : "Add row");
    return (
      <div>
        <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 4 }}>
          <button type="button" className="btn btn-ghost" onClick={addRow}>
            <Plus className="ico" /> {addLabel}
          </button>
        </div>
        {value.map((row: any, idx: number) => (
          <div key={idx} className="medrow">
            <input
              className="input"
              aria-label={camelToLabel(displayKey)}
              placeholder={camelToLabel(displayKey)}
              value={row[displayKey] ?? ""}
              onChange={(e) => updateCell(idx, displayKey, e.target.value)}
            />
            {fields.map(([key, cfg]) =>
              cfg.type === "checkbox-group" && cfg.options ? (
                <div key={key} style={{ display: "flex", gap: 14, flexWrap: "wrap" }}>
                  {cfg.options.map((opt) => (
                    <label key={opt.value} style={{ display: "flex", alignItems: "center", gap: 8, cursor: "pointer" }}>
                      <Checkbox checked={!!row[opt.value]} onCheckedChange={(c) => updateCell(idx, opt.value, c === true)} />
                      <span>{opt.label}</span>
                    </label>
                  ))}
                </div>
              ) : (
                <input
                  key={key}
                  className="input"
                  aria-label={cfg.label}
                  placeholder={cfg.placeholder ?? cfg.label}
                  value={row[key] ?? ""}
                  onChange={(e) => updateCell(idx, key, e.target.value)}
                />
              )
            )}
            <button type="button" className="icon-btn danger" aria-label="Delete row" onClick={() => deleteRow(idx)}>
              <X className="ico" />
            </button>
          </div>
        ))}
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <Button
        type="button"
        variant="outline"
        onClick={addRow}
        className="flex items-center gap-2"
      >
        <Plus className="w-4 h-4" />
        {placeholder ?? "Add Row"}
      </Button>

      {value.length > 0 && (
        <DataTable
          columns={editableColumns}
          data={value}
          meta={editableMeta}
          enableSorting={false}
          className="rounded-lg border-2"
          headerRowClassName="bg-background hover:bg-background"
          headCellClassName="px-3 py-2 font-semibold tracking-wider"
          cellClassName="px-2 py-1.5"
          rowClassName="bg-white hover:bg-background/50"
        />
      )}
    </div>
  );
}
