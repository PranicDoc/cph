import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

interface TreatmentPlanHeaderProps {
  protocolName: string;
  bookReference: string;
  onProtocolNameChange: (value: string) => void;
  onBookReferenceChange: (value: string) => void;
}

export function TreatmentPlanHeader({
  protocolName,
  bookReference,
  onProtocolNameChange,
  onBookReferenceChange,
}: TreatmentPlanHeaderProps) {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
      <div>
        <Label className="block text-sm font-semibold text-text-light mb-2">
          Protocol Name
        </Label>
        <Input
          type="text"
          value={protocolName}
          onChange={(e) => onProtocolNameChange(e.target.value)}
          placeholder="e.g., Headache Relief"
        />
      </div>

      <div>
        <Label className="block text-sm font-semibold text-text-light mb-2">
          Book Reference
        </Label>
        <Input
          type="text"
          value={bookReference}
          onChange={(e) => onBookReferenceChange(e.target.value)}
          placeholder="e.g., Advanced Pranic Healing p. 77"
        />
      </div>
    </div>
  );
}
