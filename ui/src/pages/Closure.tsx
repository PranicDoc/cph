import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import { extractDetail } from "../lib/utils";
import api from "../lib/axios";
import { Patient } from "../context/PatientContext";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import ClosureForm from "@/components/closure/ClosureForm";

type ClosurePopupProps = {
  patient: Patient;
  onClose: () => void;
  onSuccess: () => void;
};

export function ClosurePopup({
  patient,
  onClose,
  onSuccess,
}: ClosurePopupProps) {
  const [comments, setComments] = useState("");
  const [isHealingContinued, setIsHealingContinued] = useState(false);
  const closeMutation = useMutation({
    mutationFn: () =>
      api.post(`/api/v1/cases/${patient.healingCaseId}/close`, {
        comments,
        isHealingContinued,
      }),
    onSuccess: () => {
      toast.success("Case closed");
      onSuccess();
    },
    onError: (e) =>
      toast.error(extractDetail(e) || "Failed to close case", {
        duration: 5000,
      }),
  });

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="w-full max-w-md space-y-4 gap-0">
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
          onClose={onClose}
          healingCaseId={patient.healingCaseId}
        />
      </DialogContent>
    </Dialog>
  );
}
