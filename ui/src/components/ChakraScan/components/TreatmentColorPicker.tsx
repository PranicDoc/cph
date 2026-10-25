import { useMemo, useState } from "react";
import { X } from "lucide-react";
import type {
  ColorUsageType,
  PranaColorEntry,
  TreatmentColor,
} from "../types/spatial-chakra";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";
import { hasColorCategory, type ColorCategory } from "@/lib/colorCategory";

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
  const colorsForUsage = useMemo(
    () =>
      colors.filter((color) =>
        hasColorCategory(color, usageType.toUpperCase() as ColorCategory)
      ),
    [colors, usageType]
  );

  return (
    <div>
      <Label className="block text-[10px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
        Treatment Colors
      </Label>

      {value.length > 0 && (
        <div className="flex flex-wrap gap-1.5 mb-2">
          {value.map((color) => (
            <Badge
              key={`${color.id}-${color.usageType}`}
              className="h-auto gap-1.5 pl-1 pr-1.5 py-0.5 bg-gray-50 border-gray-200 text-[10px] font-normal"
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
              <Button
                type="button"
                variant="ghost"
                size="icon-xs"
                onClick={() => onRemoveColor(color.id)}
                className="h-auto w-auto ml-0.5 p-0 text-gray-300 hover:text-red-400 hover:bg-transparent"
                aria-label={`Remove ${color.name}`}
              >
                <X className="h-3 w-3" />
              </Button>
            </Badge>
          ))}
        </div>
      )}

      {!isPicking ? (
        <Button
          type="button"
          variant="link"
          onClick={() => setIsPicking(true)}
          className="h-auto p-0 text-xs text-violet-600 font-medium hover:text-violet-700"
        >
          + Add Color
        </Button>
      ) : (
        <div className="border border-gray-200 rounded-lg p-2.5 bg-gray-50/50">
          <div className="flex gap-1 mb-2">
            {USAGE_TYPES.map((type) => (
              <Button
                key={type}
                type="button"
                variant="ghost"
                size="xs"
                onClick={() => setUsageType(type)}
                className={cn(
                  "flex-1 h-auto text-[10px] font-medium py-1 rounded-md",
                  usageType === type
                    ? "bg-white shadow-sm text-violet-600 border border-violet-200 hover:bg-white hover:text-violet-600"
                    : "text-gray-400 hover:text-gray-600 hover:bg-transparent"
                )}
              >
                {type.charAt(0).toUpperCase() + type.slice(1)}
              </Button>
            ))}
          </div>

          <div className="grid grid-cols-5 gap-1.5">
            {colorsForUsage.map((color) => {
              const alreadyAdded = selectedColorIds.has(color.id);

              return (
                <Button
                  key={color.id}
                  type="button"
                  variant="ghost"
                  onClick={() => !alreadyAdded && onAddColor(color, usageType)}
                  disabled={alreadyAdded}
                  className={cn(
                    "flex flex-col items-center gap-0.5 h-auto p-1.5 rounded-lg",
                    alreadyAdded
                      ? "opacity-30 cursor-not-allowed"
                      : "hover:bg-white hover:shadow-sm cursor-pointer"
                  )}
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
                </Button>
              );
            })}
          </div>

          <Button
            type="button"
            variant="link"
            onClick={() => setIsPicking(false)}
            className="mt-2 h-auto p-0 text-[10px] font-normal text-gray-400 hover:text-gray-600"
          >
            Done
          </Button>
        </div>
      )}
    </div>
  );
}
