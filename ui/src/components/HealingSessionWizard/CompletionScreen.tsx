// components/HealingSessionWizard/CompletionScreen.tsx
import { CheckCircle2 } from "lucide-react";

export function CompletionScreen() {
  return (
    <div className="flex flex-col items-center justify-center py-12 space-y-4">
      <div className="w-20 h-20 bg-success/10 rounded-full flex items-center justify-center mx-auto">
        <CheckCircle2 className="w-10 h-10 text-success" />
      </div>
      <h2 className="text-2xl font-bold text-primary-dark">
        Healing Session Submitted!
      </h2>
      <p className="text-text-light text-center">
        The session data has been saved successfully.
      </p>
    </div>
  );
}
