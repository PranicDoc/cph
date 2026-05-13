import type {
  ActivationLevel,
  EnergyLevel,
} from "../types/spatial-chakra";

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
      <label className="block text-[10px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
        {label}
      </label>

      <div className={fill ? "flex gap-1" : "flex flex-wrap gap-1"}>
        {options.map((option) => {
          const isSelected = value === option.value;

          return (
            <button
              key={option.value}
              type="button"
              onClick={() => onChange(option.value)}
              className={`px-2 py-1 rounded-md text-[11px] font-medium transition-all ${
                fill ? "flex-1 text-center py-1.5 text-xs" : ""
              } ${
                isSelected
                  ? "ring-1 shadow-sm"
                  : "bg-gray-50 hover:bg-gray-100 text-gray-500"
              }`}
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
            </button>
          );
        })}
      </div>

      {showSelectedLabel && (
        <p className="text-[10px] text-gray-400 mt-1">{selectedLabel}</p>
      )}
    </div>
  );
}
