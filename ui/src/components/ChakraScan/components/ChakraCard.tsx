import { memo, useCallback } from "react";
import { CheckCircle2, X } from "lucide-react";
import {
  ACTIVATION_OPTIONS,
  ENERGY_OPTIONS,
  getActivationOption,
} from "../data/energy-options";
import { isDocumented } from "../utils";
import { LevelSelector } from "./LevelSelector";
import { ProtocolPicker } from "./ProtocolPicker";
import { TreatmentColorPicker } from "./TreatmentColorPicker";
import type {
  ActivationLevel,
  ChakraCatalogEntry,
  ChakraDocEntry,
  ColorUsageType,
  EnergyLevel,
  PranaColorEntry,
  ProtocolCatalogEntry,
} from "../types/spatial-chakra";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

interface ChakraCardProps {
  chakra: ChakraCatalogEntry;
  entry: ChakraDocEntry;
  expanded: boolean;
  onToggle: () => void;
  onUpdate: (entry: ChakraDocEntry) => void;
  protocolCatalog: ProtocolCatalogEntry[];
  pranaColorCatalog: PranaColorEntry[];
}

interface CollapsedChakraCardProps {
  name: string;
  colorHex: string;
  entry: ChakraDocEntry;
  documented: boolean;
  onToggle: () => void;
  onUpdateEnergy: (level: EnergyLevel) => void;
}

function CollapsedChakraCard({
  name,
  colorHex,
  entry,
  documented,
  onToggle,
  onUpdateEnergy,
}: CollapsedChakraCardProps) {
  const activationOption = getActivationOption(entry.activationLevel);

  return (
    <div
      onClick={onToggle}
      className={`flex flex-col gap-1.5 p-2.5 bg-white rounded-lg border cursor-pointer transition-all hover:shadow-sm ${
        documented ? "border-emerald-200" : "border-gray-200"
      }`}
    >
      <div className="flex items-center gap-2 min-w-0">
        <div
          className="w-3.5 h-3.5 rounded-full flex-shrink-0"
          style={{ backgroundColor: colorHex }}
        />
        <span className="text-sm font-medium text-gray-700 truncate flex-1">
          {name}
        </span>
        {documented && (
          <CheckCircle2 className="h-3.5 w-3.5 flex-shrink-0 text-emerald-500" />
        )}
      </div>

      <Select
        value={entry.energyLevel}
        onValueChange={(v) => onUpdateEnergy(v as EnergyLevel)}
      >
        <SelectTrigger
          className="w-full text-xs p-1.5 border border-gray-200 rounded-md bg-transparent text-gray-600"
          onClick={(e) => e.stopPropagation()}
        >
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {ENERGY_OPTIONS.map((option) => (
            <SelectItem key={option.value} value={option.value}>
              {option.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      {documented && (
        <div className="flex items-center gap-1 flex-wrap">
          {entry.activationLevel !== "N" && (
            <span
              className="text-[9px] font-semibold px-1.5 py-0.5 rounded"
              style={{
                backgroundColor: `${activationOption.colorHex}15`,
                color: activationOption.colorHex,
              }}
            >
              {activationOption.label}
            </span>
          )}
          {entry.treatmentColors.length > 0 && (
            <span className="flex -space-x-1">
              {entry.treatmentColors.slice(0, 4).map((color) => (
                <span
                  key={`${color.id}-${color.usageType}`}
                  className="w-3 h-3 rounded-full border-2 border-white"
                  style={{ backgroundColor: color.colorHex }}
                />
              ))}
            </span>
          )}
          {entry.protocol && (
            <span className="text-[9px] text-violet-500 font-medium truncate max-w-[80px]">
              {entry.protocol.name}
            </span>
          )}
        </div>
      )}
    </div>
  );
}

export const ChakraCard = memo(function ChakraCard({
  chakra,
  entry,
  expanded,
  onToggle,
  onUpdate,
  protocolCatalog,
  pranaColorCatalog,
}: ChakraCardProps) {
  const colorHex = chakra.colorHex;
  const name = chakra.name;
  const documented = isDocumented(entry);

  const updateEnergy = useCallback(
    (level: EnergyLevel) => onUpdate({ ...entry, energyLevel: level }),
    [entry, onUpdate]
  );

  const updateActivation = useCallback(
    (level: ActivationLevel) =>
      onUpdate({ ...entry, activationLevel: level }),
    [entry, onUpdate]
  );

  const addColor = useCallback(
    (color: PranaColorEntry, usageType: ColorUsageType) => {
      onUpdate({
        ...entry,
        treatmentColors: [
          ...entry.treatmentColors,
          {
            id: color.id,
            name: color.name,
            colorHex: color.colorHex,
            usageType,
          },
        ],
      });
    },
    [entry, onUpdate]
  );

  const removeColor = useCallback(
    (colorId: string) =>
      onUpdate({
        ...entry,
        treatmentColors: entry.treatmentColors.filter(
          (color) => color.id !== colorId
        ),
      }),
    [entry, onUpdate]
  );

  if (!expanded) {
    return (
      <CollapsedChakraCard
        name={name}
        colorHex={colorHex}
        entry={entry}
        documented={documented}
        onToggle={onToggle}
        onUpdateEnergy={updateEnergy}
      />
    );
  }

  return (
    <div className="col-span-full bg-white rounded-xl border border-violet-200 shadow-md overflow-hidden">
      <div className="flex items-center gap-3 px-4 py-3 border-b border-gray-100 bg-gray-50/50">
        <span
          className="w-3.5 h-3.5 rounded-full flex-shrink-0"
          style={{
            backgroundColor: colorHex,
            boxShadow: `0 0 8px ${colorHex}44`,
          }}
        />
        <h3 className="text-sm font-semibold text-gray-800 flex-1 truncate">
          {name}
        </h3>
        {chakra.location && (
          <span className="text-[10px] text-gray-400">
            {chakra.location}
          </span>
        )}
        <Button
          type="button"
          variant="ghost"
          size="icon-xs"
          onClick={onToggle}
          className="w-6 h-6 rounded-md hover:bg-gray-200/60 text-gray-400 hover:text-gray-400"
          aria-label="Collapse chakra details"
        >
          <X className="h-3 w-3" />
        </Button>
      </div>

      <div className="px-4 py-3 space-y-4">
        <LevelSelector
          label="Energy Level"
          value={entry.energyLevel}
          options={ENERGY_OPTIONS}
          onChange={updateEnergy}
          showSelectedLabel
        />

        <LevelSelector
          label="Activation Level"
          value={entry.activationLevel}
          options={ACTIVATION_OPTIONS}
          onChange={updateActivation}
          fill
        />

        <ProtocolPicker
          value={entry.protocol}
          protocols={protocolCatalog}
          onChange={(protocol) => onUpdate({ ...entry, protocol })}
        />

        <TreatmentColorPicker
          value={entry.treatmentColors}
          colors={pranaColorCatalog}
          onAddColor={addColor}
          onRemoveColor={removeColor}
        />
      </div>
    </div>
  );
});
