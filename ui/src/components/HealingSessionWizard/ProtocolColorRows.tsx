import { X } from "lucide-react";
import type { ColorOption, ProtocolColorRequest } from "../../interfaces";

interface ProtocolColorRowsProps {
  entryIndex: number;
  value: ProtocolColorRequest[];
  colors: ColorOption[];
  onAddColor: (entryIndex: number) => void;
  onUpdateColor: (
    entryIndex: number,
    colorIndex: number,
    field: string,
    value: any
  ) => void;
  onRemoveColor: (entryIndex: number, colorIndex: number) => void;
}

export function ProtocolColorRows({
  entryIndex,
  value,
  colors,
  onAddColor,
  onUpdateColor,
  onRemoveColor,
}: ProtocolColorRowsProps) {
  return (
    <div className="border-t border-border pt-3">
      <div className="flex justify-between items-center mb-2">
        <span className="text-xs font-semibold text-text-light uppercase">
          Colors
        </span>
        <button
          type="button"
          onClick={() => onAddColor(entryIndex)}
          className="text-xs text-primary font-medium hover:underline"
        >
          + Add Color
        </button>
      </div>

      <div className="space-y-2">
        {value.map((color, colorIndex) => {
          const selectedColor = colors.find(
            (option) => option.id === color.colorId
          );

          return (
            <div key={colorIndex} className="flex gap-2 items-center">
              <span
                className="h-5 w-5 rounded-full border border-border bg-white"
                style={{
                  backgroundColor: selectedColor?.colorHex ?? "#FFFFFF",
                }}
              />
              <select
                value={color.colorId}
                onChange={(e) =>
                  onUpdateColor(
                    entryIndex,
                    colorIndex,
                    "colorId",
                    e.target.value
                  )
                }
                className={`flex-1 px-2 py-1.5 bg-white border rounded text-xs focus:outline-none focus:ring-2 focus:ring-primary ${
                  color.colorId ? "border-border" : "border-danger"
                }`}
              >
                <option value="">Select color...</option>
                {colors.map((option) => (
                  <option key={option.id} value={option.id}>
                    {option.name} {option.code ? `(${option.code})` : ""}
                  </option>
                ))}
              </select>

              <select
                value={color.usageType}
                onChange={(e) =>
                  onUpdateColor(
                    entryIndex,
                    colorIndex,
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
                onClick={() => onRemoveColor(entryIndex, colorIndex)}
                className="text-red-600 hover:text-red-700"
                aria-label={`Remove color ${colorIndex + 1}`}
              >
                <X className="h-4 w-4" />
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
}
