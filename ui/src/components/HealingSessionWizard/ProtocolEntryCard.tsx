import type {
  ChakraOption,
  ColorOption,
  ProtocolEntryRequest,
} from "../../interfaces";
import { ProtocolColorRows } from "./ProtocolColorRows";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

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
        <Select
          value={entry.chakraId || ""}
          onValueChange={(v) => onUpdate(index, "chakraId", v)}
        >
          <SelectTrigger className="w-full border px-3 py-2 rounded-md">
            <SelectValue placeholder="Select chakra (optional)" />
          </SelectTrigger>
          <SelectContent>
            {chakras.map((chakra) => (
              <SelectItem key={chakra.id} value={chakra.id}>
                {chakra.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Input
          type="text"
          placeholder="Target Area *"
          required
          value={entry.targetArea}
          onChange={(e) => onUpdate(index, "targetArea", e.target.value)}
          className="border px-3 py-2 rounded-md"
        />
      </div>

      <Input
        type="text"
        placeholder="Technique *"
        required
        value={entry.technique}
        onChange={(e) => onUpdate(index, "technique", e.target.value)}
        className="border px-3 py-2 rounded-md"
      />

      <Textarea
        placeholder="Notes (optional)"
        rows={2}
        value={entry.notes || ""}
        onChange={(e) => onUpdate(index, "notes", e.target.value)}
        className="border px-3 py-2 rounded-md min-h-0"
      />

      <ProtocolColorRows
        entryIndex={index}
        value={entry.colors || []}
        colors={colors}
        onAddColor={onAddColor}
        onUpdateColor={onUpdateColor}
        onRemoveColor={onRemoveColor}
      />

      <Button
        type="button"
        variant="link"
        onClick={() => onRemove(index)}
        className="h-auto p-0 text-xs font-medium text-red-600 hover:text-red-600"
      >
        Remove Entry
      </Button>
    </div>
  );
}
