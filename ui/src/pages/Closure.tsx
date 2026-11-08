import { Patient } from "../context/PatientContext";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import ClosureForm from "@/components/closure/ClosureForm";
import { useCaseClosure } from "@/hooks/useCaseClosure";

type ClosurePopupProps = {
  patient: Patient;
  onClose: () => void;
  onSuccess: () => void;
  skipUnderstanding?: boolean;
};

export function ClosurePopup({
  patient,
  onClose,
  onSuccess,
  skipUnderstanding,
}: ClosurePopupProps) {
  const {
    comments,
    setComments,
    isHealingContinued,
    setIsHealingContinued,
    additionalSessions,
    setAdditionalSessions,
    selectedProtocols,
    setSelectedProtocols,
    closeMutation,
  } = useCaseClosure(patient.healingCaseId, onSuccess);

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="w-full max-w-[calc(100%-2rem)] md:max-w-4xl space-y-4 gap-0">
        <div className="flex items-center justify-between">
          <DialogTitle className="text-lg font-semibold text-primary-dark">
            Close Case: {patient.firstName} {patient.lastName}
          </DialogTitle>
        </div>
        <ClosureForm
          comments={comments}
          closeMutation={closeMutation}
          setComments={setComments}
          setIsHealingContinued={setIsHealingContinued}
          isHealingContinued={isHealingContinued}
          additionalSessions={additionalSessions}
          setAdditionalSessions={setAdditionalSessions}
          selectedProtocols={selectedProtocols}
          setSelectedProtocols={setSelectedProtocols}
          onClose={onClose}
          healingCaseId={patient.healingCaseId}
          skipUnderstanding={skipUnderstanding}
        />
      </DialogContent>
    </Dialog>
  );
}
