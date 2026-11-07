import type { useCaseClosure } from "@/pages/Closure";
import { MultiSelect } from "@/components/ui/multi-select/multi-select";
import { Textarea } from "@/components/ui/textarea";
import { CharacterCount } from "@/components/form/CharacterCount";
import { useProtocols, type Protocol } from "@/hooks/admin/useMasterData";
import { TEXT_LIMITS } from "@/lib/textLimits";
import { HlSwitch } from "../components/Controls";

type Closure = ReturnType<typeof useCaseClosure>;

/** The close-case page's last step: recommended protocols, comments and whether healing continues. */
export default function WrapUp({ closure }: { closure: Closure }) {
  const { data: supplementaryProtocols = [], isLoading } = useProtocols("SUPPLEMENTARY");
  const { comments, setComments, isHealingContinued, setIsHealingContinued, selectedProtocols, setSelectedProtocols } = closure;

  return (
    <div className="space-y-4">
      <div className="space-y-1.5">
        <span className="block text-sm font-semibold">Recommended protocols for the patient</span>
        <MultiSelect
          options={supplementaryProtocols}
          value={selectedProtocols}
          onChange={setSelectedProtocols}
          getOptionKey={(p: Protocol) => p.id}
          getOptionLabel={(p: Protocol) => p.name}
          placeholder="Add protocols to recommend..."
          loading={isLoading}
        />
      </div>
      <div className="space-y-1.5">
        <label className="lbl" htmlFor="closure-comments">Comments</label>
        <Textarea
          id="closure-comments"
          value={comments}
          onChange={(e) => setComments(e.target.value)}
          maxLength={TEXT_LIMITS.closureComments}
          className="min-h-[100px] resize-none"
          placeholder="What stood out in this case (optional)"
        />
        <CharacterCount value={comments} max={TEXT_LIMITS.closureComments} />
      </div>
      <div>
        <div className="flex items-center justify-between gap-3">
          <span className="text-sm font-semibold">Healing will continue</span>
          <HlSwitch checked={isHealingContinued} onChange={setIsHealingContinued} label="Healing will continue" />
        </div>
        <p className="help">On: the case stays open as "continued". Off: the patient gets the final feedback request.</p>
      </div>
    </div>
  );
}
