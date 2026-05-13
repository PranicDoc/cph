import { useMemo, useState } from "react";
import { X } from "lucide-react";
import type {
  ColorUsageType,
  PranaColorEntry,
  TreatmentColor,
} from "../types/spatial-chakra";

interface TreatmentColorPickerProps {
  value: TreatmentColor[];
  colors: PranaColorEntry[];
  onAddColor: (color: PranaColorEntry, usageType: ColorUsageType) => void;
  onRemoveColor: (colorId: string) => void;
}

const USAGE_TYPES: ColorUsageType[] = ["cleansing", "energising"];

export function TreatmentColorPicker({
  value,
  colors,
  onAddColor,
  onRemoveColor,
}: TreatmentColorPickerProps) {
  const [isPicking, setIsPicking] = useState(false);
  const [usageType, setUsageType] = useState<ColorUsageType>("cleansing");
  const selectedColorIds = useMemo(
    () => new Set(value.map((color) => color.id)),
    [value]
  );

  return (
    <div>
      <label className="block text-[10px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
        Treatment Colors
      </label>

      {value.length > 0 && (
        <div className="flex flex-wrap gap-1.5 mb-2">
          {value.map((color) => (
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
              <span className="text-gray-300">-</span>
              <span className="text-gray-400">{color.usageType}</span>
              <button
                type="button"
                onClick={() => onRemoveColor(color.id)}
                className="ml-0.5 text-gray-300 hover:text-red-400 transition-colors"
                aria-label={`Remove ${color.name}`}
              >
                <X className="h-3 w-3" />
              </button>
            </span>
          ))}
        </div>
      )}

      {!isPicking ? (
        <button
          type="button"
          onClick={() => setIsPicking(true)}
          className="text-xs text-violet-600 font-medium hover:text-violet-700 transition-colors"
        >
          + Add Color
        </button>
      ) : (
        <div className="border border-gray-200 rounded-lg p-2.5 bg-gray-50/50">
          <div className="flex gap-1 mb-2">
            {USAGE_TYPES.map((type) => (
              <button
                key={type}
                type="button"
                onClick={() => setUsageType(type)}
                className={`flex-1 text-[10px] font-medium py-1 rounded-md transition-all ${
                  usageType === type
                    ? "bg-white shadow-sm text-violet-600 border border-violet-200"
                    : "text-gray-400 hover:text-gray-600"
                }`}
              >
                {type.charAt(0).toUpperCase() + type.slice(1)}
              </button>
            ))}
          </div>

          <div className="grid grid-cols-5 gap-1.5">
            {colors.map((color) => {
              const alreadyAdded = selectedColorIds.has(color.id);

              return (
                <button
                  key={color.id}
                  type="button"
                  onClick={() => !alreadyAdded && onAddColor(color, usageType)}
                  disabled={alreadyAdded}
                  className={`flex flex-col items-center gap-0.5 p-1.5 rounded-lg transition-all ${
                    alreadyAdded
                      ? "opacity-30 cursor-not-allowed"
                      : "hover:bg-white hover:shadow-sm cursor-pointer"
                  }`}
                  title={`${color.name} - ${color.primaryUses}`}
                >
                  <span
                    className="w-6 h-6 rounded-full border border-white"
                    style={{
                      background: `linear-gradient(135deg, ${color.gradientFrom}, ${color.gradientTo})`,
                      boxShadow: `0 1px 3px ${color.colorHex}33`,
                    }}
                  />
                  <span className="text-[8px] text-gray-500 font-medium leading-tight text-center">
                    {color.code}
                  </span>
                </button>
              );
            })}
          </div>

          <button
            type="button"
            onClick={() => setIsPicking(false)}
            className="mt-2 text-[10px] text-gray-400 hover:text-gray-600 transition-colors"
          >
            Done
          </button>
        </div>
      )}
    </div>
  );
}
