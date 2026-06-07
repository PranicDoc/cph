import type { useHealingSessionForm } from "../../hooks/useHealingSessionForm";
import { DynamicFormEngine } from "../DynamicFormEngine";
import { protocolDetailsSchema } from "./schemas/protocolEntriesSchema";

type HealingSessionForm = ReturnType<typeof useHealingSessionForm>["form"];

interface StepProtocolEntriesProps {
  form: HealingSessionForm;
}

export function StepProtocolEntries({ form }: StepProtocolEntriesProps) {
  return (
    <div>
      <DynamicFormEngine form={form} schema={protocolDetailsSchema} />
    </div>
  );
}
