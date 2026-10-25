import { X } from "lucide-react";
import type { ColorOption, ProtocolColorRequest } from "../../interfaces";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { hasColorCategory } from "@/lib/colorCategory";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

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
        <Button
          type="button"
          variant="link"
          onClick={() => onAddColor(entryIndex)}
          className="h-auto p-0 text-xs font-medium"
        >
          + Add Color
        </Button>
      </div>

      <div className="space-y-2">
        {value.map((color, colorIndex) => {
          const selectedColor = colors.find(
            (option) => option.id === color.colorId
          );
          // Offer colors of the row's usage type; keep an already-chosen color listed
          // so switching the usage type never blanks the selection.
          const colorOptions = colors.filter(
            (option) =>
              option.id === color.colorId ||
              hasColorCategory(option, color.usageType)
          );

          return (
            <div key={colorIndex} className="flex gap-2 items-center">
              <span
                className="h-5 w-5 rounded-full border border-border bg-white"
                style={{
                  backgroundColor: selectedColor?.colorHex ?? "#FFFFFF",
                }}
              />
              <Select
                value={color.colorId || ""}
                onValueChange={(v) =>
                  onUpdateColor(entryIndex, colorIndex, "colorId", v)
                }
              >
                <SelectTrigger
                  className={cn(
                    "flex-1 border px-2 py-1.5 rounded-md text-xs",
                    !color.colorId && "border-danger"
                  )}
                >
                  <SelectValue placeholder="Select color..." />
                </SelectTrigger>
                <SelectContent>
                  {colorOptions.map((option) => (
                    <SelectItem key={option.id} value={option.id}>
                      {option.name} {option.code ? `(${option.code})` : ""}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>

              <Select
                value={color.usageType}
                onValueChange={(v) =>
                  onUpdateColor(entryIndex, colorIndex, "usageType", v)
                }
              >
                <SelectTrigger className="w-fit border px-2 py-1.5 rounded-md text-xs">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="CLEANSING">Cleansing</SelectItem>
                  <SelectItem value="ENERGISING">Energising</SelectItem>
                </SelectContent>
              </Select>

              <Button
                type="button"
                variant="ghost"
                size="icon-xs"
                onClick={() => onRemoveColor(entryIndex, colorIndex)}
                className="h-auto w-auto p-0 text-red-600 hover:text-red-700 hover:bg-transparent"
                aria-label={`Remove color ${colorIndex + 1}`}
              >
                <X className="h-4 w-4" />
              </Button>
            </div>
          );
        })}
      </div>
    </div>
  );
}
