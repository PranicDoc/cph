// components/HealingSessionWizard/StepProtocolEntries.tsx
import { UseFormReturn } from "@tanstack/react-form";
import {
  ProtocolEntryRequest,
  ChakraOption,
  ColorOption,
} from "../../interfaces";

interface StepProtocolEntriesProps {
  form: UseFormReturn<any>;
  chakras: ChakraOption[];
  colors: ColorOption[];
  onAdd: () => void;
  onUpdate: (index: number, field: string, value: any) => void;
  onRemove: (index: number) => void;
  onAddColor: (entryIndex: number) => void;
  onUpdateColor: (
    entryIndex: number,
    colorIndex: number,
    field: string,
    value: any
  ) => void;
  onRemoveColor: (entryIndex: number, colorIndex: number) => void;
}

export function StepProtocolEntries({
  form,
  chakras,
  colors,
  onAdd,
  onUpdate,
  onRemove,
  onAddColor,
  onUpdateColor,
  onRemoveColor,
}: StepProtocolEntriesProps) {
  return (
    <form.Subscribe selector={(state) => state.values.protocolEntries}>
      {(protocolEntries) => (
        <div className="space-y-6">
          <div className="flex justify-between items-center border-b pb-2">
            <h2 className="text-lg font-semibold">Protocol Entries</h2>
            <button
              type="button"
              onClick={onAdd}
              className="text-xs text-primary font-medium px-3 py-1.5 hover:bg-primary/10 rounded-lg transition-colors"
            >
              + Add Entry
            </button>
          </div>
          <div className="space-y-4">
            {protocolEntries.length === 0 && (
              <p className="text-sm text-text-light italic">
                No protocol entries added yet.
              </p>
            )}
            {protocolEntries.map((entry: ProtocolEntryRequest, idx: number) => (
              <div
                key={idx}
                className="bg-background p-4 rounded-lg border border-border space-y-3"
              >
                {/* Chakra select */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  <select
                    value={entry.chakraId || ""}
                    onChange={(e) => onUpdate(idx, "chakraId", e.target.value)}
                    className="px-3 py-2 bg-white border border-border rounded text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                  >
                    <option value="">Select chakra (optional)</option>
                    {chakras.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                  </select>
                  <input
                    type="text"
                    placeholder="Target Area *"
                    required
                    value={entry.targetArea}
                    onChange={(e) =>
                      onUpdate(idx, "targetArea", e.target.value)
                    }
                    className="px-3 py-2 bg-white border border-border rounded text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                  />
                </div>

                {/* Technique */}
                <input
                  type="text"
                  placeholder="Technique *"
                  required
                  value={entry.technique}
                  onChange={(e) => onUpdate(idx, "technique", e.target.value)}
                  className="w-full px-3 py-2 bg-white border border-border rounded text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                />

                {/* Notes */}
                <textarea
                  placeholder="Notes (optional)"
                  rows={2}
                  value={entry.notes || ""}
                  onChange={(e) => onUpdate(idx, "notes", e.target.value)}
                  className="w-full px-3 py-2 bg-white border border-border rounded text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                />

                {/* Colors section */}
                <div className="border-t border-border pt-3">
                  <div className="flex justify-between items-center mb-2">
                    <span className="text-xs font-semibold text-text-light uppercase">
                      Colors
                    </span>
                    <button
                      type="button"
                      onClick={() => onAddColor(idx)}
                      className="text-xs text-primary font-medium hover:underline"
                    >
                      + Add Color
                    </button>
                  </div>
                  <div className="space-y-2">
                    {(entry.colors || []).map((color, colorIdx) => (
                      <div key={colorIdx} className="flex gap-2 items-center">
                        <select
                          value={color.colorId}
                          onChange={(e) =>
                            onUpdateColor(
                              idx,
                              colorIdx,
                              "colorId",
                              e.target.value
                            )
                          }
                          className="flex-1 px-2 py-1.5 bg-white border border-border rounded text-xs focus:outline-none focus:ring-2 focus:ring-primary"
                        >
                          <option value="">Select color...</option>
                          {colors.map((c) => (
                            <option key={c.id} value={c.id}>
                              {c.name}
                            </option>
                          ))}
                        </select>
                        <select
                          value={color.usageType}
                          onChange={(e) =>
                            onUpdateColor(
                              idx,
                              colorIdx,
                              "usageType",
                              e.target.value
                            )
                          }
                          className="px-2 py-1.5 bg-white border border-border rounded text-xs focus:outline-none focus:ring-2 focus:ring-primary"
                        >
                          <option value="CLEANSING">Cleansing</option>
                          <option value="ENERGISING">Energising</option>
                        </select>
                        <button
                          type="button"
                          onClick={() => onRemoveColor(idx, colorIdx)}
                          className="text-xs text-red-600 font-medium hover:underline"
                        >
                          ✕
                        </button>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Remove entry button */}
                <button
                  type="button"
                  onClick={() => onRemove(idx)}
                  className="text-xs text-red-600 font-medium hover:underline"
                >
                  Remove Entry
                </button>
              </div>
            ))}
          </div>
        </div>
      )}
    </form.Subscribe>
  );
}
