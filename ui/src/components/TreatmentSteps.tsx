import type { TreatmentStep } from "../interfaces";
import { TreatmentPlanHeader } from "./TreatmentSteps/TreatmentPlanHeader";

interface TreatmentPlan {
  protocolName: string;
  bookReference: string;
  treatmentSteps: TreatmentStep[];
}

interface TreatmentStepsProps {
  value: TreatmentPlan;
  onChange: (plan: TreatmentPlan) => void;
}

export function TreatmentSteps({ value, onChange }: TreatmentStepsProps) {
  const { protocolName, bookReference } = value;

  return (
    <div className="space-y-4">
      <TreatmentPlanHeader
        protocolName={protocolName}
        bookReference={bookReference}
        onProtocolNameChange={(name) =>
          onChange({ ...value, protocolName: name })
        }
        onBookReferenceChange={(ref) =>
          onChange({ ...value, bookReference: ref })
        }
      />

    </div>
  );
}
