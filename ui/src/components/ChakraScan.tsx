interface ChakraData {
  name: string;
  color: string;
  state: string;
  highlight?: string;
}

interface ChakraScanProps {
  value: ChakraData[];
  onChange: (chakras: ChakraData[]) => void;
}

export function ChakraScan({ value, onChange }: ChakraScanProps) {
  const updateChakra = (index: number, newState: string) => {
    const updated = [...value];
    updated[index] = { ...updated[index], state: newState };
    onChange(updated);
  };

  const addChakra = () => {
    // open modal or default new chakra
    onChange([
      ...value,
      { name: "New", color: "bg-gray-300", state: "Normal" },
    ]);
  };

  return (
    <div>
      <label className="block text-sm font-semibold text-text-light mb-2 uppercase">
        PRE-SCAN — Chakra Energy Levels
      </label>
      <div className="bg-background rounded-xl p-4 grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3">
        {value.map((chakra, idx) => (
          <div
            key={idx}
            className={`flex items-center gap-2 p-2 bg-white rounded-lg border ${
              chakra.highlight || "border-border"
            }`}
          >
            <div
              className={`w-3.5 h-3.5 rounded-full flex-shrink-0 ${chakra.color}`}
            />
            <span className="text-sm flex-1 truncate">{chakra.name}</span>
            <select
              className="text-xs p-1 border border-border rounded bg-transparent outline-none w-24"
              value={chakra.state}
              onChange={(e) => updateChakra(idx, e.target.value)}
            >
              <option>Normal</option>
              <option>Depleted</option>
              <option>Sl. Depleted</option>
              <option>Congested</option>
            </select>
          </div>
        ))}
        <button
          type="button"
          onClick={addChakra}
          className="text-sm text-primary font-medium p-2 hover:bg-primary/5 rounded-lg border border-transparent transition-colors text-left"
        >
          + Add More Chakras
        </button>
      </div>
    </div>
  );
}
