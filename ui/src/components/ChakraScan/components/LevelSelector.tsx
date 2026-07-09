import type {
  ActivationLevel,
  EnergyLevel,
} from "../types/spatial-chakra";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";

type ChakraLevel = EnergyLevel | ActivationLevel;

interface LevelOption<T extends ChakraLevel> {
  value: T;
  label: string;
  shortLabel: string;
  colorHex: string;
}

interface LevelSelectorProps<T extends ChakraLevel> {
  label: string;
  value: T;
  options: LevelOption<T>[];
  onChange: (value: T) => void;
  fill?: boolean;
  showSelectedLabel?: boolean;
}

export function LevelSelector<T extends ChakraLevel>({
  label,
  value,
  options,
  onChange,
  fill = false,
  showSelectedLabel = false,
}: LevelSelectorProps<T>) {
  const selectedLabel =
    options.find((option) => option.value === value)?.label ?? "Normal";

  return (
    <div>
      <Label className="block text-[10px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
        {label}
      </Label>

      <div className={fill ? "flex gap-1" : "flex flex-wrap gap-1"}>
        {options.map((option) => {
          const isSelected = value === option.value;

          return (
            <Button
              key={option.value}
              type="button"
              variant="ghost"
              size="xs"
              onClick={() => onChange(option.value)}
              className={cn(
                "h-auto px-2 py-1 rounded-md text-[11px] font-medium",
                fill && "flex-1 text-center py-1.5 text-xs",
                isSelected
                  ? "ring-1 shadow-sm hover:bg-transparent"
                  : "bg-gray-50 hover:bg-gray-100 text-gray-500 hover:text-gray-500"
              )}
              style={
                isSelected
                  ? {
                      backgroundColor: `${option.colorHex}18`,
                      color: option.colorHex,
                      outlineColor: `${option.colorHex}40`,
                    }
                  : undefined
              }
            >
              {fill ? option.label : option.shortLabel}
            </Button>
          );
        })}
      </div>

      {showSelectedLabel && (
        <p className="text-[10px] text-gray-400 mt-1">{selectedLabel}</p>
      )}
    </div>
  );
}
