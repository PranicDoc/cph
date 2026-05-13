import type {
  ChakraOption,
  ColorOption,
  ProtocolEntryRequest,
} from "../../interfaces";
import { ProtocolColorRows } from "./ProtocolColorRows";

interface ProtocolEntryCardProps {
  entry: ProtocolEntryRequest;
  index: number;
  chakras: ChakraOption[];
  colors: ColorOption[];
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

export function ProtocolEntryCard({
  entry,
  index,
  chakras,
  colors,
  onUpdate,
  onRemove,
  onAddColor,
  onUpdateColor,
  onRemoveColor,
}: ProtocolEntryCardProps) {
  return (
    <div className="bg-background p-4 rounded-lg border border-border space-y-3">
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
        <select
          value={entry.chakraId || ""}
          onChange={(e) => onUpdate(index, "chakraId", e.target.value)}
          className="px-3 py-2 bg-white border border-border rounded text-sm focus:outline-none focus:ring-2 focus:ring-primary"
        >
          <option value="">Select chakra (optional)</option>
          {chakras.map((chakra) => (
            <option key={chakra.id} value={chakra.id}>
              {chakra.name}
            </option>
          ))}
        </select>

        <input
          type="text"
          placeholder="Target Area *"
          required
          value={entry.targetArea}
          onChange={(e) => onUpdate(index, "targetArea", e.target.value)}
          className="px-3 py-2 bg-white border border-border rounded text-sm focus:outline-none focus:ring-2 focus:ring-primary"
        />
      </div>

      <input
        type="text"
        placeholder="Technique *"
        required
        value={entry.technique}
        onChange={(e) => onUpdate(index, "technique", e.target.value)}
        className="w-full px-3 py-2 bg-white border border-border rounded text-sm focus:outline-none focus:ring-2 focus:ring-primary"
      />

      <textarea
        placeholder="Notes (optional)"
        rows={2}
        value={entry.notes || ""}
        onChange={(e) => onUpdate(index, "notes", e.target.value)}
        className="w-full px-3 py-2 bg-white border border-border rounded text-sm focus:outline-none focus:ring-2 focus:ring-primary"
      />

      <ProtocolColorRows
        entryIndex={index}
        value={entry.colors || []}
        colors={colors}
        onAddColor={onAddColor}
        onUpdateColor={onUpdateColor}
        onRemoveColor={onRemoveColor}
      />

      <button
        type="button"
        onClick={() => onRemove(index)}
        className="text-xs text-red-600 font-medium hover:underline"
      >
        Remove Entry
      </button>
    </div>
  );
}
