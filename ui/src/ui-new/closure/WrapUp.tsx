import type { useCaseClosure } from "@/hooks/useCaseClosure";
import { MultiSelect } from "@/components/ui/multi-select/multi-select";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { CharacterCount } from "@/components/form/CharacterCount";
import { useProtocols, type Protocol } from "@/hooks/admin/useMasterData";
import { TEXT_LIMITS } from "@/lib/textLimits";
import { HlSwitch } from "../components/Controls";

type Closure = ReturnType<typeof useCaseClosure>;

/** The close-case page's last step: recommended protocols, comments and whether healing continues. */
export default function WrapUp({ closure }: { closure: Closure }) {
  const { data: supplementaryProtocols = [], isLoading } = useProtocols("SUPPLEMENTARY");
  const { comments, setComments, isHealingContinued, setIsHealingContinued, additionalSessions, setAdditionalSessions, selectedProtocols, setSelectedProtocols } = closure;

  return (
    <div className="flex flex-col gap-[18px]">
      <div className="space-y-1.5" role="group" aria-labelledby="closure-protocols-label">
        <span id="closure-protocols-label" className="hl-step-q">Recommended protocols for the patient</span>
        <MultiSelect
          options={supplementaryProtocols}
          value={selectedProtocols}
          onChange={setSelectedProtocols}
          getOptionKey={(p: Protocol) => p.id}
          getOptionLabel={(p: Protocol) => p.name}
          placeholder="Add protocol"
          title="Recommended protocols"
          loading={isLoading}
        />
      </div>
      <div className="space-y-1.5">
        <label className="hl-step-q" htmlFor="closure-comments">Comments</label>
        <Textarea
          id="closure-comments"
          value={comments}
          onChange={(e) => setComments(e.target.value)}
          maxLength={TEXT_LIMITS.closureComments}
          className="min-h-[100px] resize-none"
        />
        <CharacterCount value={comments} max={TEXT_LIMITS.closureComments} />
      </div>
      <div className="space-y-1.5">
        <label className="hl-step-q" htmlFor="closure-additional-sessions">Additional sessions (optional)</label>
        <Input
          id="closure-additional-sessions"
          type="text"
          inputMode="numeric"
          pattern="[0-9]*"
          value={additionalSessions}
          onChange={(e) => setAdditionalSessions(e.target.value.replace(/\D/g, ""))}
          className="w-32"
          placeholder="0"
        />
      </div>
      <div className="hl-cont">
        <span className="hl-step-q">Healing will continue</span>
        <HlSwitch checked={isHealingContinued} onChange={setIsHealingContinued} label="Healing will continue" />
      </div>
    </div>
  );
}
