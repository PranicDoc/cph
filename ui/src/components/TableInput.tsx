import { useRef, useState } from "react";
import { X } from "lucide-react";

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
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      onClick={onClose}
    >
      <div
        className="bg-white rounded-xl shadow-xl p-6 w-80 space-y-4"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between">
          <h3 className="font-semibold text-text-dark text-base">{row[displayKey]}</h3>
          <button
            type="button"
            onClick={onClose}
            className="text-text-light hover:text-danger transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
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
      </div>
    </div>
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

  // ── Read-only view ──────────────────────────────────────────────────────────
  if (readOnly) {
    if (value.length === 0) {
      return <p className="text-sm text-text-light/50 italic">No entries</p>;
    }

    const textCols = Object.entries(editableFields ?? {}).filter(
      ([, cfg]) => cfg.type !== "checkbox-group"
    );
    const checkboxCols = Object.entries(editableFields ?? {}).filter(
      ([, cfg]) => cfg.type === "checkbox-group"
    );

    return (
      <>
        <div className="border border-border rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-background border-b border-border">
                <th className="px-3 py-2 text-left text-xs font-semibold text-text-light uppercase tracking-wider">
                  {camelToLabel(displayKey)}
                </th>
                {textCols.map(([key, cfg]) => (
                  <th
                    key={key}
                    className="px-3 py-2 text-left text-xs font-semibold text-text-light uppercase tracking-wider"
                  >
                    {cfg.label}
                  </th>
                ))}
                {checkboxCols.length > 0 && (
                  <th className="px-3 py-2 text-left text-xs font-semibold text-text-light uppercase tracking-wider">
                    {checkboxCols[0][1].label}
                  </th>
                )}
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {value.map((row, idx) => {
                const timing = checkboxCols.flatMap(([, cfg]) =>
                  (cfg.options ?? []).filter((opt) => !!row[opt.value]).map((opt) => opt.label)
                ).join(", ") || "—";

                return (
                  <tr
                    key={idx}
                    className="bg-white hover:bg-primary/5 transition-colors cursor-pointer"
                    onClick={() => setSelectedRow(row)}
                  >
                    <td className="px-3 py-2 font-medium text-primary underline underline-offset-2 decoration-primary/40">
                      {row[displayKey]}
                    </td>
                    {textCols.map(([key]) => (
                      <td key={key} className="px-3 py-2 text-text-light">
                        {row[key] || "—"}
                      </td>
                    ))}
                    {checkboxCols.length > 0 && (
                      <td className="px-3 py-2 text-text-light text-xs">{timing}</td>
                    )}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

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

  // TODO change this input to button and keep this input in the name field
  return (
    <div className="space-y-3">
      <input
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
        className="w-full p-2.5 border-2 border-border rounded-lg text-sm focus:border-primary outline-none"
      />

      {value.length > 0 && (
        <div className="border-2 border-border rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-background border-b border-border">
                {columns.map((col) => (
                  <th
                    key={col.key}
                    className="px-3 py-2 text-left text-xs font-semibold text-text-light uppercase tracking-wider"
                  >
                    {col.label}
                  </th>
                ))}
                <th className="w-10" />
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {value.map((row, rowIdx) => (
                <tr key={rowIdx} className="bg-white hover:bg-background/50 transition-colors">
                  {columns.map((col) => (
                    <td key={col.key} className="px-2 py-1.5">
                      {col.type === "checkbox-group" && col.options ? (
                        <div className="flex flex-col gap-1 px-1">
                          {col.options.map((opt) => (
                            <label
                              key={opt.value}
                              className="flex items-center gap-1.5 text-xs cursor-pointer select-none"
                            >
                              <input
                                type="checkbox"
                                checked={!!row[opt.value]}
                                onChange={(e) => updateCell(rowIdx, opt.value, e.target.checked)}
                                className="w-3.5 h-3.5 text-primary rounded border-border focus:ring-primary"
                              />
                              <span>{opt.label}</span>
                            </label>
                          ))}
                        </div>
                      ) : (
                        <input
                          type="text"
                          value={row[col.key] ?? ""}
                          onChange={(e) => updateCell(rowIdx, col.key, e.target.value)}
                          placeholder={col.placeholder}
                          className="w-full p-1.5 border border-transparent rounded focus:border-primary focus:outline-none text-sm bg-transparent hover:border-border transition-colors"
                        />
                      )}
                    </td>
                  ))}
                  <td className="px-2 py-1.5 text-center">
                    <button
                      type="button"
                      onClick={() => deleteRow(rowIdx)}
                      className="text-text-light/50 hover:text-danger transition-colors p-1 rounded"
                      aria-label="Delete row"
                    >
                      <X className="w-3.5 h-3.5" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
