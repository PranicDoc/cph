import { useMemo, useRef, useState } from "react";
import { X } from "lucide-react";
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
  const [input, setInput] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);
  const [selectedRow, setSelectedRow] = useState<any | null>(null);

  const addRow = () => {
    const trimmed = input.trim();
    if (!trimmed) return;
    const newRow = createNewTag ? createNewTag(trimmed) : { [displayKey]: trimmed };
    onChange([...value, newRow]);
    setInput("");
    inputRef.current?.focus();
  };

  const updateCell = (rowIdx: number, key: string, cellValue: string | boolean) => {
    const updated = [...value];
    updated[rowIdx] = { ...updated[rowIdx], [key]: cellValue };
    onChange(updated);
  };

  const deleteRow = (rowIdx: number) => {
    onChange(value.filter((_, i) => i !== rowIdx));
  };

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

  // ── Editable view ───────────────────────────────────────────────────────────
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

  const editableColumns: ColumnDef<any, unknown>[] = [
    ...columns.map(
      (col): ColumnDef<any, unknown> => ({
        id: col.key,
        header: col.label,
        cell: ({ row }) => {
          const rowIdx = row.index;
          const rowData = row.original;
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
                        updateCell(rowIdx, opt.value, checked === true)
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
              onChange={(e) => updateCell(rowIdx, col.key, e.target.value)}
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
      cell: ({ row }) => (
        <Button
          type="button"
          variant="ghost"
          size="icon-xs"
          onClick={() => deleteRow(row.index)}
          className="h-auto w-auto p-1 text-text-light/50 hover:text-danger hover:bg-transparent"
          aria-label="Delete row"
        >
          <X className="w-3.5 h-3.5" />
        </Button>
      ),
    },
  ];

  // TODO change this input to button and keep this input in the name field
  return (
    <div className="space-y-3">
      <Input
        ref={inputRef}
        type="text"
        value={input}
        onChange={(e) => setInput(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter") {
            e.preventDefault();
            addRow();
          }
        }}
        placeholder={placeholder ?? "Type to add..."}
      />

      {value.length > 0 && (
        <DataTable
          columns={editableColumns}
          data={value}
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
