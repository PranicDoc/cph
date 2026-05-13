import { X } from "lucide-react";
import { Autocomplete } from "../../AutoComplete";
import { getProtocolSearchLabel } from "../utils";
import type {
  ProtocolCatalogEntry,
  ProtocolSelection,
} from "../types/spatial-chakra";

interface ProtocolPickerProps {
  value?: ProtocolSelection;
  protocols: ProtocolCatalogEntry[];
  onChange: (protocol: ProtocolSelection | undefined) => void;
}

function toProtocolSelection(protocol: ProtocolCatalogEntry): ProtocolSelection {
  return {
    id: protocol.id,
    name: protocol.name,
    category: protocol.category,
    level: protocol.level,
  };
}

export function ProtocolPicker({
  value,
  protocols,
  onChange,
}: ProtocolPickerProps) {
  return (
    <div>
      <label className="block text-[10px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
        Protocol
      </label>

      {value ? (
        <div className="flex items-center gap-2 px-2.5 py-1.5 bg-violet-50 rounded-lg border border-violet-100">
          <span className="text-xs text-violet-700 font-medium flex-1 truncate">
            {value.name}
          </span>
          <span className="text-[9px] text-violet-400 uppercase">
            {value.level}
          </span>
          <button
            type="button"
            onClick={() => onChange(undefined)}
            className="text-violet-300 hover:text-violet-500 transition-colors"
            aria-label="Clear protocol"
          >
            <X className="h-3 w-3" />
          </button>
        </div>
      ) : (
        <Autocomplete
          options={protocols}
          getOptionLabel={getProtocolSearchLabel}
          onSelect={(protocol) => onChange(toProtocolSelection(protocol))}
          placeholder="Search protocols..."
          className="[&_input]:text-xs [&_input]:rounded-lg [&_input]:border-gray-200 [&_input]:focus:ring-violet-200 [&_input]:focus:border-violet-300"
        />
      )}
    </div>
  );
}
