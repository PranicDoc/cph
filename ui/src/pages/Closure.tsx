import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import { extractDetail } from "../lib/utils";
import api from "../lib/axios";
import { Patient } from "../context/PatientContext";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";

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
    onSuccess: () => { toast.success("Case closed"); onSuccess(); },
    onError: (e) => toast.error(extractDetail(e) || "Failed to close case", { duration: 5000 }),
  });

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="w-full max-w-md space-y-4 gap-0">
        <div className="flex items-center justify-between">
          <DialogTitle className="text-lg font-semibold text-primary-dark">
            Close Case: {patient.firstName} {patient.lastName}
          </DialogTitle>
        </div>
        <div className="space-y-4 py-2">
          <div className="space-y-1.5">
            <Label className="text-sm font-medium text-gray-700">
              Additional Comments
            </Label>
            <Textarea
              value={comments}
              onChange={(e) => setComments(e.target.value)}
              className="border px-3 py-2 min-h-[100px] resize-none"
              placeholder="Add any final notes or observations here..."
            />
          </div>

          <label className="flex items-center gap-2 cursor-pointer w-fit">
            <Checkbox
              checked={isHealingContinued}
              onCheckedChange={(checked) =>
                setIsHealingContinued(checked === true)
              }
            />
            <span className="text-sm font-medium text-gray-700">
              Is healing continued?
            </span>
          </label>
        </div>
        <div className="flex justify-end gap-3 pt-2">
          <Button
            variant="outline"
            onClick={onClose}
            disabled={closeMutation.isPending}
            className="hover:bg-gray-50"
          >
            Cancel
          </Button>
          <Button
            onClick={() => closeMutation.mutate()}
            disabled={closeMutation.isPending}
            className="gap-2"
          >
            {closeMutation.isPending ? "Closing..." : "Close Case"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
