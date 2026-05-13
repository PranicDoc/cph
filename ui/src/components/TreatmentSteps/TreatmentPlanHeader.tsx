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
        <label className="block text-sm font-semibold text-text-light mb-2">
          Protocol Name
        </label>
        <input
          type="text"
          value={protocolName}
          onChange={(e) => onProtocolNameChange(e.target.value)}
          placeholder="e.g., Headache Relief"
          className="w-full p-2.5 border-2 border-border rounded-lg text-sm focus:border-primary outline-none"
        />
      </div>

      <div>
        <label className="block text-sm font-semibold text-text-light mb-2">
          Book Reference
        </label>
        <input
          type="text"
          value={bookReference}
          onChange={(e) => onBookReferenceChange(e.target.value)}
          placeholder="e.g., Advanced Pranic Healing p. 77"
          className="w-full p-2.5 border-2 border-border rounded-lg text-sm focus:border-primary outline-none"
        />
      </div>
    </div>
  );
}
