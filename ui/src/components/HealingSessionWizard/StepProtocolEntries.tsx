import type { useHealingSessionForm } from "../../hooks/useHealingSessionForm";
import type {
  ChakraOption,
  ColorOption,
  ProtocolEntryRequest,
} from "../../interfaces";
import { DynamicFormEngine } from "../DynamicFormEngine";
import { ProtocolEntryCard } from "./ProtocolEntryCard";
import { protocolDetailsSchema } from "./schemas/protocolEntriesSchema";

type HealingSessionForm = ReturnType<typeof useHealingSessionForm>["form"];

interface StepProtocolEntriesProps {
  form: HealingSessionForm;
  chakras: ChakraOption[];
  colors: ColorOption[];
  onAdd: () => void;
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

export function StepProtocolEntries({
  form,
  chakras,
  colors,
  onAdd,
  onUpdate,
  onRemove,
  onAddColor,
  onUpdateColor,
  onRemoveColor,
}: StepProtocolEntriesProps) {
  return (
    // <form.Subscribe selector={(state) => state.values.protocolEntries}>
    //   {(protocolEntries) => (
    //     <div className="space-y-6">
    //       <div className="flex justify-between items-center border-b pb-2">
    //         <h2 className="text-lg font-semibold">Protocol Entries</h2>
    //         <button
    //           type="button"
    //           onClick={onAdd}
    //           className="text-xs text-primary font-medium px-3 py-1.5 hover:bg-primary/10 rounded-lg transition-colors"
    //         >
    //           + Add Entry
    //         </button>
    //       </div>

    //       <div className="space-y-4">
    //         {protocolEntries.length === 0 && (
    //           <p className="text-sm text-text-light italic">
    //             No protocol entries added yet.
    //           </p>
    //         )}

    //         {protocolEntries.map(
    //           (entry: ProtocolEntryRequest, index: number) => (
    //             <ProtocolEntryCard
    //               key={index}
    //               entry={entry}
    //               index={index}
    //               chakras={chakras}
    //               colors={colors}
    //               onUpdate={onUpdate}
    //               onRemove={onRemove}
    //               onAddColor={onAddColor}
    //               onUpdateColor={onUpdateColor}
    //               onRemoveColor={onRemoveColor}
    //             />
    //           )
    //         )}
    //       </div>
    //     </div>
    //   )}
    // </form.Subscribe>
    <div>
      <form>
        <DynamicFormEngine form={form} schema={protocolDetailsSchema} />
      </form>
    </div>
  );
}
